package net.novucs.dlac;

import java.util.HashMap;
import java.util.Map;

public enum CombatMode {
    UNKNOWN, VANILLA, HACKING, EXEMPT;

    private static final Map<String, CombatMode> BY_NAME = new HashMap<>();

    public static CombatMode getByName(String name) {
        return BY_NAME.get(name);
    }

    static {
        for (CombatMode combatMode : values()) {
            BY_NAME.put(combatMode.name().toUpperCase(), combatMode);
        }
    }
}
