package nl.hauntedmc.serverfeatures.api.command.tab.internal;

import nl.hauntedmc.serverfeatures.api.command.tab.provider.SuggestionProvider;

/**
 * A repeatable argument that can be provided multiple times in a row.
 * After matching a token, the node remains in frontier so it can match again,
 * and also allows advancing to its children.
 */
public final class RepeatableArgumentNode extends ArgumentNodeBase {
    public RepeatableArgumentNode(String name, SuggestionProvider provider) { super(name, provider); }
    @Override public boolean matchesFully(String arg) { return true; }
    @Override public String toString() { return "ArgRepeat(" + name + ")"; }
}
