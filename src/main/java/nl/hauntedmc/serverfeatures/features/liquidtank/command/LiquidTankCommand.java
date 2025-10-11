package nl.hauntedmc.serverfeatures.features.liquidtank.command;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.util.message.ComponentUtils;
import nl.hauntedmc.serverfeatures.api.command.meta.CommandMeta;
import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
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
        super(new CommandMeta.Builder("liquidtank").build());
        this.feature = feature;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        // Only players may use this command.
        if (!(sender instanceof Player player)) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(sender).build());
            return true;
        }

        // Check staff permission.
        if (!player.hasPermission("serverfeatures.feature.liquidtank.command.give")) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission").forAudience(player).build());
            return true;
        }

        // Usage: /liquidtank give <player> [amount]
        if (args.length < 1 || !args[0].equalsIgnoreCase("give") || args.length < 2) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("general.usage").forAudience(player).build());
            return true;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("liquidtank.player_offline").forAudience(player).withPlaceholders(Map.of("player", targetName)).build());
            return true;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount < 1) {
                    player.sendMessage(feature.getLocalizationHandler().getMessage("liquidtank.invalid_amount").forAudience(player).build());
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(feature.getLocalizationHandler().getMessage("liquidtank.invalid_amount").forAudience(player).build());
                return true;
            }
        }

        // Give the target the liquid tank item.
        target.getInventory().addItem(getTankItem(amount));
        player.sendMessage(feature.getLocalizationHandler().getMessage("liquidtank.given").forAudience(player).withPlaceholders(Map.of("player", target.getName(), "amount", String.valueOf(amount))).build());
        return true;
    }

    private org.bukkit.inventory.ItemStack getTankItem(int amount) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(org.bukkit.Material.HOPPER, amount);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        String displayName = ((String) feature.getConfigHandler().getSetting("item-name")).replace("&", "§");
        meta.displayName(Component.text(ComponentUtils.serializeLegacyString(displayName)));
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
