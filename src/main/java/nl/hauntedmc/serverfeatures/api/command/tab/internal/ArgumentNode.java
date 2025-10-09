package nl.hauntedmc.serverfeatures.api.command.tab.internal;

import nl.hauntedmc.serverfeatures.api.command.tab.provider.SuggestionProvider;

/** An argument position that accepts any token. */
public final class ArgumentNode extends ArgumentNodeBase {
    public ArgumentNode(String name, SuggestionProvider provider) { super(name, provider); }
    @Override public boolean matchesFully(String arg) { return true; }
    @Override public String toString() { return "Arg(" + name + ")"; }
}
