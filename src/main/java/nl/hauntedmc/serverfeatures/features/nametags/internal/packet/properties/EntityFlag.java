package nl.hauntedmc.serverfeatures.features.nametags.internal.packet.properties;

public enum EntityFlag {
    ON_FIRE((byte) 0x01),
    PRESSING_SNEAK((byte) 0x02),
    // 0x04 is unused (previously riding)
    SPRINTING((byte) 0x08),
    SWIMMING((byte) 0x10),
    INVISIBLE((byte) 0x20),
    GLOWING((byte) 0x40),
    FLYING((byte) 0x80);

    private final byte bit;

    EntityFlag(byte bit) {
        this.bit = bit;
    }

    public byte getBit() {
        return bit;
    }
}
