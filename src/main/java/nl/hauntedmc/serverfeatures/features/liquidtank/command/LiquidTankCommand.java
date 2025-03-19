package nl.hauntedmc.serverfeatures.features.liquidtank.command;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.commands.FeatureCommand;
import nl.hauntedmc.serverfeatures.common.util.TextUtils;
import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class LiquidTankCommand extends FeatureCommand {

    private final LiquidTank feature;

    public LiquidTankCommand(LiquidTank feature) {
        super("liquidtank");
        this.feature = feature;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        // Only players may use this command.
        if (!(sender instanceof Player player)) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("general.only_player", sender));
            return true;
        }

        // Check staff permission.
        if (!player.hasPermission("serverfeatures.feature.liquidtank.command.give")) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission", player));
            return true;
        }

        // Usage: /liquidtank give <player> [amount]
        if (args.length < 1 || !args[0].equalsIgnoreCase("give") || args.length < 2) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("liquidtank.give_usage", player));
            return true;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("liquidtank.player_offline", player, Map.of("player", targetName)));
            return true;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount < 1) {
                    player.sendMessage(feature.getLocalizationHandler().getMessage("liquidtank.invalid_amount", player));
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(feature.getLocalizationHandler().getMessage("liquidtank.invalid_amount", player));
                return true;
            }
        }

        // Give the target the liquid tank item.
        target.getInventory().addItem(getTankItem(amount));
        player.sendMessage(feature.getLocalizationHandler().getMessage("liquidtank.given", player, Map.of("{player}", target.getName(), "{amount}", String.valueOf(amount))));
        return true;
    }

    private org.bukkit.inventory.ItemStack getTankItem(int amount) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(org.bukkit.Material.HOPPER, amount);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        String displayName = (String) feature.getPlugin().getConfig().get("item-name");
        meta.displayName(Component.text(TextUtils.parseLegacyColors(displayName)));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String @NotNull [] args) {
        if (args.length == 1) {
            return List.of("give");
        }
        return Collections.emptyList();
    }
}
