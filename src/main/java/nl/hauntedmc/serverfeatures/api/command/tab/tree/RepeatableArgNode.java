package nl.hauntedmc.serverfeatures.api.command.tab.tree;

import nl.hauntedmc.serverfeatures.api.command.tab.MatchState;
import nl.hauntedmc.serverfeatures.api.command.tab.suggestion.SuggestionSource;
import nl.hauntedmc.serverfeatures.api.command.tab.types.ArgType;

public final class RepeatableArgNode<T> extends ArgNode<T> {
    public RepeatableArgNode(String name, ArgType<T> type, SuggestionSource src) { super(name, type, src); }
    @Override
    public boolean matchesFully(String token, MatchState stateCopy) { return super.matchesFully(token, stateCopy); }
    public String toString() { return "ArgRepeat(" + name + ")"; }
}

