package nl.hauntedmc.serverfeatures.features.customrecipes.command;

import nl.hauntedmc.serverfeatures.commands.CommandSpec;
import nl.hauntedmc.serverfeatures.commands.FeatureCommand;
import nl.hauntedmc.serverfeatures.features.customrecipes.CustomRecipes;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeData;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CustomRecipesCommand extends FeatureCommand {

    private final CustomRecipes feature;

    public CustomRecipesCommand(CustomRecipes feature) {
        super(new CommandSpec.Builder("customrecipes").build());
        this.feature = feature;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        if (args.length == 0) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("general.usage").forAudience(sender).build());
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "list":
                if (!sender.hasPermission("serverfeatures.feature.customrecipes.command.list")) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission").forAudience(sender).build());
                    return true;
                }
                handleList(sender);
                return true;
            case "disable":
                if (!sender.hasPermission("serverfeatures.feature.customrecipes.command.disable")) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission").forAudience(sender).build());
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("customrecipes.disable_usage").forAudience(sender).build());
                    return true;
                }
                handleDisable(sender, args[1]);
                return true;
            case "enable":
                if (!sender.hasPermission("serverfeatures.feature.customrecipes.command.enable")) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission").forAudience(sender).build());
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("customrecipes.enable_usage").forAudience(sender).build());
                    return true;
                }
                handleEnable(sender, args[1]);
                return true;
            default:
                sender.sendMessage(feature.getLocalizationHandler().getMessage("general.usage").forAudience(sender).build());
                return true;
        }
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(feature.getLocalizationHandler().getMessage("customrecipes.active_list_title").forAudience(sender).build());
        List<String> activeKeys = feature.getRecipeService().getActiveRecipeKeys();
        if (activeKeys.isEmpty()) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("customrecipes.no_active").forAudience(sender).build());
        } else {
            for (String keyStr : activeKeys) {
                RecipeData data = feature.getRecipeService().getRecipeData(NamespacedKey.fromString(keyStr));
                String type = (data != null) ? data.getType().name() : "Unknown";
                sender.sendMessage(feature.getLocalizationHandler().getMessage("customrecipes.list_entry").forAudience(sender).withPlaceholders(Map.of("key", keyStr, "type", type)).build());
            }
        }
    }

    private void handleDisable(CommandSender sender, String keyInput) {
        NamespacedKey key = NamespacedKey.fromString(keyInput);
        if (key == null) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("customrecipes.invalid_key").forAudience(sender).withPlaceholders(Map.of("key", keyInput)).build());
            return;
        }
        if (feature.getRecipeService().disableRecipe(key)) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("customrecipes.disabled").forAudience(sender).withPlaceholders(Map.of("key", key.toString())).build());
        } else {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("customrecipes.disable_fail").forAudience(sender).withPlaceholders(Map.of("key", key.toString())).build());
        }
    }

    private void handleEnable(CommandSender sender, String keyInput) {
        NamespacedKey key = NamespacedKey.fromString(keyInput);
        if (key == null) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("customrecipes.invalid_key").forAudience(sender).withPlaceholders(Map.of("key", keyInput)).build());
            return;
        }
        if (feature.getRecipeService().enableRecipe(key)) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("customrecipes.enabled").forAudience(sender).withPlaceholders(Map.of("key", key.toString())).build());
        } else {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("customrecipes.enable_fail").forAudience(sender).withPlaceholders(Map.of("key", key.toString())).build());
        }
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String @NotNull [] args) {
        if (args.length == 1) {
            return List.of("list", "disable", "enable");
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("disable")) {
                return feature.getRecipeService().getActiveRecipeKeys();
            } else if (sub.equals("enable")) {
                return feature.getRecipeService().getDisabledRecipeKeys();
            }
        }
        return Collections.emptyList();
    }
}
