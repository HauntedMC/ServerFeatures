package nl.hauntedmc.serverfeatures.api.command.tab.internal;

import nl.hauntedmc.serverfeatures.api.command.tab.provider.SuggestionProvider;
import nl.hauntedmc.serverfeatures.api.command.tab.TabContext;

import java.util.Collection;

/** An argument position that sources its suggestions from a provider. */
public final class ArgumentNode extends Node {
    private final String name;
    private final SuggestionProvider provider;

    public ArgumentNode(String name, SuggestionProvider provider) {
        this.name = name;
        this.provider = provider;
    }

    /** Arguments accept any token (they "consume" the step). */
    @Override public boolean matchesFully(String arg) { return true; }

    @Override
    public Collection<String> candidatesFor(TabContext ctx, String token) {
        return provider == null ? java.util.List.of() : provider.suggest(ctx);
    }

    @Override public String toString() { return "Arg(" + name + ")"; }
}
