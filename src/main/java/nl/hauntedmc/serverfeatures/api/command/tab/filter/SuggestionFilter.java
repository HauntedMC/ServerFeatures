package nl.hauntedmc.serverfeatures.api.command.tab.filter;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@FunctionalInterface
public interface SuggestionFilter {
    @NotNull Collection<String> filter(@NotNull String token, @NotNull Collection<String> candidates);
}
