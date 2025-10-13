package nl.hauntedmc.serverfeatures.features.nametags.internal.properties;

/**
 * TextDisplayAlignment represents the alignment options stored in bits 3-4 (mask 0x18)
 * of the Text Display field (index 27). The protocol interprets:
 * 0 = CENTER, 1 or 3 = LEFT, and 2 = RIGHT.
 */
public enum TextDisplayAlignment {
    CENTER(0),
    LEFT(1),
    RIGHT(2);

    private final int value;

    TextDisplayAlignment(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static TextDisplayAlignment fromValue(int value) {
        if (value == 1 || value == 3) {
            return LEFT;
        } else if (value == 2) {
            return RIGHT;
        }
        return CENTER;
    }
}
