package nl.hauntedmc.serverfeatures.features.graveyard.persistence;

import java.util.Arrays;

public record EncodedGravePayload(byte[] bytes, String checksum) {
    public EncodedGravePayload {
        bytes = Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
