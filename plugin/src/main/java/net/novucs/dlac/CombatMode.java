package net.novucs.dlac;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor
@Getter
public enum CombatMode {
    VANILLA(0),
    HACKING(1),
    UNKNOWN(2),
    EXEMPT(3);

    private static final Map<String, CombatMode> BY_NAME = new HashMap<>();
    private final int id;

    public boolean isLogged() {
        return this == VANILLA || this == HACKING;
    }

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
