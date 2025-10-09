package nl.hauntedmc.serverfeatures.api.command.tab.internal;

import nl.hauntedmc.serverfeatures.api.command.tab.TabContext;

import java.util.*;
import java.util.function.Predicate;

/** Base node with permissions, conditions, and children. */
public abstract class Node {
    protected final List<Predicate<TabContext>> conditions = new ArrayList<>();
    public final List<Node> children = new ArrayList<>();
    protected final Set<String> requireAll = new LinkedHashSet<>();
    protected final Set<String> requireAny = new LinkedHashSet<>();
    protected final Set<String> deny = new LinkedHashSet<>();

    public Node require(String permission) { if (permission != null) requireAll.add(permission); return this; }
    public Node requireAll(Collection<String> perms) { if (perms != null) perms.forEach(this::require); return this; }
    public Node requireAny(Collection<String> perms) { if (perms != null) requireAny.addAll(perms); return this; }
    public Node deny(String permission) { if (permission != null) deny.add(permission); return this; }
    public Node denyAll(Collection<String> perms) { if (perms != null) deny.addAll(perms); return this; }
    public Node when(Predicate<TabContext> cond) { if (cond != null) conditions.add(cond); return this; }

    public boolean allowed(TabContext ctx) {
        // deny takes precedence
        for (String p : deny) if (ctx.sender().hasPermission(p)) return false;

        // requireAll
        for (String p : requireAll) if (!ctx.sender().hasPermission(p)) return false;

        // requireAny (if present, at least one must pass)
        if (!requireAny.isEmpty()) {
            boolean ok = false;
            for (String p : requireAny) { if (ctx.sender().hasPermission(p)) { ok = true; break; } }
            if (!ok) return false;
        }

        for (var c : conditions) if (!c.test(ctx)) return false;
        return true;
    }

    /** True if this node consumes the argument fully. */
    public abstract boolean matchesFully(String arg);

    /** Suggestions produced by this node given the current token (not previous args). */
    public abstract java.util.Collection<String> candidatesFor(TabContext ctx, String token);
}
