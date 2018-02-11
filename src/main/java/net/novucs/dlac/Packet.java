package net.novucs.dlac;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Set;
import java.util.function.Consumer;

public interface Packet {

    static Disconnect disconnect() {
        return new Disconnect();
    }

    static Dataset dataset(Set<CombatSnippet> combatSnippets) {
        return new Dataset(combatSnippets);
    }

    static Check check(CombatSnippet combatSnippet) {
        return new Check(combatSnippet);
    }

    void handle(DlacOutputStream out, DataInputStream in, Consumer<String> callback) throws IOException;

    @AllArgsConstructor
    @Getter
    enum Type {
        DISCONNECT(0),
        DATASET(1),
        CHECK(2);
        private final int id;
    }

    @Data
    class Disconnect implements Packet {
        private final Type type = Type.DISCONNECT;

        @Override
        public void handle(DlacOutputStream out, DataInputStream in, Consumer<String> callback) throws IOException {
            out.writeInt(type.getId());
        }
    }

    @Data
    class Dataset implements Packet {
        private final Type type = Type.DATASET;
        private final Set<CombatSnippet> combatSnippets;

        @Override
        public void handle(DlacOutputStream out, DataInputStream in, Consumer<String> callback) throws IOException {
            out.writeInt(type.getId());
            out.writeInt(combatSnippets.size());
            for (CombatSnippet snippet : combatSnippets) {
                if (snippet.getCombatMode().isLogged()) {
                    out.writeCombatSnippet(snippet);
                }
            }
            out.flush();

            int total = in.readInt();
            int updated = in.readInt();
            int vanilla = in.readInt();
            int hacking = in.readInt();
            String response = "Vanilla: " + vanilla + ", Hacking: " + hacking + ", Updated: " + updated + ", Total: " + total;
            callback.accept(response);

            if (vanilla / hacking < 0.75 && hacking > 100) {
                callback.accept("More 'Vanilla' data needs to be provided to ensure an effective AntiCheat");
            } else if (hacking / vanilla < 0.75 && vanilla > 100) {
                callback.accept("More 'Hacking' data needs to be provided to ensure an effective AntiCheat");
            }
        }
    }

    @Data
    class Check implements Packet {
        private final Type type = Type.CHECK;
        private final CombatSnippet combatSnippet;

        @Override
        public void handle(DlacOutputStream out, DataInputStream in, Consumer<String> callback) throws IOException {
            out.writeInt(type.getId());
            out.flush();
            String response = "Totally just ignored your packet <:";
            callback.accept(response);
        }
    }
}
