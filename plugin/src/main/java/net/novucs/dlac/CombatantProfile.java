package net.novucs.dlac;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.Location;

import java.util.List;

@Data
@AllArgsConstructor
public class CombatantProfile {
    private Location lastLocation;
    private long lastPacket;
    private long expiry;
    private CombatSnippet activeCombatSnippet;
    private List<CombatSnippet> combatSnippetHistory;

    public boolean isActiveSnippetExpired() {
        return getExpiry() < System.currentTimeMillis();
    }
}
