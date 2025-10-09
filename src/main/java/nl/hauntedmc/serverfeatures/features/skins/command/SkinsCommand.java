package nl.hauntedmc.serverfeatures.features.skins.command;

import nl.hauntedmc.serverfeatures.api.command.meta.CommandMeta;
import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.features.skins.Skins;
import nl.hauntedmc.serverfeatures.features.skins.service.SkinService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class SkinsCommand extends FeatureCommand {

    private static final String PERM_SELF   = "serverfeatures.feature.skins.command.skin.self";
    private static final String PERM_OTHERS = "serverfeatures.feature.skins.command.skin.others";

    private final Skins feature;
    private final SkinService service;

    public SkinsCommand(Skins feature) {
        super(new CommandMeta.Builder("skin").build());
        this.feature = feature;
        this.service = new SkinService(feature);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender,
                           @NotNull String label,
                           String @NotNull [] args) {

        // /skin <name|remove>
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                // Console or command blocks must use the 2-arg variant
                usageOther(sender);
                return true;
            }
            if (!sender.hasPermission(PERM_SELF)) {
                noPerm(sender); return true;
            }

            String sub = args[0];
            if (equalsIgnoreCase(sub, "remove")) {
                service.removeSkin(sender, player, true);
            } else {
                service.applySkin(sender, player, sub, true);
            }
            return true;
        }

        // /skin <player> <name|remove>
        if (args.length == 2) {
            if (!sender.hasPermission(PERM_OTHERS)) {
                noPerm(sender); return true;
            }

            String playerName = args[0];
            Player target = Bukkit.getPlayerExact(playerName);
            if (target == null || !target.isOnline()) {
                send(sender, "skins.player_not_found", Map.of("player", playerName));
                return true;
            }

            String sub = args[1];
            if (equalsIgnoreCase(sub, "remove")) {
                service.removeSkin(sender, target, false);
            } else {
                service.applySkin(sender, target, sub, false);
            }
            return true;
        }

        // Otherwise: show usage
        if (sender.hasPermission(PERM_OTHERS)) {
            usageOther(sender);
        } else {
            usageSelf(sender);
        }
        return true;
    }

    private boolean equalsIgnoreCase(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }

    private void usageSelf(CommandSender s) {
        s.sendMessage(feature.getLocalizationHandler()
                .getMessage("skins.usage.self")
                .forAudience(s)
                .build());
    }

    private void usageOther(CommandSender s) {
        s.sendMessage(feature.getLocalizationHandler()
                .getMessage("skins.usage.other")
                .forAudience(s)
                .build());
    }

    private void noPerm(CommandSender s) {
        s.sendMessage(feature.getLocalizationHandler()
                .getMessage("general.no_permission")
                .forAudience(s)
                .build());
    }

    private void send(CommandSender audience, String key, Map<String, String> placeholders) {
        var msg = feature.getLocalizationHandler().getMessage(key);
        if (placeholders != null && !placeholders.isEmpty()) {
            msg = msg.withPlaceholders(placeholders);
        }
        audience.sendMessage(msg.forAudience(audience).build());
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender,
                                             @NotNull String alias,
                                             String @NotNull [] args) {

        // /skin <name|remove>
        if (args.length == 1) {
            List<String> base = new ArrayList<>();
            base.add("remove");
            // Also suggest online player names (for 2-arg path) if they have perms
            if (sender.hasPermission(PERM_OTHERS)) {
                base.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return base.stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .limit(20)
                    .collect(Collectors.toList());
        }

        // /skin <player> <name|remove>
        if (args.length == 2 && sender.hasPermission(PERM_OTHERS)) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            // We can't know skin names; simply suggest "remove"
            return "remove".startsWith(prefix) ? List.of("remove") : Collections.emptyList();
        }

        return Collections.emptyList();
    }
}
