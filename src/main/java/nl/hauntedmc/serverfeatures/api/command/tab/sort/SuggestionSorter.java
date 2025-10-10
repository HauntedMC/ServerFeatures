package nl.hauntedmc.serverfeatures.api.command.tab.sort;

import nl.hauntedmc.serverfeatures.api.command.tab.suggestion.Suggestion;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

@FunctionalInterface
public interface SuggestionSorter {
    @NotNull List<Suggestion> sort(@NotNull Collection<Suggestion> candidates);
}
