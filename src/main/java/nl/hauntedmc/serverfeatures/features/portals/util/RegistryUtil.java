package nl.hauntedmc.serverfeatures.features.portals.util;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public final class RegistryUtil {

    private RegistryUtil() {}

    // ---- Registries (Paper 1.21+)
    public static Registry<@NotNull Sound> soundRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.SOUND_EVENT);
    }
    public static Registry<@NotNull Particle> particleRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.PARTICLE_TYPE);
    }

    // ---- Keys
    /** Accepts "minecraft:key" or plain "key" (assumes minecraft namespace). Lowercases safely. */
    public static NamespacedKey toKey(String s) {
        if (s == null || s.isBlank()) return null;
        String norm = s.contains(":") ? s.toLowerCase(Locale.ROOT) : ("minecraft:" + s.toLowerCase(Locale.ROOT));
        return NamespacedKey.fromString(norm);
    }

    // ---- Resolve
    public static Optional<Sound> resolveSound(String keyLike) {
        NamespacedKey k = toKey(keyLike);
        return k == null ? Optional.empty() : Optional.ofNullable(soundRegistry().get(k));
    }
    public static Optional<Particle> resolveParticle(String keyLike) {
        NamespacedKey k = toKey(keyLike);
        return k == null ? Optional.empty() : Optional.ofNullable(particleRegistry().get(k));
    }

    // ---- Key string for saving / messages
    public static String keyString(Sound s) {
        NamespacedKey k = soundRegistry().getKey(s);
        return k != null ? k.asString() : "<unregistered>";
    }
    public static String keyString(Particle p) {
        NamespacedKey k = particleRegistry().getKey(p);
        return k != null ? k.asString() : "<unregistered>";
    }

    // ---- Tab-complete helpers
    public static List<String> soundKeysStartingWith(String partial, boolean includeNoneFirst) {
        String p = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        Stream<String> base = soundRegistry().keyStream().map(NamespacedKey::asString).filter(k -> k.startsWith(p));
        if (includeNoneFirst && "none".startsWith(p)) {
            return Stream.concat(Stream.of("none"), base).toList();
        }
        return base.toList();
    }

    public static List<String> particleKeysStartingWith(String partial, boolean includeNoneFirst) {
        String p = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        Stream<String> base = particleRegistry().keyStream().map(NamespacedKey::asString).filter(k -> k.startsWith(p));
        if (includeNoneFirst && "none".startsWith(p)) {
            return Stream.concat(Stream.of("none"), base).toList();
        }
        return base.toList();
    }
}
