package net.novucs.dlac;

import lombok.Data;

import java.util.Set;

public interface Packet {

    static DatasetPacket dataset(Set<CombatSnippet> combatSnippets) {
        return new DatasetPacket(combatSnippets);
    }

    static CheckPacket check(CombatSnippet combatSnippet) {
        return new CheckPacket(combatSnippet);
    }

    enum Type {
        DATASET, CHECK
    }

    @Data
    class DatasetPacket implements Packet {
        private final Type type = Type.DATASET;
        private final Set<CombatSnippet> combatSnippets;
    }

    @Data
    class CheckPacket implements Packet {
        private final Type type = Type.CHECK;
        private final CombatSnippet combatSnippet;
    }
}
