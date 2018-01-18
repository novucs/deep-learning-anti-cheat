package net.novucs.dlac;

import com.comphenix.packetwrapper.WrapperPlayClientLook;
import com.comphenix.packetwrapper.WrapperPlayClientPosition;
import com.comphenix.packetwrapper.WrapperPlayClientPositionLook;
import com.comphenix.packetwrapper.WrapperPlayClientUseEntity;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.google.common.collect.ImmutableMap;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class AntiCheatPlugin extends JavaPlugin {

    private final ConnectionManager connectionManager = new ConnectionManager(this);
    private final Map<Player, CombatMode> teachers = new WeakHashMap<>();
    private final Map<Player, CombatantProfile> combatants = new WeakHashMap<>();
    private long combatExpiryMillis = TimeUnit.SECONDS.toMillis(3);
    private int minHistoryLength = 50;
    private int maxHistoryLength = 500;

    @Override
    public void onEnable() {
        // Load settings.
        combatExpiryMillis = getConfig().getLong("combat-expiry-millis", combatExpiryMillis);
        minHistoryLength = getConfig().getInt("min-history-length", minHistoryLength);
        maxHistoryLength = getConfig().getInt("max-history-length", maxHistoryLength);

        long combatantTickRate = getConfig().getLong("combatant-tick-rate", 20 * 15);
        String classificationServerHost = getConfig().getString("classification-server.host", "localhost");
        int classificationServerPort = getConfig().getInt("classification-server.port", 14454);

        // Setup and run the connection manager.
        connectionManager.getHost().set(classificationServerHost);
        connectionManager.getPort().set(classificationServerPort);
        connectionManager.run();

        // Register all combat packet listeners.
        ImmutableMap<PacketType, Consumer<PacketEvent>> listeners = ImmutableMap.of(
                PacketType.Play.Client.USE_ENTITY, this::onUseEntity,
                PacketType.Play.Client.POSITION, this::onPosition,
                PacketType.Play.Client.LOOK, this::onLook,
                PacketType.Play.Client.POSITION_LOOK, this::onPositionLook);

        listeners.forEach((type, listener) -> ProtocolLibrary.getProtocolManager()
                .addPacketListener(new PacketAdapter(this, ListenerPriority.LOWEST, type) {
                    @Override
                    public void onPacketReceiving(PacketEvent event) {
                        listener.accept(event);
                    }
                }));

        // Schedule combatant processing task.
        getServer().getScheduler().runTaskTimer(this, this::tickCombatants, combatantTickRate, combatantTickRate);
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down connection manager...");
        connectionManager.interrupt();
        try {
            connectionManager.join();
        } catch (InterruptedException ignore) {
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /dlac mode <mode>");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players may execute this command");
            return true;
        }

        CombatMode mode = CombatMode.getByName(args[1]);

        if (mode == null) {
            sender.sendMessage("No mode of that name was found!");
            sender.sendMessage("Valid combat modes are: UNKNOWN, VANILLA, HACKING and EXEMPT");
            return true;
        }

        Player player = (Player) sender;
        teachers.put(player, mode);
        CombatantProfile profile = combatants.get(player);

        if (profile != null) {
            profile.setActiveCombatSnippet(new CombatSnippet(player.getUniqueId(), mode, new LinkedList<>()));
        }

        player.sendMessage("Successfully updated combat mode to: " + mode.name());
        return true;
    }

    private void tickCombatants() {
        Set<CombatSnippet> dataset = new HashSet<>();
        Set<Player> activeTeachers = new HashSet<>();

        combatants.forEach((player, profile) -> {
            CombatSnippet snippet = profile.getActiveCombatSnippet();

            // Ignore all combat snippets with short histories.
            if (snippet.getPacketHistory().size() > minHistoryLength) {

                // Trim large combat snippet histories.
                int size;
                while ((size = snippet.getPacketHistory().size()) > maxHistoryLength) {
                    snippet.getPacketHistory().remove(size - 1);
                }

                if (snippet.getCombatMode() == CombatMode.UNKNOWN) {
                    // Player is not teaching, perform a check.
                    // TODO: Add player to ban wave when we are confident they are hacking.
                    connectionManager.send(Packet.check(snippet), System.out::println);
                } else if (snippet.getCombatMode() != CombatMode.EXEMPT) {
                    // Add valid combat snippet to the dataset.
                    dataset.add(profile.getActiveCombatSnippet());
                    activeTeachers.add(player);
                }
            }

            // Reset the players combat snippet.
            CombatMode combatMode = teachers.getOrDefault(player, CombatMode.UNKNOWN);
            profile.setActiveCombatSnippet(new CombatSnippet(player.getUniqueId(), combatMode, new LinkedList<>()));
        });

        // Send the current dataset.
        connectionManager.send(Packet.dataset(dataset), response -> {
            for (Player teacher : activeTeachers) {
                teacher.sendMessage(ChatColor.GREEN + "[DLAC] " + ChatColor.WHITE + response);
            }
        });
    }

    private void onUseEntity(PacketEvent event) {
        WrapperPlayClientUseEntity packet = new WrapperPlayClientUseEntity(event.getPacket());
        if (packet.getType() != EnumWrappers.EntityUseAction.ATTACK)
            return;

        Player player = event.getPlayer();
        CombatantProfile profile = combatants.get(player);
        Location origin = player.getLocation();

        if (profile == null) {
            // Do nothing if player is exempt from combat monitoring.
            CombatMode combatMode = teachers.getOrDefault(player, CombatMode.UNKNOWN);
            if (combatMode == CombatMode.EXEMPT) {
                return;
            }

            // Create and update the combat profile for this player.
            long currentTime = System.currentTimeMillis();
            long expiry = currentTime + combatExpiryMillis;

            CombatSnippet snippet = new CombatSnippet(player.getUniqueId(), combatMode, new LinkedList<>());

            profile = new CombatantProfile(origin, currentTime, expiry, snippet);
            combatants.put(player, profile);
            return;
        }

        Location target = packet.getTarget(event).getLocation();

        long time = profile.getLastPacket() - System.currentTimeMillis();
        double x = origin.getX() - target.getX();
        double y = origin.getY() - target.getY();
        double z = origin.getZ() - target.getZ();
        double yaw = origin.getYaw();
        double pitch = origin.getPitch();

        PlayerPacket playerPacket = PlayerPacket.attack(time, x, y, z, yaw, pitch);
        profile.getActiveCombatSnippet().getPacketHistory().add(playerPacket);
        profile.setLastPacket(System.currentTimeMillis());
    }

    private void onPosition(PacketEvent event) {
        CombatantProfile profile = combatants.get(event.getPlayer());
        if (profile == null)
            return;

        WrapperPlayClientPosition packet = new WrapperPlayClientPosition(event.getPacket());
        Location origin = profile.getLastLocation();

        long time = profile.getLastPacket() - System.currentTimeMillis();
        double x = origin.getX() - packet.getX();
        double y = origin.getY() - packet.getY();
        double z = origin.getZ() - packet.getZ();

        PlayerPacket playerPacket = PlayerPacket.position(time, x, y, z);
        profile.getActiveCombatSnippet().getPacketHistory().add(playerPacket);
        profile.setLastLocation(event.getPlayer().getLocation());
        profile.setLastPacket(System.currentTimeMillis());
    }

    private void onLook(PacketEvent event) {
        CombatantProfile profile = combatants.get(event.getPlayer());
        if (profile == null)
            return;

        WrapperPlayClientLook packet = new WrapperPlayClientLook(event.getPacket());
        Location origin = profile.getLastLocation();

        long time = profile.getLastPacket() - System.currentTimeMillis();
        double yaw = origin.getYaw() - packet.getYaw();
        double pitch = origin.getPitch() - packet.getPitch();

        PlayerPacket playerPacket = PlayerPacket.look(time, yaw, pitch);
        profile.getActiveCombatSnippet().getPacketHistory().add(playerPacket);
        profile.setLastLocation(event.getPlayer().getLocation());
        profile.setLastPacket(System.currentTimeMillis());
    }

    private void onPositionLook(PacketEvent event) {
        CombatantProfile profile = combatants.get(event.getPlayer());
        if (profile == null)
            return;

        WrapperPlayClientPositionLook packet = new WrapperPlayClientPositionLook(event.getPacket());
        Location origin = profile.getLastLocation();

        long time = profile.getLastPacket() - System.currentTimeMillis();
        double x = origin.getX() - packet.getX();
        double y = origin.getY() - packet.getY();
        double z = origin.getZ() - packet.getZ();
        double yaw = origin.getYaw() - packet.getYaw();
        double pitch = origin.getPitch() - packet.getPitch();

        PlayerPacket playerPacket = PlayerPacket.positionLook(time, x, y, z, yaw, pitch);
        profile.getActiveCombatSnippet().getPacketHistory().add(playerPacket);
        profile.setLastLocation(event.getPlayer().getLocation());
        profile.setLastPacket(System.currentTimeMillis());
    }
}
