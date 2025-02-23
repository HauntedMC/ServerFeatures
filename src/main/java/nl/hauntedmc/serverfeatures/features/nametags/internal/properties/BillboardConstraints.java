package nl.hauntedmc.serverfeatures.features.nametags.internal.properties;

public enum BillboardConstraints {
    FIXED((byte) 0),
    VERTICAL((byte) 1),
    HORIZONTAL((byte) 2),
    CENTER((byte) 3);

    private final byte value;

    BillboardConstraints(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }

    public static BillboardConstraints fromValue(byte value) {
        for (BillboardConstraints bc : values()) {
            if (bc.value == value) {
                return bc;
            }
        }
        return FIXED;
    }
}
