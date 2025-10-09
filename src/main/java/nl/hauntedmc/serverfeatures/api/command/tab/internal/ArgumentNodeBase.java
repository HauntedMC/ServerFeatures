package nl.hauntedmc.serverfeatures.api.command.tab.internal;

import nl.hauntedmc.serverfeatures.api.command.tab.TabContext;
import nl.hauntedmc.serverfeatures.api.command.tab.provider.SuggestionProvider;

/** Base class for argument nodes with providers. */
public abstract class ArgumentNodeBase extends Node {
    protected final String name;
    protected final SuggestionProvider provider;

    protected ArgumentNodeBase(String name, SuggestionProvider provider) {
        this.name = name;
        this.provider = provider;
    }

    @Override
    public java.util.Collection<String> candidatesFor(TabContext ctx, String token) {
        return provider == null ? java.util.List.of() : provider.suggest(ctx);
    }
}
