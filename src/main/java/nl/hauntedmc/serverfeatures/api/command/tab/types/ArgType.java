package nl.hauntedmc.serverfeatures.api.command.tab.types;

import nl.hauntedmc.serverfeatures.api.command.tab.suggestion.Suggestion;
import nl.hauntedmc.serverfeatures.api.command.tab.suggestion.SuggestionSource;
import nl.hauntedmc.serverfeatures.api.command.tab.TabRequest;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface ArgType<T> {
    boolean matches(String token);
    @Nullable T parseOrNull(String token);

    default SuggestionSource suggestions() { return SuggestionSource.empty(); }
    default Collection<Suggestion> suggest(TabRequest q) { return suggestions().suggest(q); }
}
