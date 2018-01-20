package net.novucs.dlac;

import java.util.HashMap;
import java.util.Map;

public enum CombatMode {
    UNKNOWN, VANILLA, HACKING, EXEMPT;

    private static final Map<String, CombatMode> BY_NAME = new HashMap<>();

    public static CombatMode match(String name) {
        name = name.toUpperCase();
        CombatMode mode = BY_NAME.get(name);
        if (mode != null) {
            return mode;
        }

        for (CombatMode mode1 : values()) {
            if (mode1.name().toUpperCase().startsWith(name)) {
                return mode1;
            }
        }

        return null;
    }

    static {
        for (CombatMode combatMode : values()) {
            BY_NAME.put(combatMode.name().toUpperCase(), combatMode);
        }
    }
}
