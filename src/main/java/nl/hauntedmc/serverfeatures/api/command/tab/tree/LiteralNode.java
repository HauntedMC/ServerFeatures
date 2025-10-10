package nl.hauntedmc.serverfeatures.api.command.tab.tree;

import nl.hauntedmc.serverfeatures.api.command.tab.MatchState;
import nl.hauntedmc.serverfeatures.api.command.tab.suggestion.Suggestion;
import nl.hauntedmc.serverfeatures.api.command.tab.TabRequest;
import net.kyori.adventure.text.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class LiteralNode extends Node implements TooltipCapable {
    final String literal;
    private Function<TabRequest, Component> tooltipSupplier; // nullable

    public LiteralNode(String lit) { this.literal = Objects.requireNonNull(lit, "literal"); }

    @Override
    public boolean matchesFully(String token, MatchState stateCopy) { return literal.equalsIgnoreCase(token); }

    @Override
    public Collection<Suggestion> candidates(TabRequest q, String token) {
        Component tip = tooltipSupplier != null ? tooltipSupplier.apply(q) : null;
        return List.of(Suggestion.of(literal, literal, tip));
    }

    @Override
    public void setTooltip(Function<TabRequest, Component> supplier) {
        this.tooltipSupplier = supplier;
    }

    public String toString() { return "Lit(" + literal + ")"; }
}
