package nl.hauntedmc.serverfeatures.features.nickname.command;

import nl.hauntedmc.serverfeatures.commands.FeatureCommand;
import nl.hauntedmc.serverfeatures.features.nickname.Nickname;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NickCommand extends FeatureCommand {

    private final Nickname feature;

    public NickCommand(Nickname feature) {
        super("nickname");
        this.feature = feature;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command", sender));
            return true;
        }

        if (!player.hasPermission("serverfeatures.feature.nickname.command.nickname")) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission", player));
            return false;
        }

        if (args.length == 0) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("general.usage", player));
            return true;
        }

        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("remove")) {
                feature.getNicknameHandler().removeNickname(player);
                player.sendMessage(feature.getLocalizationHandler().getMessage("nickname.removed", player));
            } else {
                boolean succes = feature.getNicknameHandler().setNickname(player, args[0]);

                if (succes) {
                    player.sendMessage(feature.getLocalizationHandler().getMessage("nickname.set", player, Map.of("nickname", args[0])));
                }
            }
            return true;
        }

        if (args.length == 2 && player.hasPermission("serverfeatures.feature.nickname.command.nickname_other")) {
            Player target = feature.getPlugin().getServer().getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(feature.getLocalizationHandler().getMessage("nickname.player_not_found", player));
                return true;
            }
            if (args[1].equalsIgnoreCase("remove")) {
                feature.getNicknameHandler().removeNickname(player);
                target.sendMessage(feature.getLocalizationHandler().getMessage("nickname.removed", player));
                sender.sendMessage(feature.getLocalizationHandler().getMessage("nickname.other_removed", player, Map.of("player", target.getName())));
            } else {
                boolean succes = feature.getNicknameHandler().setNickname(target, args[1]);

                if (succes) {
                    player.sendMessage(feature.getLocalizationHandler().getMessage("nickname.set", player, Map.of("nickname", args[1])));
                    player.sendMessage(feature.getLocalizationHandler().getMessage("nickname.set_other", player, Map.of("player", target.getName(), "nickname", args[1])));
                }
            }
        }

        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String @NotNull[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(player.getName());
                }
            }
        } else {
            if ("remove".startsWith(args[0].toLowerCase())) {
                completions.add("remove");
            }
        }


        return completions;
    }
}
