package nl.hauntedmc.serverfeatures.api.effect.sound;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Immutable sound descriptor: sound id + category + volume + pitch,
 * with convenience play() helpers and a few curated presets.
 */
public final class SoundProfile {
    private final Sound sound;
    private final SoundCategory category;
    private final float volume;           // >= 0
    private final float pitch;            // clamped to ~[0.5, 2.0] when played

    private SoundProfile(Sound sound, SoundCategory category, float volume, float pitch) {
        this.sound = Objects.requireNonNull(sound, "sound");
        this.category = category;
        this.volume = Math.max(0f, volume);
        this.pitch = pitch;
    }

    public static SoundProfile of(Sound sound, float volume, float pitch) {
        return new SoundProfile(sound, null, volume, pitch);
    }

    public static SoundProfile of(Sound sound, SoundCategory category, float volume, float pitch) {
        return new SoundProfile(sound, category, volume, pitch);
    }

    public Sound sound() {
        return sound;
    }

    public SoundCategory category() {
        return category;
    }

    public float volume() {
        return volume;
    }

    public float pitch() {
        return pitch;
    }

    /* ---------- “with” clones ---------- */

    public SoundProfile withSound(Sound s) {
        return new SoundProfile(s, category, volume, pitch);
    }

    public SoundProfile withCategory(SoundCategory c) {
        return new SoundProfile(sound, c, volume, pitch);
    }

    public SoundProfile withVolume(float v) {
        return new SoundProfile(sound, category, Math.max(0f, v), pitch);
    }

    public SoundProfile withPitch(float p) {
        return new SoundProfile(sound, category, volume, p);
    }

    /* ---------- Playback helpers ---------- */

    /**
     * Play for a player at their location.
     */
    public void play(Player player) {
        if (player == null) return;
        float clampedPitch = clampPitch(pitch);
        if (category != null) {
            player.playSound(player.getLocation(), sound, category, volume, clampedPitch);
        } else {
            player.playSound(player.getLocation(), sound, volume, clampedPitch);
        }
    }

    /**
     * Play for a player at a specific location.
     */
    public void play(Player player, Location at) {
        if (player == null || at == null) return;
        float clampedPitch = clampPitch(pitch);
        if (category != null) {
            player.playSound(at, sound, category, volume, clampedPitch);
        } else {
            player.playSound(at, sound, volume, clampedPitch);
        }
    }

    /**
     * Play for the whole world at a location (audible to nearby players).
     */
    public void play(World world, Location at) {
        if (world == null || at == null) return;
        float clampedPitch = clampPitch(pitch);
        if (category != null) {
            world.playSound(at, sound, category, volume, clampedPitch);
        } else {
            world.playSound(at, sound, volume, clampedPitch);
        }
    }

    private static float clampPitch(float p) {
        return Math.max(0.5f, Math.min(2.0f, p));
    }

    /* ---------- Curated presets (optional) ---------- */

    public static final SoundProfile PING_PLING =
            SoundProfile.of(org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING,
                    org.bukkit.SoundCategory.PLAYERS, 0.8f, 1.4f);

    public static final SoundProfile PING_XP =
            SoundProfile.of(org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                    org.bukkit.SoundCategory.PLAYERS, 0.7f, 1.2f);

    public static final SoundProfile PING_AMETHYST =
            SoundProfile.of(org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                    org.bukkit.SoundCategory.PLAYERS, 0.7f, 1.0f);

    public static final SoundProfile UI_CLICK =
            SoundProfile.of(org.bukkit.Sound.UI_BUTTON_CLICK,
                    org.bukkit.SoundCategory.MASTER, 0.6f, 1.0f);
}
