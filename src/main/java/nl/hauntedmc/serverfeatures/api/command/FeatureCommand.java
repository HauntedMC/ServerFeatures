package nl.hauntedmc.serverfeatures.api.command;

import nl.hauntedmc.serverfeatures.api.command.meta.CommandMeta;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

public abstract class FeatureCommand extends Command {

    private static final Pattern ALIAS_ALLOWED = Pattern.compile("^[a-z0-9_\\-]+$");

    public FeatureCommand(@NotNull CommandMeta spec) {
        super(
                spec.name(),
                spec.description() == null ? "" : spec.description(),
                spec.usage() == null ? "" : spec.usage(),
                sanitizeAliases(spec.name(), spec.aliases() == null ? Collections.emptyList() : spec.aliases())
        );

        if (spec.permission() != null) {
            setPermission(spec.permission());
        }
    }

    private static @NotNull List<String> sanitizeAliases(@NotNull String name, @Nullable List<String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();

        String primary = name.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> out = new LinkedHashSet<>();

        for (String alias : raw) {
            if (alias == null) continue;
            String t = alias.trim().toLowerCase(Locale.ROOT);
            if (t.isEmpty()) continue;
            if (t.equals(primary)) continue;
            if (t.contains(" ")) {
                throw new IllegalArgumentException("Alias must not contain spaces: '" + alias + "'");
            }
            if (!ALIAS_ALLOWED.matcher(t).matches()) {
                throw new IllegalArgumentException(
                        "Alias contains invalid characters (allowed: a-z, 0-9, '_', '-'): '" + alias + "'"
                );
            }
            out.add(t);
        }
        return List.copyOf(out);
    }

    /**
     * Finalize registration: keep this final so validation always runs.
     */
    @Override
    public final boolean register(@NotNull CommandMap commandMap) {
        return super.register(commandMap);
    }

    public abstract boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args);

    public abstract @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String @NotNull [] args);
}
