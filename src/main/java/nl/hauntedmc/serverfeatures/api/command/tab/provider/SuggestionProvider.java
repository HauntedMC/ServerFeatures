package nl.hauntedmc.serverfeatures.api.command.tab.provider;

import nl.hauntedmc.serverfeatures.api.command.tab.TabContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.function.Function;

@FunctionalInterface
public interface SuggestionProvider {
    @NotNull Collection<String> suggest(@NotNull TabContext ctx);

    /** Create a provider that refines based on the current token. */
    default SuggestionProvider refined(Function<String, Collection<String>> refiner) {
        return ctx -> refiner.apply(ctx.lastTokenOrEmpty());
    }

    static SuggestionProvider of(Collection<String> values) {
        return ctx -> values;
    }

    static SuggestionProvider empty() { return ctx -> java.util.List.of(); }
}
