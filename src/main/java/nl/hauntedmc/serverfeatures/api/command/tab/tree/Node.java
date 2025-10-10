package nl.hauntedmc.serverfeatures.api.command.tab.tree;

import nl.hauntedmc.serverfeatures.api.command.tab.*;
import nl.hauntedmc.serverfeatures.api.command.tab.suggestion.Suggestion;

import java.util.*;
import java.util.function.Predicate;

public abstract class Node {
    public final List<Node> children = new ArrayList<>();
    final Set<String> requireAll = new LinkedHashSet<>();
    final Set<String> requireAny = new LinkedHashSet<>();
    final Set<String> deny = new LinkedHashSet<>();
    final List<Predicate<TabRequest>> conditions = new ArrayList<>();

    public Node require(String p) { if (p != null) requireAll.add(p); return this; }
    public Node requireAny(Collection<String> perms) { if (perms != null) requireAny.addAll(perms); return this; }
    public Node deny(String p) { if (p != null) deny.add(p); return this; }
    public Node when(Predicate<TabRequest> cond) { if (cond != null) conditions.add(cond); return this; }

    public boolean allowed(TabRequest q) {
        for (String p : deny) if (q.sender().hasPermission(p)) return false;
        for (String p : requireAll) if (!q.sender().hasPermission(p)) return false;
        if (!requireAny.isEmpty()) {
            boolean ok = false;
            for (String p : requireAny) if (q.sender().hasPermission(p)) { ok = true; break; }
            if (!ok) return false;
        }
        for (var c : conditions) if (!c.test(q)) return false;
        return true;
    }

    public abstract boolean matchesFully(String token, MatchState stateCopy);
    public abstract Collection<Suggestion> candidates(TabRequest q, String token);
}
