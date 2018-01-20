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
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class AntiCheatPlugin extends JavaPlugin implements Listener {

    private static final boolean TEACH_MODE = true;
    private final ConnectionManager connectionManager = new ConnectionManager(this);
    private final Map<Player, CombatMode> teachers = new WeakHashMap<>();
    private final Map<Player, CombatantProfile> combatants = new WeakHashMap<>();
    private long combatExpiryMillis = TimeUnit.SECONDS.toMillis(2);
    private int minHistoryLength = 50;
    private int maxHistoryLength = 500;

    @Override
    public void onEnable() {
        // Load settings.
        saveDefaultConfig();
        combatExpiryMillis = getConfig().getLong("combat-expiry-millis", combatExpiryMillis);
        minHistoryLength = getConfig().getInt("min-history-length", minHistoryLength);
        maxHistoryLength = getConfig().getInt("max-history-length", maxHistoryLength);

        long combatantTickRate = getConfig().getLong("combatant-tick-rate", 20 * 15);
        String classificationServerHost = getConfig().getString("classification-server.host", "localhost");
        int classificationServerPort = getConfig().getInt("classification-server.port", 14454);

        // Setup and start the connection manager.
        connectionManager.getHost().set(classificationServerHost);
        connectionManager.getPort().set(classificationServerPort);
        connectionManager.start();

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

        if (TEACH_MODE) {
            getServer().getPluginManager().registerEvents(this, this);
        }
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

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.setGameMode(GameMode.CREATIVE);
        sendTeachModeMessage(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        teachers.remove(event.getPlayer());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        player.setHealth(20);
        player.setFoodLevel(20);
        if (!teachers.containsKey(player)) {
            sendTeachModeMessage(player);
            player.teleport(event.getFrom());
        }
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SURVIVAL) {
            captureSnippet(player, combatants.get(player));
        }
    }

    private void sendTeachModeMessage(Player player) {
        for (int i = 0; i < 100; i++) {
            player.sendMessage("");
        }
        player.sendMessage(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "|||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||");
        player.sendMessage("");
        player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "TEACH MODE IS CURRENTLY ACTIVE");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "You /must/ make sure you tell the server whether you're hacking or not. " +
                "To do this, execute: " + ChatColor.RED + "/dlac mode <hacking|vanilla>");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "WARNING: When training vanilla data, ONLY train using the vanilla client. " +
                "Simply turning off hacked client features may still send signatures that we want to detect.");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "It's incredibly important you do not cross-contaminate training examples. " +
                "Once registered, simply start attacking things while in survival mode.");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Thanks for helping me train this thing!");
        player.sendMessage("");
        player.sendMessage(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "|||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||");
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

        CombatMode mode = CombatMode.match(args[1]);

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
            captureSnippet(player, profile);
            List<CombatSnippet> snippets = profile.getCombatSnippetHistory();

            for (CombatSnippet snippet : snippets) {
                // Ignore all combat snippets with short histories.
                if (snippet.getPacketHistory().size() <= minHistoryLength) {
                    continue;
                }

                // Trim large combat snippet histories.
                int size;
                while ((size = snippet.getPacketHistory().size()) > maxHistoryLength) {
                    snippet.getPacketHistory().remove(size - 1);
                }

                if (snippet.getCombatMode() == CombatMode.UNKNOWN) {
                    // Player is not teaching, perform a check.
                    // TODO: Add player to ban wave when we are confident they are hacking.
                    connectionManager.send(Packet.check(snippet), response ->
                            getServer().broadcastMessage(ChatColor.GREEN + "[DLAC] " + ChatColor.WHITE + response));
                } else if (snippet.getCombatMode() != CombatMode.EXEMPT) {
                    // Add valid combat snippet to the dataset.
                    dataset.add(snippet);
                    activeTeachers.add(player);
                }
            }

            // Reset the players combat snippet history.
            snippets.clear();
        });

        // Send the current dataset.
        connectionManager.send(Packet.dataset(dataset), response -> {
            for (Player teacher : activeTeachers) {
                teacher.sendMessage(ChatColor.GREEN + "[DLAC] " + ChatColor.WHITE + response);
            }
        });
    }

    private void captureSnippet(Player player, CombatantProfile profile) {
        if (profile == null || profile.getActiveCombatSnippet().getPacketHistory().isEmpty()) {
            return;
        }

        CombatSnippet oldSnippet = profile.getActiveCombatSnippet();
        CombatMode combatMode = oldSnippet.getCombatMode();
        if (combatMode == CombatMode.EXEMPT) {
            return;
        }

        CombatSnippet newSnippet = new CombatSnippet(player.getUniqueId(), combatMode, new LinkedList<>());
        profile.setActiveCombatSnippet(newSnippet);
        profile.setExpiry(combatExpiryMillis);

        int historyLength = oldSnippet.getPacketHistory().size();

        if (historyLength < minHistoryLength) {
            if (combatMode != CombatMode.UNKNOWN) {
                player.sendMessage("Ignored current combat snippet as it was too small");
            }
            return;
        }

        profile.getCombatSnippetHistory().add(oldSnippet);

        if (combatMode != CombatMode.UNKNOWN) {
            int totalQueued = profile.getCombatSnippetHistory().size();
            player.sendMessage("Successfully captured combat snippet of history length: " + historyLength);
            player.sendMessage("Total queued snippets: " + totalQueued);
        }
    }

    private void onUseEntity(PacketEvent event) {
        WrapperPlayClientUseEntity packet = new WrapperPlayClientUseEntity(event.getPacket());
        if (packet.getType() != EnumWrappers.EntityUseAction.ATTACK) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }

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

            profile = new CombatantProfile(origin, currentTime, expiry, snippet, new LinkedList<>());
            combatants.put(player, profile);

            if (combatMode != CombatMode.UNKNOWN) {
                player.sendMessage("Starting a new combat snippet");
            }
            return;
        }

        if (profile.isActiveSnippetExpired()) {
            captureSnippet(player, profile);
            profile.setExpiry(System.currentTimeMillis() + combatExpiryMillis);
            if (profile.getActiveCombatSnippet().getCombatMode() != CombatMode.UNKNOWN) {
                player.sendMessage("Starting a new combat snippet");
            }
            return;
        }

        Location target = packet.getTarget(event).getLocation();

        long time = System.currentTimeMillis() - profile.getLastPacket();
        double x = origin.getX() - target.getX();
        double y = origin.getY() - target.getY();
        double z = origin.getZ() - target.getZ();
        double yaw = origin.getYaw();
        double pitch = origin.getPitch();

        PlayerPacket playerPacket = PlayerPacket.attack(time, x, y, z, yaw, pitch);
        profile.getActiveCombatSnippet().getPacketHistory().add(playerPacket);
        profile.setLastPacket(System.currentTimeMillis());
        profile.setExpiry(System.currentTimeMillis() + combatExpiryMillis);
    }

    private void onPosition(PacketEvent event) {
        Player player = event.getPlayer();
        CombatantProfile profile = combatants.get(player);
        if (profile == null || player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }

        if (profile.isActiveSnippetExpired()) {
            captureSnippet(player, profile);
            return;
        }

        WrapperPlayClientPosition packet = new WrapperPlayClientPosition(event.getPacket());
        Location origin = profile.getLastLocation();

        long time = System.currentTimeMillis() - profile.getLastPacket();
        double x = origin.getX() - packet.getX();
        double y = origin.getY() - packet.getY();
        double z = origin.getZ() - packet.getZ();

        PlayerPacket playerPacket = PlayerPacket.position(time, x, y, z);
        profile.getActiveCombatSnippet().getPacketHistory().add(playerPacket);
        profile.setLastLocation(player.getLocation());
        profile.setLastPacket(System.currentTimeMillis());
    }

    private void onLook(PacketEvent event) {
        Player player = event.getPlayer();
        CombatantProfile profile = combatants.get(player);
        if (profile == null || player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }

        if (profile.isActiveSnippetExpired()) {
            captureSnippet(player, profile);
            return;
        }

        WrapperPlayClientLook packet = new WrapperPlayClientLook(event.getPacket());
        Location origin = profile.getLastLocation();

        long time = System.currentTimeMillis() - profile.getLastPacket();
        double yaw = origin.getYaw() - packet.getYaw();
        double pitch = origin.getPitch() - packet.getPitch();

        PlayerPacket playerPacket = PlayerPacket.look(time, yaw, pitch);
        profile.getActiveCombatSnippet().getPacketHistory().add(playerPacket);
        profile.setLastLocation(player.getLocation());
        profile.setLastPacket(System.currentTimeMillis());
    }

    private void onPositionLook(PacketEvent event) {
        Player player = event.getPlayer();
        CombatantProfile profile = combatants.get(player);
        if (profile == null || player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }

        if (profile.isActiveSnippetExpired()) {
            captureSnippet(player, profile);
            return;
        }

        WrapperPlayClientPositionLook packet = new WrapperPlayClientPositionLook(event.getPacket());
        Location origin = profile.getLastLocation();

        long time = System.currentTimeMillis() - profile.getLastPacket();
        double x = origin.getX() - packet.getX();
        double y = origin.getY() - packet.getY();
        double z = origin.getZ() - packet.getZ();
        double yaw = origin.getYaw() - packet.getYaw();
        double pitch = origin.getPitch() - packet.getPitch();

        PlayerPacket playerPacket = PlayerPacket.positionLook(time, x, y, z, yaw, pitch);
        profile.getActiveCombatSnippet().getPacketHistory().add(playerPacket);
        profile.setLastLocation(player.getLocation());
        profile.setLastPacket(System.currentTimeMillis());
    }
}
