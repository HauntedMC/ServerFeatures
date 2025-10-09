package nl.hauntedmc.serverfeatures.api.command.tab.sort;

import java.text.Collator;
import java.util.*;
import java.util.stream.Collectors;

/** Common sorting strategies for tab suggestions. */
public final class Sorters {
    private Sorters() {}

    /** Case-insensitive (ROOT). */
    public static SuggestionSorter caseInsensitive() {
        return candidates -> candidates.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    /** Case-sensitive natural order (Unicode code point). */
    public static SuggestionSorter natural() {
        return candidates -> candidates.stream()
                .sorted()
                .collect(Collectors.toList());
    }

    /** Locale-aware collation (human-friendly). */
    public static SuggestionSorter collator(Locale locale) {
        Objects.requireNonNull(locale, "locale");
        final Collator collator = Collator.getInstance(locale);
        collator.setStrength(Collator.PRIMARY);
        return candidates -> candidates.stream()
                .sorted(collator)
                .collect(Collectors.toList());
    }

    /** By length, then case-insensitive (ROOT). */
    public static SuggestionSorter byLengthThenCaseInsensitive() {
        return candidates -> candidates.stream()
                .sorted(Comparator.comparingInt(String::length)
                        .thenComparing(String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    /** Stable/as-is order (copy). */
    public static SuggestionSorter none() {
        return ArrayList::new;
    }

    /** Reverse any sorter. */
    public static SuggestionSorter reversed(SuggestionSorter base) {
        Objects.requireNonNull(base, "base");
        return candidates -> {
            List<String> list = base.sort(candidates);
            Collections.reverse(list);
            return list;
        };
    }

    public static SuggestionSorter caseInsensitiveDesc() { return reversed(caseInsensitive()); }
    public static SuggestionSorter naturalDesc()        { return reversed(natural()); }
    public static SuggestionSorter collatorDesc(Locale locale) { return reversed(collator(locale)); }
    public static SuggestionSorter byLengthThenCaseInsensitiveDesc() {
        return reversed(byLengthThenCaseInsensitive());
    }
}
