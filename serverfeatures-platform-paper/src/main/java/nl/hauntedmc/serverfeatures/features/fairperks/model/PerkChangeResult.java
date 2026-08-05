package nl.hauntedmc.serverfeatures.features.fairperks.model;

import java.util.Objects;

public record PerkChangeResult(Status status, boolean enabled) {

    public PerkChangeResult {
        Objects.requireNonNull(status, "status");
    }

    public static PerkChangeResult changed(boolean enabled) {
        return new PerkChangeResult(Status.CHANGED, enabled);
    }

    public static PerkChangeResult already(boolean enabled) {
        return new PerkChangeResult(Status.ALREADY_IN_STATE, enabled);
    }

    public static PerkChangeResult denied(Status status) {
        if (!status.denied()) {
            throw new IllegalArgumentException("Status is not a denial: " + status);
        }
        return new PerkChangeResult(status, false);
    }

    public boolean success() {
        return status == Status.CHANGED || status == Status.ALREADY_IN_STATE;
    }

    public enum Status {
        CHANGED(false),
        ALREADY_IN_STATE(false),
        NO_PERMISSION(true),
        COMBAT_TAGGED(true),
        HOSTILE_NEARBY(true),
        WORLD_BLOCKED(true),
        GAME_MODE_BLOCKED(true);

        private final boolean denied;

        Status(boolean denied) {
            this.denied = denied;
        }

        public boolean denied() {
            return denied;
        }
    }
}
