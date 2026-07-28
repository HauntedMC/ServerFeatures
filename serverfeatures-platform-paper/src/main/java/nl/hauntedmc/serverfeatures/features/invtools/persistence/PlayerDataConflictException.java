package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import java.io.IOException;

public final class PlayerDataConflictException extends IOException {

    private static final long serialVersionUID = 1L;

    public PlayerDataConflictException(String message) {
        super(message);
    }
}
