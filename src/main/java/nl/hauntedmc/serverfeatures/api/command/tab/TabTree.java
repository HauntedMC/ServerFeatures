package nl.hauntedmc.serverfeatures.api.command.tab;

import nl.hauntedmc.serverfeatures.api.command.tab.filter.SuggestionFilter;
import nl.hauntedmc.serverfeatures.api.command.tab.filter.Filters;
import nl.hauntedmc.serverfeatures.api.command.tab.sort.SuggestionSorter;
import nl.hauntedmc.serverfeatures.api.command.tab.sort.Sorters;
import nl.hauntedmc.serverfeatures.api.command.tab.suggestion.Suggestion;
import nl.hauntedmc.serverfeatures.api.command.tab.suggestion.SuggestionSource;
import nl.hauntedmc.serverfeatures.api.command.tab.tree.*;
import nl.hauntedmc.serverfeatures.api.command.tab.types.ArgType;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Function;

/** Paper-gerichte boom met één duidelijke DSL: literals, arg, argRepeatable, argGreedy, seq, alt. */
public final class TabTree {
    private final List<Node> roots;
    private final SuggestionFilter filter;
    private final SuggestionSorter sorter;
    private final int maxResults;
    private final boolean showRootArgs;

    private TabTree(List<Node> roots, SuggestionFilter filter, SuggestionSorter sorter, int maxResults, boolean showRootArgs) {
        this.roots = roots; this.filter = filter; this.sorter = sorter; this.maxResults = maxResults; this.showRootArgs = showRootArgs;
    }

    public static Builder builder() { return new Builder(); }

    public @NotNull List<Suggestion> complete(@NotNull TabRequest req) {
        String[] args = req.args();
        final String token = args.length == 0 ? "" : args[args.length - 1];
        final String[] prev = args.length <= 1 ? new String[0] : Arrays.copyOf(args, args.length - 1);

        List<Map.Entry<Node, MatchState>> frontier = new ArrayList<>();
        MatchState base = new MatchState();
        for (Node r : roots) frontier.add(Map.entry(r, base.copy()));

        // consumeer eerdere tokens
        for (String t : prev) {
            frontier = descend(frontier, req, t);
            if (frontier.isEmpty()) break;
        }

        LinkedHashSet<Suggestion> raw = new LinkedHashSet<>();
        if (args.length <= 1) {
            for (Node r : roots) {
                if (!r.allowed(req)) continue;
                if (r instanceof LiteralNode) raw.addAll(r.candidates(req, token));
                else if (showRootArgs) raw.addAll(r.candidates(req, token));
            }
        } else {
            for (var e : frontier) {
                Node n = e.getKey();
                MatchState st = e.getValue();
                TabRequest withState = new TabRequest(req.sender(), req.alias(), req.args(), st, req::callSync);
                if (!n.allowed(withState)) continue;
                raw.addAll(n.candidates(withState, token));
            }
        }

        Collection<Suggestion> filtered = filter.filter(token, raw);
        List<Suggestion> sorted = sorter.sort(filtered);
        return sorted.size() > maxResults ? sorted.subList(0, maxResults) : sorted;
    }

    private List<Map.Entry<Node, MatchState>> descend(List<Map.Entry<Node, MatchState>> current, TabRequest req, String token) {
        List<Map.Entry<Node, MatchState>> next = new ArrayList<>();
        for (var e : current) {
            Node n = e.getKey();
            MatchState s = e.getValue();
            if (!n.allowed(req)) continue;
            MatchState sc = s.copy();
            if (n.matchesFully(token, sc)) {
                if (n instanceof RepeatableArgNode<?>) next.add(Map.entry(n, sc));
                for (Node c : n.children) next.add(Map.entry(c, sc.copy()));
            }
        }
        return next;
    }

    /* ===================== DSL ===================== */

    public static final class Builder {
        private final List<Node> roots = new ArrayList<>();
        private SuggestionFilter filter = Filters.prefixThenContainsFuzzy();
        private SuggestionSorter sorter = Sorters.caseInsensitive();
        private int maxResults = 100;
        private boolean showRootArgs = false;

        public Builder filter(SuggestionFilter f) { this.filter = Objects.requireNonNull(f); return this; }
        public Builder sorter(SuggestionSorter s) { this.sorter = Objects.requireNonNull(s); return this; }
        public Builder maxResults(int max) { this.maxResults = Math.max(1, max); return this; }
        public Builder showRootArguments(boolean show) { this.showRootArgs = show; return this; }

        public Root root() { return new Root(this, roots); }
        TabTree buildInternal() { return new TabTree(List.copyOf(roots), filter, sorter, maxResults, showRootArgs); }

        public static final class Root extends Branch<Root> {
            private final Builder owner;
            Root(Builder owner, List<Node> attach) { super(attach); this.owner = owner; }
            @Override protected Root self() { return this; }
            public TabTree build() { return owner.buildInternal(); }
        }

        /** Basistak (siblings). Gebruik seq() voor posities, alt() voor alternatieven. */
        public static class Branch<B extends Branch<B>> {
            protected List<Node> attach;
            protected Branch(List<Node> attach) { this.attach = attach; }
            protected B self() { return (B) this; }

            public B literal(String lit) { attach.add(new LiteralNode(lit)); return self(); }

            public B literal(String lit, java.util.function.Consumer<Configurator> cfg) {
                LiteralNode n = new LiteralNode(lit); attach.add(n);
                if (cfg != null) cfg.accept(new Configurator(n)); return self();
            }

            public <T> B arg(String name, ArgType<T> type, SuggestionSource source) { attach.add(new ArgNode<>(name, type, source)); return self(); }
            public <T> B arg(String name, ArgType<T> type) { return arg(name, type, null); }

            public <T> B argRepeatable(String name, ArgType<T> type, SuggestionSource source) { attach.add(new RepeatableArgNode<>(name, type, source)); return self(); }
            public <T> B argRepeatable(String name, ArgType<T> type) { return argRepeatable(name, type, null); }

            public B argGreedy(String name) { attach.add(new GreedyArgNode(name)); return self(); }

            public Sequential seq() { return new Sequential(attach); }

            public B alt(java.util.function.Consumer<Branch<?>> siblings) {
                Branch<?> sib = new Branch<>(attach);
                siblings.accept(sib);
                return self();
            }

            public B require(String perm) { for (Node n : attach) n.require(perm); return self(); }
            public B requireAny(String... perms) { for (Node n : attach) n.requireAny(Arrays.asList(perms)); return self(); }
            public B deny(String... perms) { for (Node n : attach) for (String p : perms) n.deny(p); return self(); }
            public B when(Predicate<TabRequest> cond) { for (Node n : attach) n.when(cond); return self(); }

            public static final class Configurator {
                private final Node node;
                Configurator(Node node) { this.node = node; }

                public Configurator require(String perm) { node.require(perm); return this; }
                public Configurator requireAny(String... perms) { node.requireAny(Arrays.asList(perms)); return this; }
                public Configurator deny(String... perms) { for (String p : perms) node.deny(p); return this; }
                public Configurator when(Predicate<TabRequest> cond) { node.when(cond); return this; }

                /** Add a static tooltip to this node (supported for literals). */
                public Configurator tooltip(Component constant) {
                    if (node instanceof TooltipCapable t) {
                        t.setTooltip(q -> constant);
                    }
                    return this;
                }

                /** Add a dynamic tooltip (supported for literals). */
                public Configurator tooltip(Function<TabRequest, Component> supplier) {
                    if (node instanceof TooltipCapable t) {
                        t.setTooltip(supplier);
                    }
                    return this;
                }

                public Sequential child() { return new Sequential(node.children); }
            }
        }

        /** Sequentiële tak: elke call schuift cursor door naar kinderen (volgende positie). */
        public static final class Sequential extends Branch<Sequential> {
            Sequential(List<Node> attach) { super(attach); }
            @Override protected Sequential self() { return this; }

            @Override public Sequential literal(String lit) {
                LiteralNode n = new LiteralNode(lit); attach.add(n); this.attach = n.children; return this;
            }

            @Override public Sequential literal(String lit, java.util.function.Consumer<Configurator> cfg) {
                LiteralNode n = new LiteralNode(lit); attach.add(n);
                if (cfg != null) cfg.accept(new Configurator(n));
                this.attach = n.children; return this;
            }

            @Override public <T> Sequential arg(String name, ArgType<T> type, SuggestionSource source) {
                ArgNode<T> n = new ArgNode<>(name, type, source); attach.add(n); this.attach = n.children; return this;
            }

            @Override public <T> Sequential argRepeatable(String name, ArgType<T> type, SuggestionSource source) {
                RepeatableArgNode<T> n = new RepeatableArgNode<>(name, type, source); attach.add(n); this.attach = n.children; return this;
            }

            @Override public Sequential argGreedy(String name) {
                GreedyArgNode n = new GreedyArgNode(name); attach.add(n); this.attach = n.children; return this;
            }
        }
    }
}
