package nl.hauntedmc.serverfeatures.api.command.tab.suggestion;

import nl.hauntedmc.serverfeatures.api.command.tab.TabRequest;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@FunctionalInterface
public interface SuggestionSource {
    @NotNull Collection<Suggestion> suggest(@NotNull TabRequest q);
    static SuggestionSource empty() { return q -> java.util.List.of(); }
}
