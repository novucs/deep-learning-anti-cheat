package net.novucs.dlac;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CombatSnippet {
    private final UUID playerId;
    private final CombatMode combatMode;
    private final List<PlayerPacket> packetHistory;
}
