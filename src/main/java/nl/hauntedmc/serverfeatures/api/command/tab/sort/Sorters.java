package nl.hauntedmc.serverfeatures.api.command.tab.sort;

import nl.hauntedmc.serverfeatures.api.command.tab.suggestion.Suggestion;

import java.text.Collator;
import java.util.*;
import java.util.stream.Collectors;

public final class Sorters {
    private Sorters() {}

    public static SuggestionSorter caseInsensitive() {
        return c -> c.stream().sorted(Comparator.comparing(s -> s.key().toLowerCase(Locale.ROOT))).collect(Collectors.toList());
    }

    public static SuggestionSorter byLengthThenCaseInsensitive() {
        return c -> c.stream().sorted(
                Comparator.comparingInt((Suggestion s) -> s.key().length())
                        .thenComparing(s -> s.key().toLowerCase(Locale.ROOT))
        ).collect(Collectors.toList());
    }

    public static SuggestionSorter collator(Locale locale) {
        Collator col = Collator.getInstance(locale);
        col.setStrength(Collator.PRIMARY);
        return c -> c.stream().sorted((a,b) -> col.compare(a.key(), b.key())).collect(Collectors.toList());
    }

    public static SuggestionSorter none() { return ArrayList::new; }
}
