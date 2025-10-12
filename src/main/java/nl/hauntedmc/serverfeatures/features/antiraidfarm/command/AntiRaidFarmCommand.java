package nl.hauntedmc.serverfeatures.features.antiraidfarm.command;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.command.meta.CommandMeta;
import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.features.antiraidfarm.AntiRaidFarm;
import nl.hauntedmc.serverfeatures.features.antiraidfarm.internal.AntiRaidFarmHandler;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class AntiRaidFarmCommand extends FeatureCommand {

    private static final String ADMIN_PERM = "serverfeatures.feature.antiraidfarm.command.admin";

    private final AntiRaidFarm feature;
    private final AntiRaidFarmHandler handler;

    public AntiRaidFarmCommand(AntiRaidFarm feature, AntiRaidFarmHandler handler) {
        super(new CommandMeta.Builder("antiraidfarm")
                .permission(ADMIN_PERM)
                .build());
        this.feature = feature;
        this.handler = handler;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission(ADMIN_PERM)) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission").forAudience(sender).build());
            return true;
        }

        // Usage: /antiraidfarm [list]
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            sendList(sender);
            return true;
        }

        sendUsage(sender);
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("/antiraidfarm list");
    }

    private void sendList(CommandSender sender) {
        var entries = handler.listActiveCooldowns();
        var lh = feature.getLocalizationHandler();

        if (entries.isEmpty()) {
            sender.sendMessage(lh.getMessage("antiraidfarm.list.none").forAudience(sender).build());
            return;
        }

        sender.sendMessage(lh.getMessage("antiraidfarm.list.header")
                .with("count", entries.size())
                .forAudience(sender)
                .build());

        for (var e : entries) {
            sender.sendMessage(lh.getMessage("antiraidfarm.list.entry")
                    .forAudience(sender)
                    .with("player", e.name())
                    .with("remaining", e.remainingSeconds())
                    .with("total", e.totalSeconds())
                    .build());
        }
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String @NotNull [] args) {
        if (!sender.hasPermission(ADMIN_PERM)) return List.of();
        if (args.length == 1) return Stream.of("list")
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .toList();
        return List.of();
    }
}
