package nl.hauntedmc.serverfeatures.api.command.tab.sort;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

@FunctionalInterface
public interface SuggestionSorter {
    @NotNull List<String> sort(@NotNull Collection<String> candidates);
}
