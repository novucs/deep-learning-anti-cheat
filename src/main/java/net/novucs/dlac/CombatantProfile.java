package net.novucs.dlac;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.Location;

@Data
@AllArgsConstructor
public class CombatantProfile {
    private Location lastLocation;
    private long lastPacket;
    private long expiry;
    private CombatSnippet activeCombatSnippet;
}
