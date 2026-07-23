package nl.hauntedmc.serverfeatures.features.nickname.command;

import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.api.command.meta.CommandMeta;
import nl.hauntedmc.serverfeatures.features.nickname.Nickname;
import nl.hauntedmc.serverfeatures.features.nickname.internal.NicknameHandler.NicknameMutationResult;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NickCommand extends FeatureCommand {

    private static final String PERMISSION_SELF = "serverfeatures.feature.nickname.command.nickname";
    private static final String PERMISSION_OTHER = "serverfeatures.feature.nickname.command.nickname_other";

    private final Nickname feature;

    public NickCommand(Nickname feature) {
        super(new CommandMeta.Builder("nickname").build());
        this.feature = feature;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command")
                    .forAudience(sender)
                    .build());
            return true;
        }

        if (!player.hasPermission(PERMISSION_SELF)) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission_rank")
                    .forAudience(player)
                    .with("rank", "&6Elite")
                    .build());
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("general.usage")
                    .forAudience(player)
                    .build());
            return true;
        }

        if (args.length == 1) {
            handleSelf(player, args[0]);
            return true;
        }

        if (args.length == 2) {
            if (!player.hasPermission(PERMISSION_OTHER)) {
                player.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission")
                        .forAudience(player)
                        .build());
                return true;
            }
            handleOther(player, args[0], args[1]);
            return true;
        }

        player.sendMessage(feature.getLocalizationHandler().getMessage("nickname.one_word")
                .forAudience(player)
                .build());
        return true;
    }

    private void handleSelf(Player player, String value) {
        String playerIdentifier = player.getUniqueId().toString();
        feature.getNicknameHandler().findPlayerIdentity(playerIdentifier).whenComplete((identity, lookupThrowable) -> {
            if (lookupThrowable != null) {
                feature.getLogger().warning("Could not resolve nickname identity for " + playerIdentifier + ": "
                        + rootMessage(lookupThrowable));
                scheduleMain(() -> sendDataUnavailable(player));
                return;
            }
            if (identity == null || identity.isEmpty()) {
                scheduleMain(() -> sendDataUnavailable(player));
                return;
            }

            if (value.equalsIgnoreCase("remove")) {
                removeSelf(player, identity.get());
            } else {
                setSelf(player, identity.get(), value);
            }
        });
    }

    private void setSelf(Player player, PlayerIdentity identity, String value) {
        feature.getNicknameHandler().setNickname(identity, value).whenComplete((result, throwable) -> {
            if (throwable != null) {
                feature.getLogger().warning("Could not save nickname for " + identity.uuid() + ": "
                        + rootMessage(throwable));
                scheduleMain(() -> sendDataUnavailable(player));
                return;
            }
            scheduleMain(() -> {
                if (!player.isOnline()) {
                    return;
                }
                if (!result.success()) {
                    feature.getNicknameHandler().sendValidationFailure(player, result.failure());
                    return;
                }
                player.sendMessage(feature.getLocalizationHandler().getMessage("nickname.set")
                        .forAudience(player)
                        .with("nickname", result.nickname())
                        .build());
            });
        });
    }

    private void removeSelf(Player player, PlayerIdentity identity) {
        feature.getNicknameHandler().removeNickname(identity).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                feature.getLogger().warning("Could not remove nickname for " + identity.uuid() + ": "
                        + rootMessage(throwable));
                scheduleMain(() -> sendDataUnavailable(player));
                return;
            }
            scheduleMain(() -> {
                if (player.isOnline()) {
                    player.sendMessage(feature.getLocalizationHandler().getMessage("nickname.removed")
                            .forAudience(player)
                            .build());
                }
            });
        });
    }

    private void handleOther(Player actor, String identifier, String value) {
        feature.getNicknameHandler().findPlayerIdentity(identifier).whenComplete((identity, lookupThrowable) -> {
            if (lookupThrowable != null) {
                feature.getLogger().warning("Could not resolve nickname target " + identifier + ": "
                        + rootMessage(lookupThrowable));
                scheduleMain(() -> sendDataUnavailable(actor));
                return;
            }
            if (identity == null || identity.isEmpty()) {
                scheduleMain(() -> {
                    if (actor.isOnline()) {
                        actor.sendMessage(feature.getLocalizationHandler().getMessage("nickname.player_not_found")
                                .forAudience(actor)
                                .build());
                    }
                });
                return;
            }

            PlayerIdentity targetIdentity = identity.get();
            if (value.equalsIgnoreCase("remove")) {
                removeOther(actor, targetIdentity);
            } else {
                setOther(actor, targetIdentity, value);
            }
        });
    }

    private void setOther(Player actor, PlayerIdentity targetIdentity, String value) {
        feature.getNicknameHandler().setNickname(targetIdentity, value).whenComplete((result, throwable) -> {
            if (throwable != null) {
                feature.getLogger().warning("Could not save nickname for " + targetIdentity.uuid() + ": "
                        + rootMessage(throwable));
                scheduleMain(() -> sendDataUnavailable(actor));
                return;
            }

            scheduleMain(() -> completeSetOther(actor, targetIdentity, result));
        });
    }

    private void completeSetOther(Player actor, PlayerIdentity targetIdentity, NicknameMutationResult result) {
        if (!actor.isOnline()) {
            return;
        }
        if (!result.success()) {
            feature.getNicknameHandler().sendValidationFailure(actor, result.failure());
            return;
        }

        Player onlineTarget = Bukkit.getPlayer(targetIdentity.uuid());
        if (onlineTarget != null && onlineTarget.isOnline()) {
            onlineTarget.sendMessage(feature.getLocalizationHandler().getMessage("nickname.set")
                    .forAudience(onlineTarget)
                    .with("nickname", result.nickname())
                    .build());
        }
        actor.sendMessage(feature.getLocalizationHandler().getMessage("nickname.set_other")
                .forAudience(actor)
                .with("player", targetIdentity.username())
                .with("nickname", result.nickname())
                .build());
    }

    private void removeOther(Player actor, PlayerIdentity targetIdentity) {
        feature.getNicknameHandler().removeNickname(targetIdentity).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                feature.getLogger().warning("Could not remove nickname for " + targetIdentity.uuid() + ": "
                        + rootMessage(throwable));
                scheduleMain(() -> sendDataUnavailable(actor));
                return;
            }

            scheduleMain(() -> {
                if (!actor.isOnline()) {
                    return;
                }
                Player onlineTarget = Bukkit.getPlayer(targetIdentity.uuid());
                if (onlineTarget != null && onlineTarget.isOnline()) {
                    onlineTarget.sendMessage(feature.getLocalizationHandler().getMessage("nickname.removed")
                            .forAudience(onlineTarget)
                            .build());
                }
                actor.sendMessage(feature.getLocalizationHandler().getMessage("nickname.other_removed")
                        .forAudience(actor)
                        .with("player", targetIdentity.username())
                        .build());
            });
        });
    }

    private void sendDataUnavailable(Player player) {
        if (player.isOnline()) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("nickname.data_unavailable")
                    .forAudience(player)
                    .build());
        }
    }

    private void scheduleMain(Runnable task) {
        try {
            feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(task);
        } catch (RuntimeException exception) {
            feature.getLogger().warning("Could not schedule nickname command completion: "
                    + rootMessage(exception));
        }
    }

    @Override
    public @NotNull List<String> tabComplete(
            @NotNull CommandSender sender,
            @NotNull String alias,
            String @NotNull [] args
    ) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 2 && "remove".startsWith(args[1].toLowerCase(Locale.ROOT))) {
            completions.add("remove");
        }
        return completions;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
