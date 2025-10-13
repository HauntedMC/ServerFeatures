package nl.hauntedmc.serverfeatures.features.liquidtank.command;

import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.api.command.meta.CommandMeta;
import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.ItemCreator;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class LiquidTankCommand extends FeatureCommand {

    private static final String PERM_GIVE = "serverfeatures.feature.liquidtank.command.give";
    private static final String SUBCMD_GIVE = "give";

    private final LiquidTank feature;

    public LiquidTankCommand(LiquidTank feature) {
        super(new CommandMeta.Builder("liquidtank").build());
        this.feature = feature;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("general.player_command")
                    .forAudience(sender)
                    .build());
            return true;
        }

        if (!player.hasPermission(PERM_GIVE)) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("general.no_permission")
                    .forAudience(player)
                    .build());
            return true;
        }

        // /liquidtank give <player> [amount]
        if (args.length < 1 || !SUBCMD_GIVE.equalsIgnoreCase(args[0]) || args.length < 2) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("general.usage")
                    .forAudience(player)
                    .build());
            return true;
        }

        final String targetName = args[1];
        final Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("liquidtank.player_offline")
                    .forAudience(player)
                    .with("player", targetName)
                    .build());
            return true;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount < 1) {
                    player.sendMessage(feature.getLocalizationHandler()
                            .getMessage("liquidtank.invalid_amount")
                            .forAudience(player)
                            .build());
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(feature.getLocalizationHandler()
                        .getMessage("liquidtank.invalid_amount")
                        .forAudience(player)
                        .build());
                return true;
            }
        }

        // Give legit, signed tank item
        target.getInventory().addItem(ItemCreator.createTankItem(feature, amount));

        player.sendMessage(feature.getLocalizationHandler()
                .getMessage("liquidtank.given")
                .forAudience(player)
                .with("player", target.getName())
                .with("amount", String.valueOf(amount))
                .build());
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String @NotNull [] args) {
        if (args.length == 1) {
            return prefixMatch(List.of(SUBCMD_GIVE), args[0]);
        }
        if (args.length == 2 && SUBCMD_GIVE.equalsIgnoreCase(args[0])) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return prefixMatch(names, args[1]);
        }
        if (args.length == 3 && SUBCMD_GIVE.equalsIgnoreCase(args[0])) {
            return prefixMatch(List.of("1", "2", "3", "5", "10", "16", "32", "64"), args[2]);
        }
        return Collections.emptyList();
    }

    private static List<String> prefixMatch(List<String> options, String typed) {
        if (typed == null || typed.isEmpty()) return options;
        String lower = typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>(options.size());
        for (String opt : options) {
            if (opt.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(opt);
            }
        }
        return out;
    }
}
