package nl.hauntedmc.serverfeatures.features.graveyard.text;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.graveyard.GraveStatus;
import nl.hauntedmc.serverfeatures.features.graveyard.Graveyard;
import nl.hauntedmc.serverfeatures.features.graveyard.model.Grave;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Objects;

/**
 * Builds every player-facing runtime text used by Graveyard through the localization handler.
 */
public final class GraveyardText {
    private final Graveyard feature;

    public GraveyardText(Graveyard feature) {
        this.feature = Objects.requireNonNull(feature, "feature");
    }

    public Component hologramTitle(Grave grave, Player viewer) {
        return feature.getLocalizationHandler()
                .getMessage("graveyard.hologram.title")
                .with("player", grave.ownerName())
                .forAudience(viewer)
                .build();
    }

    public Component timer(Grave grave, Player viewer, long remainingMillis) {
        return switch (grave.status()) {
            case DELIVERY_PENDING -> feature.getLocalizationHandler()
                    .getMessage("graveyard.timer.delivery_pending")
                    .forAudience(viewer)
                    .build();
            case ORPHANED_WORLD -> feature.getLocalizationHandler()
                    .getMessage("graveyard.timer.remote_recovery")
                    .forAudience(viewer)
                    .build();
            default -> feature.getLocalizationHandler()
                    .getMessage("graveyard.timer.remaining")
                    .with("remaining", duration(remainingMillis, viewer))
                    .forAudience(viewer)
                    .build();
        };
    }

    public Component tracking(
            Grave grave,
            Player viewer,
            Integer distance,
            long remainingMillis
    ) {
        var builder = feature.getLocalizationHandler()
                .getMessage(distance == null
                        ? "graveyard.tracking.other_world"
                        : "graveyard.tracking.same_world")
                .with("grave_id", grave.shortId())
                .with("timer", timer(grave, viewer, remainingMillis))
                .forAudience(viewer);
        if (distance == null) {
            builder.with("world", grave.location().worldKey());
        } else {
            builder.with("distance", distance);
        }
        return builder.build();
    }

    public Component duration(long millis, Audience audience) {
        DurationParts parts = durationParts(millis);
        if (parts.hours() > 0L) {
            return feature.getLocalizationHandler()
                    .getMessage("graveyard.duration.hours_minutes")
                    .with("hours", parts.hours())
                    .with("minutes", parts.minutes())
                    .forAudience(audience)
                    .build();
        }
        return feature.getLocalizationHandler()
                .getMessage("graveyard.duration.minutes_seconds")
                .with("minutes", parts.minutes())
                .with("seconds", parts.seconds())
                .forAudience(audience)
                .build();
    }

    public Component status(GraveStatus status, Audience audience) {
        return feature.getLocalizationHandler()
                .getMessage("graveyard.status." + status.name().toLowerCase(Locale.ROOT))
                .forAudience(audience)
                .build();
    }

    public String timerFingerprint(Grave grave, long remainingMillis) {
        return switch (grave.status()) {
            case DELIVERY_PENDING, ORPHANED_WORLD -> grave.status().name();
            default -> "REMAINING:" + durationParts(remainingMillis).totalSeconds();
        };
    }

    public static DurationParts durationParts(long millis) {
        long totalSeconds = Math.max(0L, (millis + 999L) / 1_000L);
        long hours = totalSeconds / 3_600L;
        long minutes = totalSeconds % 3_600L / 60L;
        long seconds = totalSeconds % 60L;
        return new DurationParts(totalSeconds, hours, minutes, seconds);
    }

    public record DurationParts(long totalSeconds, long hours, long minutes, long seconds) {
    }
}
