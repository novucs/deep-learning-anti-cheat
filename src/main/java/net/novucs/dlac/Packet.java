package net.novucs.dlac;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import java.util.Set;

public interface Packet {

    static DatasetPacket dataset(Set<CombatSnippet> combatSnippets) {
        return new DatasetPacket(combatSnippets);
    }

    static CheckPacket check(CombatSnippet combatSnippet) {
        return new CheckPacket(combatSnippet);
    }

    Type getType();

    @AllArgsConstructor
    @Getter
    enum Type {
        DISCONNECT(0),
        DATASET(1),
        CHECK(2);
        private final int id;
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
