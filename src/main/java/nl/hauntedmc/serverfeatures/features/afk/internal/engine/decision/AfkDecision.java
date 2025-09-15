package nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class AfkDecision {
    private final AfkPriority priority;
    private final Set<AfkDecisionType> actions;

    private AfkDecision(AfkPriority priority, Set<AfkDecisionType> actions) {
        this.priority = Objects.requireNonNull(priority);
        this.actions = EnumSet.copyOf(actions);
    }

    public AfkPriority priority() { return priority; }
    public Set<AfkDecisionType> actions() { return actions; }

    public static AfkDecision of(AfkPriority prio, AfkDecisionType... acts) {
        EnumSet<AfkDecisionType> set = EnumSet.noneOf(AfkDecisionType.class);
        set.addAll(Arrays.asList(acts));
        return new AfkDecision(prio, set);
    }

    public static AfkDecision none() {
        return new AfkDecision(AfkPriority.LOW, EnumSet.noneOf(AfkDecisionType.class));
    }

    public boolean isNoop() { return actions.isEmpty(); }
}
