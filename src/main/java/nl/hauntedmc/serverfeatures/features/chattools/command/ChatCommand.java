package nl.hauntedmc.serverfeatures.features.chattools.command;

import nl.hauntedmc.serverfeatures.commands.FeatureCommand;
import nl.hauntedmc.serverfeatures.features.chattools.ChatTools;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ChatCommand extends FeatureCommand {

    private final ChatTools feature;

    public ChatCommand(ChatTools feature) {
        super("chat");
        this.feature = feature;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender,
                           @NotNull String label,
                           String @NotNull [] args) {

        if (args.length == 0) {
            usage(sender); return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "lock"   -> lock(sender);
            case "unlock" -> unlock(sender);
            case "clear"  -> clear(sender);
            default       -> usage(sender);
        }
        return true;
    }

    private void lock(CommandSender sender) {
        if (!sender.hasPermission("serverfeatures.feature.chattools.command.chat.lock")) {
            noPerm(sender); return;
        }

        if (feature.isChatLocked()) {
            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("chattools.already_locked")
                    .forAudience(sender)
                    .build());
            return;
        }
        feature.setChatLocked(true);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("chattools.locked_broadcast")
                            .forAudience(player)
                            .build()
            );
        }

    }

    private void unlock(CommandSender sender) {
        if (!sender.hasPermission("serverfeatures.feature.chattools.command.chat.unlock")) {
            noPerm(sender); return;
        }

        if (!feature.isChatLocked()) {
            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("chattools.not_locked")
                    .forAudience(sender)
                    .build());
            return;
        }
        feature.setChatLocked(false);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("chattools.unlocked_broadcast")
                            .forAudience(player)
                            .build()
            );
        }
    }

    private void clear(CommandSender sender) {
        if (!sender.hasPermission("serverfeatures.feature.chattools.command.chat.clear")) {
            noPerm(sender); return;
        }

        int lines = (int) feature.getConfigHandler().getSetting("clear_lines");
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (int i = 0; i < lines; i++) p.sendMessage("");
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("chattools.cleared_broadcast")
                            .forAudience(player)
                            .build()
            );
        }
    }

    private void usage(CommandSender s) {
        s.sendMessage(feature.getLocalizationHandler()
                .getMessage("chattools.usage")
                .forAudience(s)
                .build());
    }
    private void noPerm(CommandSender s) {
        s.sendMessage(feature.getLocalizationHandler()
                .getMessage("general.no_permission")
                .forAudience(s)
                .build());
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender,
                                             @NotNull String alias,
                                             String[] args) {

        if (args.length == 1) {
            return Stream.of("lock", "unlock", "clear")
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
