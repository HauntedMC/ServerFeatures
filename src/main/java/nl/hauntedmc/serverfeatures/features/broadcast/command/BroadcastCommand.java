package nl.hauntedmc.serverfeatures.features.broadcast.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import nl.hauntedmc.serverfeatures.api.util.message.ComponentUtils;
import nl.hauntedmc.serverfeatures.api.command.meta.CommandMeta;
import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.features.broadcast.Broadcast;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BroadcastCommand extends FeatureCommand {

    private final Broadcast feature;

    public BroadcastCommand(Broadcast feature) {
        super(new CommandMeta.Builder("broadcast").build());
        this.feature = feature;
    }

    /* ------------------------------------------------- */
    /*  Permissions                                      */
    /* ------------------------------------------------- */
    @Override
    public boolean execute(@NotNull CommandSender sender,
                           @NotNull String label,
                           @NotNull String @NotNull [] args) {

        if (!sender.hasPermission(
                "serverfeatures.feature.broadcast.command.broadcast")) {
            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("general.no_permission")
                    .forAudience(sender)
                    .build());
            return true;
        }

        if (args.length < 2) {
            usage(sender);
            return true;
        }

        String mode    = args[0].toLowerCase(Locale.ROOT);
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        switch (mode) {
            case "chat"  -> broadcastChat(message, sender);
            case "title" -> broadcastTitle(message, sender);
            default      -> {
                sender.sendMessage(feature.getLocalizationHandler()
                        .getMessage("broadcast.noMode")
                        .forAudience(sender)
                        .build());
                usage(sender);
            }
        }
        return true;
    }

    /* ------------------------------------------------- */
    /*  Chat broadcast                                   */
    /* ------------------------------------------------- */
    private void broadcastChat(String raw, CommandSender sender) {
        String message = ComponentUtils.serializeLegacyString(raw);
        Component messageComponent = ComponentUtils.deserializeComponent(message);
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(messageComponent));
        ack(sender);
    }

    /* ------------------------------------------------- */
    /*  Title broadcast                                  */
    /* ------------------------------------------------- */
    private void broadcastTitle(String raw, CommandSender sender) {
        String titlePart;
        String subPart;

        if (raw.contains("|")) {
            String[] split = raw.split("\\|", 2);
            titlePart = split[0].trim();
            subPart   = split[1].trim();
        } else {
            titlePart = raw;
            subPart   = "";
        }

        titlePart = ComponentUtils.serializeLegacyString(titlePart);
        subPart = ComponentUtils.serializeLegacyString(subPart);
        Component titlePartComponent = ComponentUtils.deserializeComponent(titlePart);
        Component subPartComponent = ComponentUtils.deserializeComponent(subPart);

        int fadeIn  = (int) feature.getConfigHandler().getSetting("title_fade_in");
        int stay    = (int) feature.getConfigHandler().getSetting("title_stay");
        int fadeOut = (int) feature.getConfigHandler().getSetting("title_fade_out");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(Title.title(
                    titlePartComponent, subPartComponent,
                    Title.Times.times(Duration.ofSeconds(fadeIn/20), Duration.ofSeconds(stay/20), Duration.ofSeconds(fadeOut/20))
            ));
        }
        ack(sender);
    }

    private void ack(CommandSender s) {
        s.sendMessage(feature.getLocalizationHandler()
                .getMessage("broadcast.sent")
                .forAudience(s)
                .build());
    }

    private void usage(CommandSender s) {
        s.sendMessage(feature.getLocalizationHandler()
                .getMessage("broadcast.usage")
                .forAudience(s)
                .build());
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender,
                                             @NotNull String alias,
                                             String @NotNull [] args) {
        if (args.length == 1) {
            return Stream.of("chat", "title")
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

}
