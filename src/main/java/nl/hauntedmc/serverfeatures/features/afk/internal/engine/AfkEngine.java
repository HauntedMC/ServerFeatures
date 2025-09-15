package nl.hauntedmc.serverfeatures.features.afk.internal.engine;

import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkDecision;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkDecisionType;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkPriority;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.event.AfkEvent;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.player.AfkPlayerState;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.rules.*;

import java.util.ArrayList;
import java.util.List;

public final class AfkEngine {

    private final List<AfkRule> rules = new ArrayList<>();
    private final AfkServiceFacade facade;

    public AfkEngine(AfkServiceFacade facade) {
        this.facade = facade;
        rules.add(new ChatCommandRule());
        rules.add(new InventoryStrongRule());
        rules.add(new StrongActionRule());
        rules.add(new WeakActionRule());
        rules.add(new TeleportRule());
        rules.add(new MovementRule());
    }

    public AfkDecision evaluate(AfkEvent event, AfkPlayerState state) {
        AfkPriority top = null;
        List<AfkDecision> winners = new ArrayList<>();
        for (AfkRule r : rules) {
            AfkDecision d = r.evaluate(event, state, facade);
            if (d == null || d.isNoop()) continue;
            if (top == null || d.priority().ordinal() < top.ordinal()) {
                top = d.priority();
                winners.clear();
                winners.add(d);
            } else if (d.priority() == top) {
                winners.add(d);
            }
        }
        if (winners.isEmpty()) return AfkDecision.none();

        var merged = java.util.EnumSet.noneOf(AfkDecisionType.class);
        for (AfkDecision d : winners) merged.addAll(d.actions());
        return AfkDecision.of(top, merged.toArray(new AfkDecisionType[0]));
    }
}
