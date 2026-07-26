package nl.hauntedmc.serverfeatures.features.afk.command;

import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.api.command.meta.CommandMeta;
import nl.hauntedmc.serverfeatures.features.afk.AFK;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AfkCommand extends FeatureCommand {

    private final AFK feature;

    public AfkCommand(AFK feature) {
        super(new CommandMeta.Builder("afk").build());
        this.feature = feature;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender,
                           @NotNull String label,
                           String @NotNull [] args) {
        if (!(sender instanceof Player p)) {
            usage(sender);
            return true;
        }
        if (!sender.hasPermission("serverfeatures.feature.afk.command.afk.toggle")) {
            noPerm(sender);
            return true;
        }
        boolean turnOn = !feature.getService().isAfk(p.getUniqueId());
        feature.getService().setAfk(p, turnOn);
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String @NotNull [] args) {
        return List.of();
    }

    private void usage(CommandSender s) {
        s.sendMessage(feature.getLocalizationHandler().getMessage("afk.usage").forAudience(s).build());
    }

    private void noPerm(CommandSender s) {
        s.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission").forAudience(s).build());
    }
}
