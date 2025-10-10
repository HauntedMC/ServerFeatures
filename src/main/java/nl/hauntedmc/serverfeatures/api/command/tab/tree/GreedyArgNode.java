package nl.hauntedmc.serverfeatures.api.command.tab.tree;

import nl.hauntedmc.serverfeatures.api.command.tab.MatchState;
import nl.hauntedmc.serverfeatures.api.command.tab.suggestion.Suggestion;
import nl.hauntedmc.serverfeatures.api.command.tab.TabRequest;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class GreedyArgNode extends Node {
    final String name;
    public GreedyArgNode(String name) { this.name = Objects.requireNonNull(name); }
    @Override
    public boolean matchesFully(String token, MatchState stateCopy) { stateCopy.put(name, token, token); return true; }
    @Override
    public Collection<Suggestion> candidates(TabRequest q, String token) { return List.of(); }
    public String toString() { return "ArgGreedy(" + name + ")"; }
}
