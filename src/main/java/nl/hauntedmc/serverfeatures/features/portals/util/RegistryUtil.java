package nl.hauntedmc.serverfeatures.features.portals.util;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import nl.hauntedmc.serverfeatures.api.util.BukkitRegistry;
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

    private RegistryUtil() {
    }

    /**
     * Accepts "minecraft:key" or plain "key" (assumes minecraft namespace). Lowercases safely.
     */
    public static NamespacedKey toKey(String s) {
        if (s == null || s.isBlank()) return null;
        String norm = s.contains(":") ? s.toLowerCase(Locale.ROOT) : ("minecraft:" + s.toLowerCase(Locale.ROOT));
        return NamespacedKey.fromString(norm);
    }

    // ---- Resolve
    public static Optional<Sound> resolveSound(String keyLike) {
        NamespacedKey k = toKey(keyLike);
        return k == null ? Optional.empty() : Optional.ofNullable(BukkitRegistry.soundRegistry().get(k));
    }

    public static Optional<Particle> resolveParticle(String keyLike) {
        NamespacedKey k = toKey(keyLike);
        return k == null ? Optional.empty() : Optional.ofNullable(BukkitRegistry.particleRegistry().get(k));
    }

    // ---- Key string for saving / messages
    public static String keyString(Sound s) {
        NamespacedKey k = BukkitRegistry.soundRegistry().getKey(s);
        return k != null ? k.asString() : "<unregistered>";
    }

    public static String keyString(Particle p) {
        NamespacedKey k = BukkitRegistry.particleRegistry().getKey(p);
        return k != null ? k.asString() : "<unregistered>";
    }

    // ---- Tab-complete helpers
    public static List<String> soundKeysStartingWith(String partial, boolean includeNoneFirst) {
        String p = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        Stream<String> base = BukkitRegistry.soundRegistry().keyStream().map(NamespacedKey::asString).filter(k -> k.startsWith(p));
        if (includeNoneFirst && "none".startsWith(p)) {
            return Stream.concat(Stream.of("none"), base).toList();
        }
        return base.toList();
    }

    public static List<String> particleKeysStartingWith(String partial, boolean includeNoneFirst) {
        String p = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        Stream<String> base = BukkitRegistry.particleRegistry().keyStream().map(NamespacedKey::asString).filter(k -> k.startsWith(p));
        if (includeNoneFirst && "none".startsWith(p)) {
            return Stream.concat(Stream.of("none"), base).toList();
        }
        return base.toList();
    }
}
