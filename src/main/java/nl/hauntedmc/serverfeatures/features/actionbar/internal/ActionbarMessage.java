package nl.hauntedmc.serverfeatures.features.actionbar.internal;

public class ActionbarMessage {
    private final String messageKey;
    private final long duration;

    private ActionbarMessage(Builder builder) {
        this.messageKey = builder.messageKey;
        this.duration = builder.duration;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public long getDuration() {
        return duration;
    }

    public static class Builder {
        private String messageKey;
        private long duration = 100L;

        public Builder messageKey(String messageKey) {
            this.messageKey = messageKey;
            return this;
        }

        public Builder duration(long duration) {
            this.duration = duration;
            return this;
        }

        public ActionbarMessage build() {
            return new ActionbarMessage(this);
        }
    }
}
