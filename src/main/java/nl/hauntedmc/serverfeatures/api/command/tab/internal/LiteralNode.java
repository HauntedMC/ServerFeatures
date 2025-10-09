package nl.hauntedmc.serverfeatures.api.command.tab.internal;

import nl.hauntedmc.serverfeatures.api.command.tab.TabContext;

import java.util.Collection;
import java.util.List;

/** A fixed literal token. */
public final class LiteralNode extends Node {
    private final String literal;

    public LiteralNode(String literal) { this.literal = literal; }

    @Override public boolean matchesFully(String arg) { return literal.equalsIgnoreCase(arg); }

    @Override
    public Collection<String> candidatesFor(TabContext ctx, String token) {
        return List.of(literal);
    }

    @Override public String toString() { return "Literal(" + literal + ")"; }
}
