package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import java.util.Objects;

public record PlayerDataRevision(String sha256) {

    public PlayerDataRevision {
        Objects.requireNonNull(sha256, "sha256");
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "sha256 must contain exactly 64 lowercase hexadecimal characters"
            );
        }
    }
}
