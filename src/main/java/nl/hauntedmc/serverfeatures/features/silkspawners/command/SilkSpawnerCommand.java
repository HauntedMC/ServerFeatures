package nl.hauntedmc.serverfeatures.features.silkspawners.command;

import nl.hauntedmc.serverfeatures.commands.CommandSpec;
import nl.hauntedmc.serverfeatures.commands.FeatureCommand;
import nl.hauntedmc.serverfeatures.features.silkspawners.SilkSpawners;
import nl.hauntedmc.serverfeatures.features.silkspawners.internal.SilkSpawnersHandler;
import nl.hauntedmc.serverfeatures.features.silkspawners.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SilkSpawnerCommand extends FeatureCommand {

    private final SilkSpawners feature;

    public SilkSpawnerCommand(SilkSpawners feature) {
        super(new CommandSpec.Builder("silkspawners").build());
        this.feature = feature;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender,
                           @NotNull String label,
                           String @NotNull [] args) {
        // Permission
        if (!sender.hasPermission("serverfeatures.feature.silkspawners.give")) {
            sender.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("general.no_permission")
                            .forAudience(sender)
                            .build()
            );
            return true;
        }

        // Expect: /silkspawners give <player> <mobtype> <amount>
        if (args.length == 0 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("silkspawners.give_usage")
                            .forAudience(sender)
                            .build()
            );
            return true;
        }

        if (args.length != 4) {
            sender.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("silkspawners.give_usage")
                            .forAudience(sender)
                            .build()
            );
            return true;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("silkspawners.player_not_found")
                            .withPlaceholders(Map.of("player", targetName))
                            .forAudience(sender)
                            .build()
            );
            return true;
        }

        String typeName = args[2].toUpperCase();
        EntityType type;
        try {
            type = EntityType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("silkspawners.invalid_mobtype")
                            .withPlaceholders(Map.of("type", typeName))
                            .forAudience(sender)
                            .build()
            );
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[3]);
            if (amount < 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("silkspawners.invalid_amount")
                            .forAudience(sender)
                            .build()
            );
            return true;
        }

        // Create the spawner item and give it
        ItemStack spawner = ItemUtils.createSpawnerItem(type);
        spawner.setAmount(amount);

        // Try to add to inventory, capture leftovers
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(spawner);

        // If inventory was full, drop the leftovers in front of the player
        if (!leftover.isEmpty()) {
            Location loc = target.getLocation();
            Vector dir = loc.getDirection().setY(0).normalize();
            Location dropLoc = loc.add(dir);
            leftover.values().forEach(item ->
                    target.getWorld().dropItemNaturally(dropLoc, item)
            );
        }
        sender.sendMessage(
                feature.getLocalizationHandler()
                        .getMessage("silkspawners.give_success")
                        .withPlaceholders(Map.of(
                                "player",   targetName,
                                "type",     typeName,
                                "amount",   String.valueOf(amount)))
                        .forAudience(sender)
                        .build()
        );

        target.sendMessage(
                feature.getLocalizationHandler()
                        .getMessage("silkspawners.receive_success")
                        .withPlaceholders(Map.of(
                                "type",     typeName,
                                "amount",   String.valueOf(amount)))
                        .forAudience(target)
                        .build()
        );

        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender,
                                             @NotNull String alias,
                                             String @NotNull [] args) {
        if (args.length == 1) {
            return filter(Collections.singletonList("give"), args[0]);
        }
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 3) {
            List<String> types = new ArrayList<>();
            for (EntityType e : EntityType.values()) {
                if (e.isAlive()) {
                    types.add(e.name().toLowerCase());
                }
            }
            return filter(types, args[2]);
        }
        if (args.length == 4) {
            return Collections.singletonList("<amount>");
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : options) {
            if (s.startsWith(lower)) out.add(s);
        }
        return out;
    }
}