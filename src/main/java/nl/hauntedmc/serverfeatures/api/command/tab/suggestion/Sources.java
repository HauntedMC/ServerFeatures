package nl.hauntedmc.serverfeatures.api.command.tab.suggestion;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.command.tab.TabRequest;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class Sources {
    private Sources() {}

    public static SuggestionSource ofStrings(Collection<String> vals) {
        List<Suggestion> list = vals == null ? List.of() : vals.stream().map(Suggestion::of).toList();
        return q -> list;
    }
    public static SuggestionSource ofStrings(String... vals) { return ofStrings(Arrays.asList(vals)); }

    public static SuggestionSource union(SuggestionSource... sources) {
        return q -> {
            LinkedHashMap<String, Suggestion> map = new LinkedHashMap<>();
            for (SuggestionSource s : sources) {
                for (Suggestion sug : (s == null ? List.<Suggestion>of() : s.suggest(q))) {
                    map.putIfAbsent(sug.key(), sug);
                }
            }
            return map.values();
        };
    }

    public static SuggestionSource dynamic(Function<TabRequest, Collection<String>> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return q -> {
            Collection<String> v = supplier.apply(q);
            if (v == null) return List.of();
            return v.stream().map(Suggestion::of).toList();
        };
    }

    public static SuggestionSource dynamicRich(Function<TabRequest, Collection<Suggestion>> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return q -> {
            Collection<Suggestion> v = supplier.apply(q);
            return v == null ? List.of() : v;
        };
    }

    /** Memoize resultaten met TTL (per command thread-safe). */
    public static SuggestionSource memoize(SuggestionSource base, Duration ttl) {
        AtomicReference<Collection<Suggestion>> cache = new AtomicReference<>(List.of());
        AtomicLong until = new AtomicLong(0);
        long ttlMs = Math.max(0, ttl.toMillis());
        return q -> {
            long now = System.currentTimeMillis();
            if (now < until.get()) return cache.get();
            Collection<Suggestion> fresh = base.suggest(q);
            cache.set(fresh);
            until.set(now + ttlMs);
            return fresh;
        };
    }

    /** Async-safe: vraag main thread om de lijst (bv. voor Bukkit API). */
    public static SuggestionSource mainThread(Supplier<Collection<String>> supplier) {
        return q -> {
            Collection<String> vals = q.callSync(supplier);
            return vals == null ? List.of() : vals.stream().map(Suggestion::of).toList();
        };
    }

    // Handige standaardbronnen

    public static SuggestionSource onlinePlayerNames() {
        // Gebruik mainThread want Bukkit.getOnlinePlayers is niet thread-safe
        return mainThread(() -> Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
    }

    public static SuggestionSource offlinePlayerNames() {
        return mainThread(() -> {
            OfflinePlayer[] arr = Bukkit.getOfflinePlayers();
            LinkedHashSet<String> out = new LinkedHashSet<>();
            for (OfflinePlayer p : arr) if (p.getName() != null) out.add(p.getName());
            return out;
        });
    }

    public static SuggestionSource booleans() { return ofStrings("true","false"); }
    public static SuggestionSource yesNo()    { return ofStrings("yes","no"); }
    public static SuggestionSource onOff()    { return ofStrings("on","off"); }

    public static SuggestionSource intRange(int min, int max, int step) {
        if (step <= 0 || max < min) return SuggestionSource.empty();
        return q -> {
            String token = q.last();
            int focus;
            try { focus = Integer.parseInt(token); } catch (Exception e) { focus = min; }
            int span = Math.max(0, max - min);
            int window = Math.max(step, Math.min(1000, span));
            int start = Math.max(min, focus - window);
            int end = Math.min(max, focus + window);
            start = min + Math.floorDiv((start - min), step) * step;
            LinkedHashSet<Suggestion> out = new LinkedHashSet<>();
            for (int i = start; i <= end; i += step) out.add(Suggestion.of(String.valueOf(i)));
            return out;
        };
    }

    public static SuggestionSource doubleRange(double min, double max, double step, int scale) {
        if (step <= 0 || max < min) return SuggestionSource.empty();
        return q -> {
            String token = q.last();
            double focus;
            try { focus = Double.parseDouble(token); } catch (Exception e) { focus = min; }
            double span = Math.max(0, max - min);
            double window = Math.min(span, 100 * step);
            double start = Math.max(min, focus - window);
            double end = Math.min(max, focus + window);
            start = min + Math.floor((start - min) / step) * step;
            LinkedHashSet<Suggestion> out = new LinkedHashSet<>();
            for (double d = start; d <= end + 1e-9; d += step) {
                out.add(Suggestion.of(String.format(Locale.ROOT, "%." + scale + "f", d)));
            }
            return out;
        };
    }

    public static SuggestionSource withTooltip(SuggestionSource base, Function<Suggestion, Component> tooltip) {
        Objects.requireNonNull(tooltip, "tooltip");
        return q -> base.suggest(q).stream()
                .map(s -> Suggestion.of(s.text(), s.insert() != null ? s.insert() : s.text(), tooltip.apply(s)))
                .toList();
    }
}
