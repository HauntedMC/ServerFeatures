package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum TankType {
    EMPTY, LAVA, WATER, MILK, MUSHROOM_STEW, RABBIT_STEW, BEETROOT_SOUP, HONEY, DRAGON_BREATH, EXPERIENCE;

    private static final Map<String, TankType> LOOKUP;

    static {
        Map<String, TankType> m = new HashMap<>();
        for (TankType type : values()) {
            m.put(normalize(type.name()), type);
        }
        LOOKUP = Collections.unmodifiableMap(m);
    }

    public static TankType getTankType(String paramString) {
        TankType found = LOOKUP.get(normalize(paramString));
        return (found != null) ? found : EMPTY;
    }

    private static String normalize(String s) {
        return s.replace("_", "").toLowerCase(Locale.ROOT);
    }
}
