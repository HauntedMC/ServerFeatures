package nl.hauntedmc.serverfeatures.features.nametags.internal.packet.properties;

/**
 * TextDisplayFlag represents the individual bit flags for the Text Display field (index 27).
 */
public enum TextDisplayFlag {
    HAS_SHADOW((byte) 0x01),
    IS_SEE_THROUGH((byte) 0x02),
    USE_DEFAULT_BG((byte) 0x04);

    private final byte bit;

    TextDisplayFlag(byte bit) {
        this.bit = bit;
    }

    public byte getBit() {
        return bit;
    }
}
