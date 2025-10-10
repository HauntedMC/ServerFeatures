package nl.hauntedmc.serverfeatures.api.command.tab.types;

import nl.hauntedmc.serverfeatures.api.command.tab.suggestion.Suggestion;
import nl.hauntedmc.serverfeatures.api.command.tab.suggestion.SuggestionSource;
import nl.hauntedmc.serverfeatures.api.command.tab.TabRequest;

import java.util.*;

public final class ArgTypes {
    private ArgTypes() {}

    public static ArgType<String> string() {
        return new ArgType<>() {
            public boolean matches(String token) { return true; }
            public String parseOrNull(String token) { return token; }
        };
    }

    /** Greedy remainder (type zelf valideert alles; 'greedy' wordt door DSL verwerkt). */
    public static ArgType<String> greedyString() { return string(); }

    public static ArgType<Integer> integer(int min, int max) {
        return new ArgType<>() {
            public boolean matches(String token) {
                try { int v = Integer.parseInt(token); return v >= min && v <= max; } catch (Exception e) { return false; }
            }
            public Integer parseOrNull(String token) {
                try { int v = Integer.parseInt(token); return (v >= min && v <= max) ? v : null; } catch (Exception e) { return null; }
            }
            public SuggestionSource suggestions() {
                List<Suggestion> s = new ArrayList<>();
                for (int v = min; v <= Math.min(max, min + 25); v++) s.add(Suggestion.of(String.valueOf(v)));
                return q -> s;
            }
        };
    }

    public static ArgType<Double> ddouble(double min, double max) {
        return new ArgType<>() {
            public boolean matches(String token) {
                try { double v = Double.parseDouble(token); return v >= min && v <= max; } catch (Exception e) { return false; }
            }
            public Double parseOrNull(String token) {
                try { double v = Double.parseDouble(token); return (v >= min && v <= max) ? v : null; } catch (Exception e) { return null; }
            }
        };
    }

    public static ArgType<Boolean> bool() {
        return new ArgType<>() {
            final Set<String> acc = Set.of("true","false","yes","no","on","off");
            public boolean matches(String token) { return acc.contains(token.toLowerCase(Locale.ROOT)); }
            public Boolean parseOrNull(String token) {
                String t = token.toLowerCase(Locale.ROOT);
                return switch (t) {
                    case "true","yes","on" -> Boolean.TRUE;
                    case "false","no","off" -> Boolean.FALSE;
                    default -> null;
                };
            }
            public SuggestionSource suggestions() { return q -> List.of(Suggestion.of("true"), Suggestion.of("false")); }
        };
    }

    public static <E extends Enum<E>> ArgType<E> enumOf(Class<E> en) {
        E[] vals = en.getEnumConstants();
        List<Suggestion> sug = Arrays.stream(vals).map(v -> Suggestion.of(v.name().toLowerCase(Locale.ROOT))).toList();
        return new ArgType<>() {
            public boolean matches(String token) {
                String t = token.toLowerCase(Locale.ROOT);
                for (E v : vals) if (v.name().toLowerCase(Locale.ROOT).equals(t)) return true;
                return false;
            }
            public E parseOrNull(String token) {
                try { return Enum.valueOf(en, token.toUpperCase(Locale.ROOT)); } catch (Exception e) { return null; }
            }
            public Collection<Suggestion> suggest(TabRequest q) { return sug; }
        };
    }
}
