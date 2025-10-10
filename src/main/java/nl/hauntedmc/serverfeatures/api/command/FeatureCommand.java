package nl.hauntedmc.serverfeatures.api.command;

import nl.hauntedmc.serverfeatures.api.command.meta.CommandMeta;
import nl.hauntedmc.serverfeatures.api.command.tab.TabService;
import nl.hauntedmc.serverfeatures.api.command.tab.TabTree;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

public abstract class FeatureCommand extends Command {

    private @Nullable TabTree tabTree;
    private @Nullable TabService tabService;

    protected FeatureCommand(@NotNull CommandMeta spec) {
        super(
                spec.name(),
                spec.description() == null ? "" : spec.description(),
                spec.usage() == null ? "" : spec.usage(),
                sanitizeAliases(spec.name(), spec.aliases() == null ? Collections.emptyList() : spec.aliases())
        );
        if (spec.permission() != null) setPermission(spec.permission());
    }

    public abstract boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args);


    @Override
    public final boolean register(@NotNull CommandMap commandMap) {
        return super.register(commandMap);
    }

    /**
     * Override to provide a TabTree for this command.
     * Return null if this command has no tab-completions.
     */
    public @Nullable TabTree createTabTree() {
        return tabTree;
    }

    /** Register this command's labels (primary + aliases) in the global TabService. */
    public final void registerTabTree(@NotNull TabService service, @NotNull TabTree tree) {
        this.tabService = Objects.requireNonNull(service, "service");
        this.tabTree = Objects.requireNonNull(tree, "tree");

        final String primary = getName().toLowerCase(Locale.ROOT);
        service.register(primary, tree);

        List<String> aliases = getAliases();
        for (String a : aliases) {
            if (a == null || a.isBlank()) continue;
            service.register(a.toLowerCase(Locale.ROOT), tree);
        }
    }

    /** Unregister all labels of this command from the global TabService. Safe on disable. */
    public final void unregisterTabTree() {
        if (tabService == null) return;
        final String primary = getName().toLowerCase(Locale.ROOT);
        tabService.unregister(primary);
        List<String> aliases = getAliases();
        for (String a : aliases) {
            if (a == null || a.isBlank()) continue;
            tabService.unregister(a.toLowerCase(Locale.ROOT));
        }
        this.tabTree = null;
        this.tabService = null;
    }

    /** Bukkit will not be used for completions; global listener handles everything. */
    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String @NotNull [] args) {
        return Collections.emptyList();
    }

    private static @NotNull List<String> sanitizeAliases(@NotNull String name, @Nullable List<String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        String primary = name.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Pattern ALIAS_ALLOWED = Pattern.compile("^[a-z0-9_\\-]+$");
        for (String alias : raw) {
            if (alias == null) continue;
            String t = alias.trim().toLowerCase(Locale.ROOT);
            if (t.isEmpty()) continue;
            if (t.equals(primary)) continue;
            if (t.contains(" ")) throw new IllegalArgumentException("Alias must not contain spaces: '" + alias + "'");
            if (!ALIAS_ALLOWED.matcher(t).matches()) {
                throw new IllegalArgumentException("Alias contains invalid characters (allowed: a-z, 0-9, '_', '-'): '" + alias + "'");
            }
            out.add(t);
        }
        return List.copyOf(out);
    }
}
