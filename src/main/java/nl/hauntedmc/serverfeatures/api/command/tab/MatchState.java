package nl.hauntedmc.serverfeatures.api.command.tab;

import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class MatchState {
    private final LinkedHashMap<String, String> raw = new LinkedHashMap<>();
    private final LinkedHashMap<String, Object> parsed = new LinkedHashMap<>();

    public MatchState copy() {
        MatchState s = new MatchState();
        s.raw.putAll(raw);
        s.parsed.putAll(parsed);
        return s;
    }

    public void put(String name, String rawValue, @Nullable Object parsedValue) {
        if (name != null) raw.put(name, rawValue);
        if (name != null && parsedValue != null) parsed.put(name, parsedValue);
    }

    public Optional<String> raw(String name) { return Optional.ofNullable(raw.get(name)); }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String name, Class<T> type) {
        Object v = parsed.get(name);
        if (type.isInstance(v)) return Optional.of((T) v);
        return Optional.empty();
    }

    public Map<String,String> rawView() { return Collections.unmodifiableMap(raw); }
    public Map<String,Object> parsedView() { return Collections.unmodifiableMap(parsed); }
}
