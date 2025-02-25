package nl.hauntedmc.serverfeatures.features.actionbar.internal;

public class ActionbarMessage {
    private final String text;
    private final long duration;
    private ActionbarMessage(Builder builder) {
        this.text = builder.text;
        this.duration = builder.duration;
    }

    public String getText() {
        return text;
    }

    public long getDuration() {
        return duration;
    }

    public static class Builder {
        private String text;
        private long duration = 100L;

        public Builder text(String text) {
            this.text = text;
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
