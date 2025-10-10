package nl.hauntedmc.serverfeatures.api.command.tab.tree;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.command.tab.TabRequest;

import java.util.function.Function;

/** Marker for nodes that can provide a hover tooltip in completions. */
public interface TooltipCapable {
    /** Set a tooltip supplier (called at tab time). */
    void setTooltip(Function<TabRequest, Component> supplier);
}
