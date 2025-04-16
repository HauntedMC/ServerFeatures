package nl.hauntedmc.serverfeatures.features.bossbar.internal;

import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

import java.util.Collections;
import java.util.Set;

public class BossbarMessage {
    private final String messageKey;
    private final long durationTicks;
    private final BarColor color;
    private final BarStyle style;
    private final double initialProgress;
    private final boolean autoFade;
    private final Set<BarFlag> flags;

    private BossbarMessage(Builder builder) {
        this.messageKey = builder.messageKey;
        this.durationTicks = builder.durationTicks;
        this.color = builder.color;
        this.style = builder.style;
        this.initialProgress = builder.initialProgress;
        this.autoFade = builder.autoFade;
        this.flags = builder.flags;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public long getDurationTicks() {
        return durationTicks;
    }

    public BarColor getColor() {
        return color;
    }

    public BarStyle getStyle() {
        return style;
    }

    public double getInitialProgress() {
        return initialProgress;
    }

    public boolean isAutoFade() {
        return autoFade;
    }

    public Set<BarFlag> getFlags() {
        return flags;
    }

    public static class Builder {
        private String messageKey;
        private long durationTicks = 100L;
        private BarColor color = BarColor.WHITE;
        private BarStyle style = BarStyle.SOLID;
        private double initialProgress = 1.0;
        private boolean autoFade = false;
        private Set<BarFlag> flags = Collections.emptySet();

        public Builder messageKey(String messageKey) {
            this.messageKey = messageKey;
            return this;
        }

        public Builder durationTicks(long durationTicks) {
            this.durationTicks = durationTicks;
            return this;
        }

        public Builder color(BarColor color) {
            this.color = color;
            return this;
        }

        public Builder style(BarStyle style) {
            this.style = style;
            return this;
        }

        public Builder initialProgress(double progress) {
            this.initialProgress = progress;
            return this;
        }

        public Builder autoFade(boolean autoFade) {
            this.autoFade = autoFade;
            return this;
        }

        public Builder flags(Set<BarFlag> flags) {
            this.flags = flags;
            return this;
        }

        public BossbarMessage build() {
            return new BossbarMessage(this);
        }
    }
}
