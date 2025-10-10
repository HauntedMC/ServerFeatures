package nl.hauntedmc.serverfeatures.api.command.tab.tree;

import nl.hauntedmc.serverfeatures.api.command.tab.MatchState;
import nl.hauntedmc.serverfeatures.api.command.tab.suggestion.Suggestion;
import nl.hauntedmc.serverfeatures.api.command.tab.TabRequest;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class LiteralNode extends Node {
    final String literal;
    public LiteralNode(String lit) { this.literal = Objects.requireNonNull(lit, "literal"); }
    @Override
    public boolean matchesFully(String token, MatchState stateCopy) { return literal.equalsIgnoreCase(token); }
    @Override
    public Collection<Suggestion> candidates(TabRequest q, String token) { return List.of(Suggestion.of(literal)); }
    public String toString() { return "Lit(" + literal + ")"; }
}

