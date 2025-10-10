package nl.hauntedmc.serverfeatures.api.command.tab.tree;

import nl.hauntedmc.serverfeatures.api.command.tab.MatchState;
import nl.hauntedmc.serverfeatures.api.command.tab.suggestion.Suggestion;
import nl.hauntedmc.serverfeatures.api.command.tab.suggestion.SuggestionSource;
import nl.hauntedmc.serverfeatures.api.command.tab.TabRequest;
import nl.hauntedmc.serverfeatures.api.command.tab.types.ArgType;

import java.util.Collection;
import java.util.Objects;

public class ArgNode<T> extends Node {
    final String name;
    final ArgType<T> type;
    final SuggestionSource source;
    public ArgNode(String name, ArgType<T> type, SuggestionSource source) {
        this.name = Objects.requireNonNull(name,"name");
        this.type = Objects.requireNonNull(type,"type");
        this.source = source == null ? type.suggestions() : source;
    }
    @Override
    public boolean matchesFully(String token, MatchState stateCopy) {
        T parsed = type.parseOrNull(token);
        if (parsed != null || type.matches(token)) { stateCopy.put(name, token, parsed); return true; }
        return false;
    }
    @Override
    public Collection<Suggestion> candidates(TabRequest q, String token) { return source.suggest(q); }
    public String toString() { return "Arg(" + name + ")"; }
}

