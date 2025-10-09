package nl.hauntedmc.serverfeatures.api.command.tab;

import nl.hauntedmc.serverfeatures.api.command.tab.filter.SuggestionFilter;
import nl.hauntedmc.serverfeatures.api.command.tab.filter.Filters;
import nl.hauntedmc.serverfeatures.api.command.tab.internal.ArgumentNode;
import nl.hauntedmc.serverfeatures.api.command.tab.internal.LiteralNode;
import nl.hauntedmc.serverfeatures.api.command.tab.internal.Node;
import nl.hauntedmc.serverfeatures.api.command.tab.provider.SuggestionProvider;
import nl.hauntedmc.serverfeatures.api.command.tab.sort.SuggestionSorter;
import nl.hauntedmc.serverfeatures.api.command.tab.sort.Sorters;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Immutable tab-completion tree. Build with {@link TabTree#builder()}.
 *
 * Rules:
 * - With 0 or 1 tokens: only root literals are suggested.
 * - With 2+ tokens: suggestions come from the nodes at the current frontier (matched path),
 *   i.e., the argument/literal node where the cursor currently is.
 * - Permissions and conditions are evaluated on every node before using them.
 */
public final class TabTree {
    private final List<Node> roots;
    private final SuggestionFilter filter;
    private final SuggestionSorter sorter;
    private final int maxResults;
    private final boolean showRootArguments;

    private TabTree(
            List<Node> roots,
            SuggestionFilter filter,
            SuggestionSorter sorter,
            int maxResults,
            boolean showRootArguments
    ) {
        this.roots = roots;
        this.filter = filter;
        this.sorter = sorter;
        this.maxResults = maxResults;
        this.showRootArguments = showRootArguments;
    }

    public static Builder builder() { return new Builder(); }

    public @NotNull List<String> complete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        TabContext ctx = new TabContext(sender, alias, args);
        final String token = args.length == 0 ? "" : args[args.length - 1];
        final String[] prev = args.length <= 1 ? new String[0] : Arrays.copyOf(args, args.length - 1);

        // Start at roots and consume all previous tokens to locate the current frontier.
        List<Node> frontier = new ArrayList<>(roots);
        for (String t : prev) {
            frontier = descend(frontier, ctx, t);
            if (frontier.isEmpty()) break;
        }

        LinkedHashSet<String> raw = new LinkedHashSet<>();

        if (args.length <= 1) {
            // 0 or 1 tokens: only root literals (unless showRootArguments is on)
            for (Node r : roots) {
                if (!r.allowed(ctx)) continue;
                if (r instanceof LiteralNode) {
                    raw.addAll(r.candidatesFor(ctx, token));
                } else if (showRootArguments && r instanceof ArgumentNode) {
                    raw.addAll(r.candidatesFor(ctx, token));
                }
            }
        } else {
            // 2+ tokens: suggest from the frontier nodes themselves (NOT their children)
            for (Node n : frontier) {
                if (!n.allowed(ctx)) continue;
                raw.addAll(n.candidatesFor(ctx, token));
            }
        }

        Collection<String> filtered = filter.filter(token, raw);
        List<String> sorted = sorter.sort(filtered);
        return sorted.size() > maxResults ? sorted.subList(0, maxResults) : sorted;
    }

    /**
     * Match the given token against the CURRENT nodes.
     * If a node is a Literal and equalsIgnoreCase(token) OR it's an ArgumentNode (always matches),
     * we accept it and descend to its children for the next step.
     */
    private List<Node> descend(List<Node> current, TabContext ctx, String token) {
        List<Node> next = new ArrayList<>();
        for (Node n : current) {
            if (!n.allowed(ctx)) continue;
            boolean matches = (n instanceof ArgumentNode) || n.matchesFully(token);
            if (matches) next.addAll(n.children);
        }
        return next;
    }

    /* ========================= BUILDER ========================= */

    public static final class Builder {
        private final List<Node> roots = new ArrayList<>();
        private SuggestionFilter filter = Filters.prefixCaseInsensitive();
        private SuggestionSorter sorter = Sorters.caseInsensitive();
        private int maxResults = 60;
        private boolean showRootArguments = false;

        public Builder filter(SuggestionFilter filter) {
            this.filter = Objects.requireNonNull(filter, "filter");
            return this;
        }
        public Builder sorter(SuggestionSorter sorter) {
            this.sorter = Objects.requireNonNull(sorter, "sorter");
            return this;
        }
        public Builder maxResults(int max) {
            this.maxResults = Math.max(1, max);
            return this;
        }
        /** If true, argument nodes at root are also suggested for the first token. Default: false. */
        public Builder showRootArguments(boolean show) {
            this.showRootArguments = show;
            return this;
        }

        /** Returns a root branch that can still call build() after chaining. */
        public RootBranch root() { return new RootBranch(this, roots, List.of()); }

        private TabTree buildInternal() {
            return new TabTree(List.copyOf(roots), filter, sorter, maxResults, showRootArguments);
        }

        /* -------- Root branch that PRESERVES type for build() ---------- */

        public static final class RootBranch extends Branch {
            private final Builder builder;

            private RootBranch(Builder builder, List<Node> attachTo, List<java.util.function.Consumer<Node>> decorators) {
                super(attachTo, decorators);
                this.builder = builder;
            }

            /** Build the TabTree from the current builder state. */
            public TabTree build() { return builder.buildInternal(); }

            /* Covariant overrides to KEEP RootBranch in the chain */
            @Override public RootBranch literal(String literal) {
                super.literal(literal);
                return this;
            }
            @Override public RootBranch arg(String name, SuggestionProvider provider) {
                super.arg(name, provider);
                return this;
            }
            @Override public RootBranch literals(String... literals) {
                super.literals(literals);
                return this;
            }
            @Override public RootBranch withRequire(String permission) {
                return new RootBranch(builder, attachTo, composeDecorators(d -> d.require(permission)));
            }
            @Override public RootBranch withRequireAny(String... permissions) {
                List<String> perms = Arrays.asList(permissions);
                return new RootBranch(builder, attachTo, composeDecorators(d -> d.requireAny(perms)));
            }
            @Override public RootBranch withDeny(String... permissions) {
                List<String> perms = Arrays.asList(permissions);
                return new RootBranch(builder, attachTo, composeDecorators(d -> d.denyAll(perms)));
            }
            @Override public RootBranch withWhen(Predicate<TabContext> cond) {
                return new RootBranch(builder, attachTo, composeDecorators(d -> d.when(cond)));
            }
            @Override public RootBranch requireForExisting(String permission) {
                super.requireForExisting(permission);
                return this;
            }
            @Override public RootBranch whenForExisting(Predicate<TabContext> cond) {
                super.whenForExisting(cond);
                return this;
            }
            @Override public RootBranch group(Builder.Group g) {
                super.group(g);
                return this;
            }

            /** Inline configure a literal and continue on RootBranch (covariant). */
            public RootBranch literal(String literal, java.util.function.Consumer<NodeHandle> cfg) {
                NodeHandle h = literalInternal(literal, factoryForRoot());
                if (cfg != null) cfg.accept(h);
                return (RootBranch) h.end(); // returns RootBranch because factoryForRoot()
            }
            /** Inline configure an arg and continue on RootBranch (covariant). */
            public RootBranch arg(String name, SuggestionProvider provider, java.util.function.Consumer<NodeHandle> cfg) {
                NodeHandle h = argInternal(name, provider, factoryForRoot());
                if (cfg != null) cfg.accept(h);
                return (RootBranch) h.end();
            }

            private BiFunction<List<Node>, List<java.util.function.Consumer<Node>>, Branch> factoryForRoot() {
                return (list, decs) -> new RootBranch(builder, list, decs);
            }
        }

        /** Reusable group applied to a branch. */
        public static final class Group {
            private final List<java.util.function.Consumer<Branch>> steps = new ArrayList<>();
            private Group() {}
            public static Group define() { return new Group(); }
            public Group literal(String literal) { steps.add(b -> b.literal(literal)); return this; }
            public Group arg(String name, SuggestionProvider provider) { steps.add(b -> b.arg(name, provider)); return this; }
            public Group require(String permission) { steps.add(b -> b.requireForExisting(permission)); return this; }
            public Group when(Predicate<TabContext> cond) { steps.add(b -> b.whenForExisting(cond)); return this; }
            void applyTo(Branch b) { steps.forEach(s -> s.accept(b)); }
        }

        /** Node handle with .end() returning the correct branch type via a factory. */
        public static final class NodeHandle {
            private final Node node;
            private final List<Node> parentList;
            private final List<java.util.function.Consumer<Node>> parentDecorators;
            private final BiFunction<List<Node>, List<java.util.function.Consumer<Node>>, Branch> branchFactory;

            private NodeHandle(Node node,
                               List<Node> parentList,
                               List<java.util.function.Consumer<Node>> parentDecorators,
                               BiFunction<List<Node>, List<java.util.function.Consumer<Node>>, Branch> branchFactory) {
                this.node = node;
                this.parentList = parentList;
                this.parentDecorators = parentDecorators;
                this.branchFactory = branchFactory;
            }

            public NodeHandle require(String permission) { node.require(permission); return this; }
            public NodeHandle requireAll(Collection<String> perms) { node.requireAll(perms); return this; }
            public NodeHandle requireAny(String... perms) { node.requireAny(Arrays.asList(perms)); return this; }
            public NodeHandle deny(String... perms) { node.denyAll(Arrays.asList(perms)); return this; }
            public NodeHandle when(Predicate<TabContext> cond) { node.when(cond); return this; }

            /** Build children under this node. You can keep chaining on this NodeHandle afterwards. */
            public NodeHandle child(java.util.function.Consumer<Branch> builder) {
                Branch b = new Branch(node.children, List.of());
                builder.accept(b);
                return this;
            }

            /** Return to the parent branch (type preserved by the factory). */
            public Branch end() { return branchFactory.apply(parentList, parentDecorators); }
        }

        /** Branch that accumulates nodes and decorators. */
        public static class Branch {
            protected final List<Node> attachTo;
            protected final List<java.util.function.Consumer<Node>> decorators;

            protected Branch(List<Node> attachTo, List<java.util.function.Consumer<Node>> decorators) {
                this.attachTo = attachTo;
                this.decorators = decorators;
            }

            /** Short form: add literal and keep chaining on the same Branch. */
            public Branch literal(String literal) {
                NodeHandle h = literalInternal(Objects.requireNonNull(literal, "literal"), Branch::new);
                return h.end();
            }
            /** Short form: add arg and keep chaining on the same Branch. */
            public Branch arg(String name, SuggestionProvider provider) {
                NodeHandle h = argInternal(Objects.requireNonNull(name, "name"), Objects.requireNonNull(provider, "provider"), Branch::new);
                return h.end();
            }

            /** Inline configure a literal and continue on the branch. */
            public Branch literal(String literal, java.util.function.Consumer<NodeHandle> cfg) {
                NodeHandle h = literalInternal(Objects.requireNonNull(literal, "literal"), Branch::new);
                if (cfg != null) cfg.accept(h);
                return h.end();
            }
            /** Inline configure an arg and continue on the branch. */
            public Branch arg(String name, SuggestionProvider provider, java.util.function.Consumer<NodeHandle> cfg) {
                NodeHandle h = argInternal(Objects.requireNonNull(name, "name"), Objects.requireNonNull(provider, "provider"), Branch::new);
                if (cfg != null) cfg.accept(h);
                return h.end();
            }

            public Branch literals(String... literals) {
                for (String lit : literals) {
                    LiteralNode n = new LiteralNode(Objects.requireNonNull(lit, "literal"));
                    applyDecorators(n);
                    attachTo.add(n);
                }
                return this;
            }

            public Branch withRequire(String permission) {
                return new Branch(attachTo, composeDecorators(d -> d.require(permission)));
            }
            public Branch withRequireAny(String... permissions) {
                List<String> perms = Arrays.asList(permissions);
                return new Branch(attachTo, composeDecorators(d -> d.requireAny(perms)));
            }
            public Branch withDeny(String... permissions) {
                List<String> perms = Arrays.asList(permissions);
                return new Branch(attachTo, composeDecorators(d -> d.denyAll(perms)));
            }
            public Branch withWhen(Predicate<TabContext> cond) {
                return new Branch(attachTo, composeDecorators(d -> d.when(cond)));
            }

            public Branch requireForExisting(String permission) {
                for (Node n : attachTo) n.require(permission);
                return this;
            }
            public Branch whenForExisting(Predicate<TabContext> cond) {
                for (Node n : attachTo) n.when(cond);
                return this;
            }

            public Branch group(Builder.Group g) { g.applyTo(this); return this; }

            protected void applyDecorators(Node n) {
                for (var d : decorators) d.accept(n);
            }
            protected List<java.util.function.Consumer<Node>> composeDecorators(java.util.function.Consumer<Node> extra) {
                List<java.util.function.Consumer<Node>> next = new ArrayList<>(decorators);
                next.add(extra);
                return next;
            }

            protected NodeHandle literalInternal(String literal,
                                                 BiFunction<List<Node>, List<java.util.function.Consumer<Node>>, Branch> factory) {
                LiteralNode n = new LiteralNode(literal);
                applyDecorators(n);
                attachTo.add(n);
                return new NodeHandle(n, attachTo, decorators, factory);
            }

            protected NodeHandle argInternal(String name, SuggestionProvider provider,
                                             BiFunction<List<Node>, List<java.util.function.Consumer<Node>>, Branch> factory) {
                ArgumentNode n = new ArgumentNode(name, provider);
                applyDecorators(n);
                attachTo.add(n);
                return new NodeHandle(n, attachTo, decorators, factory);
            }
        }
    }
}
