package nl.hauntedmc.serverfeatures.api.command.tab;

import nl.hauntedmc.serverfeatures.api.command.tab.filter.SuggestionFilter;
import nl.hauntedmc.serverfeatures.api.command.tab.filter.Filters;
import nl.hauntedmc.serverfeatures.api.command.tab.internal.*;
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
 * Semantics:
 * - With 0 or 1 tokens: only root literals are suggested (unless showRootArguments is true).
 * - With 2+ tokens: suggestions come from the current frontier nodes (matched path).
 * - Permissions and conditions are enforced on every node before they participate.
 * - Repeatable arguments can consume multiple tokens before moving on.
 *
 * IMPORTANT:
 * - Root-level builder produces SIBLINGS by default (multiple literals -> alternatives).
 * - Inside a .child(...) block, the builder is SEQUENTIAL by default:
 *     multiple .arg()/literal() calls represent ordered positions (arg1 -> arg2 -> arg3).
 *   Use .alt(...) inside child to create same-position alternatives if needed.
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

        // Consume prev tokens to locate current frontier
        List<Node> frontier = new ArrayList<>(roots);
        for (String t : prev) {
            frontier = descend(frontier, ctx, t);
            if (frontier.isEmpty()) break;
        }

        LinkedHashSet<String> raw = new LinkedHashSet<>();

        if (args.length <= 1) {
            // Root suggestions
            for (Node r : roots) {
                if (!r.allowed(ctx)) continue;
                if (r instanceof LiteralNode) {
                    raw.addAll(r.candidatesFor(ctx, token));
                } else if (showRootArguments && r instanceof ArgumentNodeBase) {
                    raw.addAll(r.candidatesFor(ctx, token));
                }
            }
        } else {
            // Suggest from frontier nodes themselves
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
     * Match token against CURRENT nodes. If a node matches, we descend.
     * Repeatable args remain in the frontier for additional tokens and also open their children.
     */
    private List<Node> descend(List<Node> current, TabContext ctx, String token) {
        List<Node> next = new ArrayList<>();
        for (Node n : current) {
            if (!n.allowed(ctx)) continue;

            boolean matches = n.matchesFully(token);
            if (!matches) continue;

            if (n instanceof RepeatableArgumentNode) {
                // Stay to allow repetition
                next.add(n);
            }
            // Advance as well
            next.addAll(n.children);
        }
        return next;
    }

    /* ========================= BUILDER ========================= */

    public static final class Builder {
        private final List<Node> roots = new ArrayList<>();
        private SuggestionFilter filter = Filters.prefixCaseInsensitive();
        private SuggestionSorter sorter = Sorters.caseInsensitive();
        private int maxResults = Integer.MAX_VALUE;
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
        /** If true, argument nodes at root are also suggested for the first token. Default false. */
        public Builder showRootArguments(boolean show) {
            this.showRootArguments = show;
            return this;
        }

        /** Returns a root branch that can still call build() after chaining. */
        public RootBranch root() { return new RootBranch(this, roots, List.of()); }

        private TabTree buildInternal() {
            return new TabTree(List.copyOf(roots), filter, sorter, maxResults, showRootArguments);
        }

        /* -------- Root branch preserving type for build() ---------- */

        public static final class RootBranch extends Branch {
            private final Builder builder;

            private RootBranch(Builder builder, List<Node> attachTo, List<java.util.function.Consumer<Node>> decorators) {
                super(attachTo, decorators);
                this.builder = builder;
            }

            /** Build the TabTree from the current builder state. */
            public TabTree build() { return builder.buildInternal(); }

            /* Covariant overrides so chaining stays on RootBranch (siblings semantics) */
            @Override public RootBranch literal(String literal) { super.literal(literal); return this; }
            @Override public RootBranch literalWithAliases(String primary, String... aliases) { super.literalWithAliases(primary, aliases); return this; }
            @Override public RootBranch arg(String name, SuggestionProvider provider) { super.arg(name, provider); return this; }
            @Override public RootBranch argRepeatable(String name, SuggestionProvider provider) { super.argRepeatable(name, provider); return this; }
            @Override public RootBranch argMatching(String name, java.util.function.Predicate<String> matcher, SuggestionProvider provider) { super.argMatching(name, matcher, provider); return this; }
            @Override public RootBranch literals(String... literals) { super.literals(literals); return this; }

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
            @Override public RootBranch requireForExisting(String permission) { super.requireForExisting(permission); return this; }
            @Override public RootBranch whenForExisting(Predicate<TabContext> cond) { super.whenForExisting(cond); return this; }
            @Override public RootBranch group(Builder.Group g) { super.group(g); return this; }

            /** Inline configure a literal and continue (covariant). */
            public RootBranch literal(String literal, java.util.function.Consumer<NodeHandle> cfg) {
                LiteralNode n = new LiteralNode(Objects.requireNonNull(literal, "literal"));
                applyDecorators(n);
                attachTo.add(n);
                NodeHandle h = new NodeHandle(n, attachTo, decorators, factoryForRoot());
                if (cfg != null) cfg.accept(h);
                return (RootBranch) h.end();
            }
            /** Inline configure an arg and continue (covariant). */
            public RootBranch arg(String name, SuggestionProvider provider, java.util.function.Consumer<NodeHandle> cfg) {
                ArgumentNode n = new ArgumentNode(Objects.requireNonNull(name, "name"), Objects.requireNonNull(provider, "provider"));
                applyDecorators(n);
                attachTo.add(n);
                NodeHandle h = new NodeHandle(n, attachTo, decorators, factoryForRoot());
                if (cfg != null) cfg.accept(h);
                return (RootBranch) h.end();
            }
            /** Inline configure repeatable arg and continue (covariant). */
            public RootBranch argRepeatable(String name, SuggestionProvider provider, java.util.function.Consumer<NodeHandle> cfg) {
                RepeatableArgumentNode n = new RepeatableArgumentNode(Objects.requireNonNull(name, "name"), Objects.requireNonNull(provider, "provider"));
                applyDecorators(n);
                attachTo.add(n);
                NodeHandle h = new NodeHandle(n, attachTo, decorators, factoryForRoot());
                if (cfg != null) cfg.accept(h);
                return (RootBranch) h.end();
            }
            /** Inline configure matching arg and continue (covariant). */
            public RootBranch argMatching(String name, java.util.function.Predicate<String> matcher, SuggestionProvider provider, java.util.function.Consumer<NodeHandle> cfg) {
                PatternArgumentNode n = new PatternArgumentNode(Objects.requireNonNull(name, "name"),
                        Objects.requireNonNull(matcher, "matcher"),
                        Objects.requireNonNull(provider, "provider"));
                applyDecorators(n);
                attachTo.add(n);
                NodeHandle h = new NodeHandle(n, attachTo, decorators, factoryForRoot());
                if (cfg != null) cfg.accept(h);
                return (RootBranch) h.end();
            }

            private BiFunction<List<Node>, List<java.util.function.Consumer<Node>>, Branch> factoryForRoot() {
                return (list, decs) -> new RootBranch(builder, list, decs);
            }
        }

        /** Reusable group applied to a branch.  NOTE: In a child block (sequential branch),
         *  applying a group is sequential by default (id -> mode -> ...). */
        public static final class Group {
            private final List<java.util.function.Consumer<Branch>> steps = new ArrayList<>();
            private Group() {}
            public static Group define() { return new Group(); }
            public Group literal(String literal) { steps.add(b -> b.literal(literal)); return this; }
            public Group literalWithAliases(String primary, String... aliases) { steps.add(b -> b.literalWithAliases(primary, aliases)); return this; }
            public Group arg(String name, SuggestionProvider provider) { steps.add(b -> b.arg(name, provider)); return this; }
            public Group argRepeatable(String name, SuggestionProvider provider) { steps.add(b -> b.argRepeatable(name, provider)); return this; }
            public Group argMatching(String name, java.util.function.Predicate<String> matcher, SuggestionProvider provider) { steps.add(b -> b.argMatching(name, matcher, provider)); return this; }
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

            /** Build children under this node (SEQUENTIAL by default). */
            public NodeHandle child(java.util.function.Consumer<Branch> builder) {
                Branch b = new SequentialBranch(node.children, List.of());
                builder.accept(b);
                return this;
            }

            /** Return to the parent branch (type preserved by the factory). */
            public Branch end() { return branchFactory.apply(parentList, parentDecorators); }
        }

        /** Base branch: SIBLING semantics (used at root). */
        public static class Branch {
            protected List<Node> attachTo;
            protected final List<java.util.function.Consumer<Node>> decorators;

            protected Branch(List<Node> attachTo, List<java.util.function.Consumer<Node>> decorators) {
                this.attachTo = attachTo;
                this.decorators = decorators;
            }

            /** Short form: add literal and keep chaining on the same branch (sibling). */
            public Branch literal(String literal) {
                LiteralNode n = new LiteralNode(Objects.requireNonNull(literal, "literal"));
                applyDecorators(n);
                attachTo.add(n);
                return this;
            }
            /** Convenience: primary literal + aliases as separate literals. */
            public Branch literalWithAliases(String primary, String... aliases) {
                literal(primary);
                if (aliases != null) for (String a : aliases) literal(a);
                return this;
            }
            /** Short form: add generic arg (accepts any token) as sibling. */
            public Branch arg(String name, SuggestionProvider provider) {
                ArgumentNode n = new ArgumentNode(Objects.requireNonNull(name, "name"), Objects.requireNonNull(provider, "provider"));
                applyDecorators(n);
                attachTo.add(n);
                return this;
            }
            /** Short form: add repeatable arg as sibling. */
            public Branch argRepeatable(String name, SuggestionProvider provider) {
                RepeatableArgumentNode n = new RepeatableArgumentNode(Objects.requireNonNull(name, "name"), Objects.requireNonNull(provider, "provider"));
                applyDecorators(n);
                attachTo.add(n);
                return this;
            }
            /** Short form: add pattern-matching arg as sibling. */
            public Branch argMatching(String name, java.util.function.Predicate<String> matcher, SuggestionProvider provider) {
                PatternArgumentNode n = new PatternArgumentNode(Objects.requireNonNull(name, "name"),
                        Objects.requireNonNull(matcher, "matcher"),
                        Objects.requireNonNull(provider, "provider"));
                applyDecorators(n);
                attachTo.add(n);
                return this;
            }

            /** Inline configure a literal and continue. */
            public Branch literal(String literal, java.util.function.Consumer<NodeHandle> cfg) {
                LiteralNode n = new LiteralNode(Objects.requireNonNull(literal, "literal"));
                applyDecorators(n);
                attachTo.add(n);
                NodeHandle h = new NodeHandle(n, attachTo, decorators, Branch::new);
                if (cfg != null) cfg.accept(h);
                return this;
            }
            /** Inline configure a generic arg and continue. */
            public Branch arg(String name, SuggestionProvider provider, java.util.function.Consumer<NodeHandle> cfg) {
                ArgumentNode n = new ArgumentNode(Objects.requireNonNull(name, "name"), Objects.requireNonNull(provider, "provider"));
                applyDecorators(n);
                attachTo.add(n);
                NodeHandle h = new NodeHandle(n, attachTo, decorators, Branch::new);
                if (cfg != null) cfg.accept(h);
                return this;
            }
            /** Inline configure a repeatable arg and continue. */
            public Branch argRepeatable(String name, SuggestionProvider provider, java.util.function.Consumer<NodeHandle> cfg) {
                RepeatableArgumentNode n = new RepeatableArgumentNode(Objects.requireNonNull(name, "name"), Objects.requireNonNull(provider, "provider"));
                applyDecorators(n);
                attachTo.add(n);
                NodeHandle h = new NodeHandle(n, attachTo, decorators, Branch::new);
                if (cfg != null) cfg.accept(h);
                return this;
            }
            /** Inline configure a matching arg and continue. */
            public Branch argMatching(String name, java.util.function.Predicate<String> matcher, SuggestionProvider provider, java.util.function.Consumer<NodeHandle> cfg) {
                PatternArgumentNode n = new PatternArgumentNode(Objects.requireNonNull(name, "name"),
                        Objects.requireNonNull(matcher, "matcher"),
                        Objects.requireNonNull(provider, "provider"));
                applyDecorators(n);
                attachTo.add(n);
                NodeHandle h = new NodeHandle(n, attachTo, decorators, Branch::new);
                if (cfg != null) cfg.accept(h);
                return this;
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
        }

        /**
         * SequentialBranch (used inside NodeHandle.child):
         * - Each added literal/arg becomes the next POSITION in the command (nested chain).
         * - Use alt(...) to add alternatives at the current position (siblings).
         */
        public static final class SequentialBranch extends Branch {
            public SequentialBranch(List<Node> attachTo, List<java.util.function.Consumer<Node>> decorators) {
                super(attachTo, decorators);
            }

            /** Add a same-position alternative block (sibling semantics) without moving the cursor. */
            public SequentialBranch alt(java.util.function.Consumer<Branch> alternatives) {
                // Alternatives share the same attachTo list (siblings at this position)
                Branch sibling = new Branch(this.attachTo, this.decorators);
                alternatives.accept(sibling);
                return this;
            }

            @Override public SequentialBranch literal(String literal) {
                LiteralNode n = new LiteralNode(Objects.requireNonNull(literal, "literal"));
                applyDecorators(n);
                attachTo.add(n);
                // Move cursor to children (next position)
                this.attachTo = n.children;
                return this;
            }

            @Override public SequentialBranch literalWithAliases(String primary, String... aliases) {
                this.literal(primary);
                if (aliases != null && aliases.length > 0) {
                    // For true same-position aliases, prefer explicit alt(...)
                    for (String a : aliases) this.literal(a);
                }
                return this;
            }

            @Override public SequentialBranch arg(String name, SuggestionProvider provider) {
                ArgumentNode n = new ArgumentNode(Objects.requireNonNull(name, "name"), Objects.requireNonNull(provider, "provider"));
                applyDecorators(n);
                attachTo.add(n);
                this.attachTo = n.children;
                return this;
            }

            @Override public SequentialBranch argRepeatable(String name, SuggestionProvider provider) {
                RepeatableArgumentNode n = new RepeatableArgumentNode(Objects.requireNonNull(name, "name"), Objects.requireNonNull(provider, "provider"));
                applyDecorators(n);
                attachTo.add(n);
                this.attachTo = n.children;
                return this;
            }

            @Override public SequentialBranch argMatching(String name, java.util.function.Predicate<String> matcher, SuggestionProvider provider) {
                PatternArgumentNode n = new PatternArgumentNode(Objects.requireNonNull(name, "name"),
                        Objects.requireNonNull(matcher, "matcher"),
                        Objects.requireNonNull(provider, "provider"));
                applyDecorators(n);
                attachTo.add(n);
                this.attachTo = n.children;
                return this;
            }

            @Override public SequentialBranch literals(String... literals) {
                for (String lit : literals) this.literal(lit);
                return this;
            }

            @Override public SequentialBranch withRequire(String permission) {
                return new SequentialBranch(attachTo, composeDecorators(d -> d.require(permission)));
            }
            @Override public SequentialBranch withRequireAny(String... permissions) {
                List<String> perms = Arrays.asList(permissions);
                return new SequentialBranch(attachTo, composeDecorators(d -> d.requireAny(perms)));
            }
            @Override public SequentialBranch withDeny(String... permissions) {
                List<String> perms = Arrays.asList(permissions);
                return new SequentialBranch(attachTo, composeDecorators(d -> d.denyAll(perms)));
            }
            @Override public SequentialBranch withWhen(Predicate<TabContext> cond) {
                return new SequentialBranch(attachTo, composeDecorators(d -> d.when(cond)));
            }
            @Override public SequentialBranch requireForExisting(String permission) {
                super.requireForExisting(permission);
                return this;
            }
            @Override public SequentialBranch whenForExisting(Predicate<TabContext> cond) {
                super.whenForExisting(cond);
                return this;
            }
            @Override public SequentialBranch group(Builder.Group g) { g.applyTo(this); return this; }
        }
    }
}
