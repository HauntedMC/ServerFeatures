package nl.hauntedmc.serverfeatures.api.command.tab.provider;

import nl.hauntedmc.serverfeatures.api.command.tab.TabContext;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Providers {
    private Providers() {}

    public static SuggestionProvider staticList(Collection<String> values) {
        List<String> v = values == null ? List.of() : new ArrayList<>(values);
        return SuggestionProvider.of(v);
    }
    public static SuggestionProvider staticList(String... values) {
        return staticList(Arrays.asList(values));
    }

    /** Merge multiple providers; stable order with de-duplication. */
    public static SuggestionProvider union(SuggestionProvider... providers) {
        return ctx -> {
            LinkedHashSet<String> out = new LinkedHashSet<>();
            for (SuggestionProvider p : providers) {
                Collection<String> part = p == null ? List.of() : p.suggest(ctx);
                out.addAll(part);
            }
            return out;
        };
    }

    /** Provider whose values are computed at call-time. */
    public static SuggestionProvider dynamic(Function<TabContext, Collection<String>> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return ctx -> {
            Collection<String> col = supplier.apply(ctx);
            return col == null ? List.of() : col;
        };
    }

    public static SuggestionProvider none() { return SuggestionProvider.empty(); }

    public static SuggestionProvider onlinePlayers() {
        return ctx -> Bukkit.getOnlinePlayers().stream()
                .map(p -> {
                    String dn = p.getDisplayName();
                    return (dn != null && !dn.isBlank()) ? dn : p.getName();
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static SuggestionProvider onlinePlayerNames() {
        return ctx -> Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static SuggestionProvider offlinePlayerNames() {
        return ctx -> {
            OfflinePlayer[] off = Bukkit.getOfflinePlayers();
            LinkedHashSet<String> out = new LinkedHashSet<>();
            for (OfflinePlayer p : off) if (p.getName() != null) out.add(p.getName());
            return out;
        };
    }

    public static SuggestionProvider booleans() { return staticList("true","false"); }
    public static SuggestionProvider yesNo()    { return staticList("yes","no"); }
    public static SuggestionProvider onOff()    { return staticList("on","off"); }

    /** Integer range with a rolling window; capped to avoid huge lists. */
    public static SuggestionProvider intRange(int min, int max, int step) {
        return ctx -> {
            if (step <= 0 || max < min) return List.of();
            String token = ctx.lastTokenOrEmpty();
            boolean numeric = token.chars().allMatch(Character::isDigit);
            int focus = numeric ? safeParse(token, min) : min;
            int span = Math.max(0, max - min);
            int window = Math.max(step, Math.min(1000, span)); // Cap
            int start = clampToStep(Math.max(min, focus - window), min, step);
            int end = Math.min(max, focus + window);
            LinkedHashSet<String> out = new LinkedHashSet<>();
            for (int i = start; i <= end; i += step) out.add(String.valueOf(i));
            return out;
        };
    }

    /** Double range with step and scale (0..6). */
    public static SuggestionProvider doubleRange(double min, double max, double step, int scale) {
        return ctx -> {
            if (step <= 0 || max < min || scale < 0 || scale > 6) return List.of();
            String token = ctx.lastTokenOrEmpty();
            double focus = safeParseDouble(token, min);
            double span = Math.max(0, max - min);
            double window = Math.max(step, Math.min(100 * step, span));
            double start = Math.max(min, focus - window);
            double end = Math.min(max, focus + window);
            start = align(start, min, step);
            LinkedHashSet<String> out = new LinkedHashSet<>();
            for (double d = start; d <= end + 1e-12; d += step) {
                out.add(format(d, scale));
            }
            return out;
        };
    }

    public static <E extends Enum<E>> SuggestionProvider fromEnum(Class<E> enumClass) {
        Objects.requireNonNull(enumClass, "enumClass");
        return ctx -> Arrays.stream(enumClass.getEnumConstants())
                .map(e -> e.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static SuggestionProvider mapKeys(Map<String, ?> map)   { return staticList(map.keySet()); }
    public static SuggestionProvider mapValues(Map<?, String> map) { return staticList(map.values()); }

    private static int clampToStep(int value, int min, int step) {
        int delta = Math.floorMod(value - min, step);
        return value - delta;
    }
    private static int safeParse(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (Exception ignored) { return fallback; }
    }
    private static double safeParseDouble(String s, double fallback) {
        try { return Double.parseDouble(s); } catch (Exception ignored) { return fallback; }
    }
    private static double align(double value, double min, double step) {
        double k = Math.floor((value - min) / step);
        return min + k * step;
    }
    private static String format(double d, int scale) {
        return String.format(Locale.ROOT, "%." + scale + "f", d);
    }
}
