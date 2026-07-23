package nl.hauntedmc.serverfeatures.features.chatlog.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.api.command.meta.CommandMeta;
import nl.hauntedmc.serverfeatures.features.chatlog.ChatLog;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ChatReportCommand extends FeatureCommand {

    private final ChatLog feature;

    public ChatReportCommand(ChatLog feature) {
        super(new CommandMeta.Builder("chatreport").build());
        this.feature = feature;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission("serverfeatures.feature.chatlog.use")) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission")
                    .forAudience(sender)
                    .build());
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command")
                    .forAudience(sender)
                    .build());
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("chatlog.help")
                    .forAudience(sender)
                    .build());
            return true;
        }

        long currentTime = System.currentTimeMillis();
        int timeframeMinutes = (int) feature.getConfigHandler().get("reportTimeFrameMinutes");
        long reportStart = currentTime - timeframeMinutes * 60 * 1000L;
        String serverName = (String) feature.getConfigHandler().getGlobalSetting("server_name");
        Set<String> requestedPlayers = new LinkedHashSet<>(List.of(args));

        List<CompletableFuture<PlayerMessageCount>> lookups = requestedPlayers.stream()
                .map(playerName -> feature.getReportHandler()
                        .checkMessage(serverName, playerName, reportStart, currentTime)
                        .thenApply(count -> new PlayerMessageCount(playerName, count))
                        .toCompletableFuture())
                .toList();

        CompletableFuture.allOf(lookups.toArray(CompletableFuture[]::new)).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                scheduleMain(() -> player.sendMessage(feature.getLocalizationHandler()
                        .getMessage("chatlog.errorNotSaved")
                        .forAudience(player)
                        .build()));
                return;
            }

            List<PlayerMessageCount> counts = lookups.stream().map(CompletableFuture::join).toList();
            List<String> reportedPlayers = counts.stream()
                    .filter(result -> result.count() > 0)
                    .map(PlayerMessageCount::playerName)
                    .toList();
            List<String> missingPlayers = counts.stream()
                    .filter(result -> result.count() < 1)
                    .map(PlayerMessageCount::playerName)
                    .toList();

            scheduleMain(() -> missingPlayers.forEach(playerName -> player.sendMessage(
                    feature.getLocalizationHandler().getMessage("chatlog.error")
                            .forAudience(player)
                            .with("name", playerName)
                            .build()
            )));

            if (reportedPlayers.isEmpty()) {
                scheduleMain(() -> player.sendMessage(feature.getLocalizationHandler()
                        .getMessage("chatlog.errorNotSaved")
                        .forAudience(player)
                        .build()));
                return;
            }

            String reportId = UUID.randomUUID().toString().replace("-", "");
            feature.getReportHandler()
                    .createReport(serverName, reportedPlayers, reportStart, currentTime, reportId)
                    .whenComplete((created, createThrowable) -> {
                        if (createThrowable != null) {
                            scheduleMain(() -> player.sendMessage(feature.getLocalizationHandler()
                                    .getMessage("chatlog.errorNotSaved")
                                    .forAudience(player)
                                    .build()));
                            return;
                        }

                        String baseUrl = (String) feature.getConfigHandler().get("URL");
                        String fullUrl = baseUrl + reportId;
                        scheduleMain(() -> {
                            if (!player.isOnline()) {
                                return;
                            }
                            Component clickableUrl = feature.getLocalizationHandler().getMessage("chatlog.url")
                                    .forAudience(player)
                                    .with("url", fullUrl)
                                    .build()
                                    .clickEvent(ClickEvent.openUrl(fullUrl));
                            player.sendMessage(clickableUrl);
                            feature.getReportHandler().sendDiscordNotifaction(
                                    player.getName(),
                                    reportedPlayers,
                                    serverName,
                                    fullUrl
                            );
                        });
                    });
        });

        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(
            @NotNull CommandSender sender,
            @NotNull String alias,
            String @NotNull [] args
    ) {
        String partial = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String name = player.getName();
            if (name.toLowerCase(Locale.ROOT).startsWith(partial)) {
                completions.add(name);
            }
        }
        return completions;
    }

    private void scheduleMain(Runnable task) {
        if (!feature.getPlugin().isEnabled()) {
            return;
        }
        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
            if (feature.getPlugin().isEnabled()) {
                task.run();
            }
        });
    }

    private record PlayerMessageCount(String playerName, int count) {
    }
}
