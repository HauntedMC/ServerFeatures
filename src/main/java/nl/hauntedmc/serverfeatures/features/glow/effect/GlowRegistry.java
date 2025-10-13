package nl.hauntedmc.serverfeatures.features.glow.effect;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;
import java.util.function.Function;

/**
 * Holds and exposes all available glow effects.
 * Provides deterministic ordering for menu rendering.
 */
public final class GlowRegistry {

    private final Map<String, GlowEffect> byId = new LinkedHashMap<>();

    public GlowRegistry() {
        registerAll(defaultStaticColors());
        register(new RainbowGlow());
        register(new HauntedGlow());
    }

    /**
     * All registered effects in display order.
     */
    public List<GlowEffect> all() {
        return List.copyOf(byId.values());
    }

    public Optional<GlowEffect> find(String id) {
        return Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    public void register(GlowEffect effect) {
        Objects.requireNonNull(effect, "effect");
        byId.put(effect.id().toLowerCase(Locale.ROOT), effect);
    }

    public void registerAll(Collection<? extends GlowEffect> effects) {
        for (GlowEffect e : effects) register(e);
    }

    private static List<GlowEffect> defaultStaticColors() {
        List<NamedTextColor> ordered = List.of(
                NamedTextColor.BLACK,
                NamedTextColor.DARK_BLUE,
                NamedTextColor.DARK_GREEN,
                NamedTextColor.DARK_AQUA,
                NamedTextColor.DARK_RED,
                NamedTextColor.DARK_PURPLE,
                NamedTextColor.GOLD,
                NamedTextColor.GRAY,
                NamedTextColor.DARK_GRAY,
                NamedTextColor.BLUE,
                NamedTextColor.GREEN,
                NamedTextColor.AQUA,
                NamedTextColor.RED,
                NamedTextColor.LIGHT_PURPLE,
                NamedTextColor.YELLOW,
                NamedTextColor.WHITE
        );
        return ordered.stream().map((Function<NamedTextColor, GlowEffect>) StaticColorGlow::new).toList();
    }
}
