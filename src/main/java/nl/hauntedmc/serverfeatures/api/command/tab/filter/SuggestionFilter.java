package nl.hauntedmc.serverfeatures.api.command.tab.filter;

import nl.hauntedmc.serverfeatures.api.command.tab.suggestion.Suggestion;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@FunctionalInterface
public interface SuggestionFilter {
    @NotNull Collection<Suggestion> filter(@NotNull String token, @NotNull Collection<Suggestion> candidates);
}
