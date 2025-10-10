package nl.hauntedmc.serverfeatures.api.command.tab.filter;

import nl.hauntedmc.serverfeatures.api.command.tab.suggestion.Suggestion;

import java.util.*;

public final class Filters {
    private Filters() {}

    public static SuggestionFilter prefixCaseInsensitive() {
        return (token, cands) -> {
            String t = token.toLowerCase(Locale.ROOT);
            if (t.isEmpty()) return cands;
            List<Suggestion> out = new ArrayList<>();
            for (Suggestion s : cands) {
                if (s == null) continue;
                if (s.key().toLowerCase(Locale.ROOT).startsWith(t)) out.add(s);
            }
            return out;
        };
    }

    /** Prefix → contains → simpele subsequence fuzzy. */
    public static SuggestionFilter prefixThenContainsFuzzy() {
        return (token, cands) -> {
            String t = token.toLowerCase(Locale.ROOT);
            if (t.isEmpty()) return cands;
            List<Suggestion> prefix = new ArrayList<>();
            List<Suggestion> contains = new ArrayList<>();
            List<Suggestion> fuzzy = new ArrayList<>();
            for (Suggestion s : cands) {
                String k = s.key().toLowerCase(Locale.ROOT);
                if (k.startsWith(t)) prefix.add(s);
                else if (k.contains(t)) contains.add(s);
                else {
                    int i = 0;
                    for (int j = 0; j < k.length() && i < t.length(); j++) if (k.charAt(j) == t.charAt(i)) i++;
                    if (i == t.length()) fuzzy.add(s);
                }
            }
            prefix.addAll(contains);
            prefix.addAll(fuzzy);
            return prefix;
        };
    }
}
