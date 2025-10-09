package nl.hauntedmc.serverfeatures.api.command.tab.filter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

/** Common filtering strategies for tab suggestions. */
public final class Filters {
    private Filters() {}

    /** Prefix match, case-insensitive. */
    public static SuggestionFilter prefixCaseInsensitive() {
        return (token, candidates) -> {
            String t = token.toLowerCase(Locale.ROOT);
            if (t.isEmpty()) return candidates;
            Collection<String> out = new ArrayList<>();
            for (String c : candidates) {
                if (c == null || c.isBlank()) continue;
                if (c.toLowerCase(Locale.ROOT).startsWith(t)) out.add(c);
            }
            return out;
        };
    }

    /** Contains match, case-insensitive. */
    public static SuggestionFilter containsCaseInsensitive() {
        return (token, candidates) -> {
            String t = token.toLowerCase(Locale.ROOT);
            if (t.isEmpty()) return candidates;
            Collection<String> out = new ArrayList<>();
            for (String c : candidates) {
                if (c == null || c.isBlank()) continue;
                if (c.toLowerCase(Locale.ROOT).contains(t)) out.add(c);
            }
            return out;
        };
    }

    /** Prefix first; then contains — both case-insensitive. */
    public static SuggestionFilter prefixThenContains() {
        return (token, candidates) -> {
            String t = token.toLowerCase(Locale.ROOT);
            if (t.isEmpty()) return candidates;
            Collection<String> prefix = new ArrayList<>();
            Collection<String> contains = new ArrayList<>();
            for (String c : candidates) {
                if (c == null || c.isBlank()) continue;
                String lc = c.toLowerCase(Locale.ROOT);
                if (lc.startsWith(t)) prefix.add(c);
                else if (lc.contains(t)) contains.add(c);
            }
            prefix.addAll(contains);
            return prefix;
        };
    }
}
