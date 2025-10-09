package nl.hauntedmc.serverfeatures.api.command.tab.internal;

import nl.hauntedmc.serverfeatures.api.command.tab.provider.SuggestionProvider;

import java.util.function.Predicate;

/**
 * An argument position that only matches tokens satisfying a predicate (e.g., numeric, regex).
 * Suggestions still come from the provider.
 */
public final class PatternArgumentNode extends ArgumentNodeBase {
    private final Predicate<String> matcher;

    public PatternArgumentNode(String name, Predicate<String> matcher, SuggestionProvider provider) {
        super(name, provider);
        this.matcher = matcher == null ? s -> true : matcher;
    }

    @Override public boolean matchesFully(String arg) {
        try { return matcher.test(arg); }
        catch (Throwable t) { return false; }
    }

    @Override public String toString() { return "ArgMatch(" + name + ")"; }
}
