package nl.hauntedmc.serverfeatures.features.nametags.internal.update;

public class UpdateProperties {
    private final boolean forced;
    private final boolean ownerOnly;
    private final long delay;
    private final boolean updateText;

    private UpdateProperties(Builder builder) {
        this.forced = builder.forced;
        this.ownerOnly = builder.ownerOnly;
        this.delay = builder.delay;
        this.updateText = builder.updateText;
    }

    public boolean isForced() {
        return forced;
    }

    public boolean isOwnerOnly() {
        return ownerOnly;
    }

    public long getDelay() {
        return delay;
    }

    public boolean getUpdateText() {
        return updateText;
    }

    public static class Builder {
        private boolean forced = false;
        private boolean ownerOnly = false;
        private long delay = 0L;
        private boolean updateText;

        /**
         * Set whether the update is forced.
         * @param forced true if the update should be forced.
         * @return this Builder instance.
         */
        public Builder forced(boolean forced) {
            this.forced = forced;
            return this;
        }

        /**
         * Set whether only the nametag owner should be updated.
         * @param ownerOnly true if only the owner should be updated.
         * @return this Builder instance.
         */
        public Builder ownerOnly(boolean ownerOnly) {
            this.ownerOnly = ownerOnly;
            return this;
        }

        /**
         * Set the delay (in ticks) before sending update packets.
         * @param delay the delay in ticks.
         * @return this Builder instance.
         */
        public Builder delay(long delay) {
            this.delay = delay;
            return this;
        }

        /**
         * Set whether the nametag text should be updated.
         * @param updateText true if only the text should be updated.
         * @return this Builder instance.
         */
        public Builder updateText(boolean updateText) {
            this.updateText = updateText;
            return this;
        }


        /**
         * Build and return the UpdateProperty instance.
         * @return a new UpdateProperty with the configured values.
         */
        public UpdateProperties build() {
            return new UpdateProperties(this);
        }
    }
}
