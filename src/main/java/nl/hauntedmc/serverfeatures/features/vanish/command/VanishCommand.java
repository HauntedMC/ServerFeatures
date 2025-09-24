package nl.hauntedmc.serverfeatures.features.vanish.command;

import nl.hauntedmc.serverfeatures.commands.CommandSpec;
import nl.hauntedmc.serverfeatures.commands.FeatureCommand;
import nl.hauntedmc.serverfeatures.features.vanish.Vanish;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VanishCommand extends FeatureCommand {

    private final Vanish feature;

    public VanishCommand(Vanish feature) {
        super(new CommandSpec.Builder("vanish").build());
        this.feature = feature;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender,
                           @NotNull String label,
                           String @NotNull [] args) {

        // /vanish           -> toggle self
        // /vanish on|off    -> set self
        // /vanish <p> [on|off] -> set other (admin)

        if (args.length == 0) {
            if (!(sender instanceof Player self)) { usage(sender); return true; }
            if (!sender.hasPermission("serverfeatures.feature.vanish.command.vanish.toggle")) {
                noPerm(sender); return true;
            }
            boolean desired = !feature.getService().isPlayerVanished(self);
            feature.getService().setVanished(self, desired);

            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage(desired ? "vanish.enabled_self" : "vanish.disabled_self")
                    .forAudience(sender)
                    .build());
            feature.getService().notifyStaffToggle(self, self, desired);
            return true;
        }

        // Self on/off
        if (args.length == 1 && (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off"))) {
            if (!(sender instanceof Player self)) { usage(sender); return true; }
            if (!sender.hasPermission("serverfeatures.feature.vanish.command.vanish.toggle")) {
                noPerm(sender); return true;
            }
            boolean desired = args[0].equalsIgnoreCase("on");
            boolean current = feature.getService().isPlayerVanished(self);
            if (current == desired) {
                alreadyState(sender); return true;
            }
            feature.getService().setVanished(self, desired);

            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage(desired ? "vanish.enabled_self" : "vanish.disabled_self")
                    .forAudience(sender)
                    .build());

            feature.getService().notifyStaffToggle(self, self, desired);
            return true;
        }

        // Others
        if (!sender.hasPermission("serverfeatures.feature.vanish.command.vanish.others")) {
            noPerm(sender); return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("vanish.not_online")
                    .forAudience(sender)
                    .build());
            return true;
        }
        boolean desired;
        if (args.length >= 2 && (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) {
            desired = args[1].equalsIgnoreCase("on");
        } else {
            desired = !feature.getService().isPlayerVanished(target);
        }
        boolean current = feature.getService().isPlayerVanished(target);
        if (current == desired) {
            alreadyState(sender); return true;
        }

        feature.getService().setVanished(target, desired);

        // Informeer de target zelf (alleen als online)
        target.sendMessage(feature.getLocalizationHandler()
                .getMessage(desired ? "vanish.target_enabled_by_other" : "vanish.target_disabled_by_other")
                .withPlaceholders(Map.of("actor", sender.getName()))
                .forAudience(target)
                .build());

        sender.sendMessage(feature.getLocalizationHandler()
                .getMessage(desired ? "vanish.enabled_other" : "vanish.disabled_other")
                .withPlaceholders(Map.of("target", target.getName()))
                .forAudience(sender)
                .build());

        feature.getService().notifyStaffToggle(sender instanceof Player p ? p : null, target, desired);
        return true;
    }

    private void usage(CommandSender s) {
        s.sendMessage(feature.getLocalizationHandler()
                .getMessage("vanish.usage")
                .forAudience(s)
                .build());
    }
    private void noPerm(CommandSender s) {
        s.sendMessage(feature.getLocalizationHandler()
                .getMessage("general.no_permission")
                .forAudience(s)
                .build());
    }
    private void alreadyState(CommandSender s) {
        s.sendMessage(feature.getLocalizationHandler()
                .getMessage("vanish.already_state")
                .forAudience(s)
                .build());
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender,
                                             @NotNull String alias,
                                             String @NotNull [] args) {
        if (args.length == 1) {
            return Stream.concat(Stream.of("on", "off"),
                            Bukkit.getOnlinePlayers().stream().map(Player::getName))
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return Stream.of("on", "off")
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
