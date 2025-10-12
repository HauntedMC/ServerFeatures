package nl.hauntedmc.serverfeatures.features.customrecipes;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.customrecipes.command.CustomRecipesCommand;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeService;
import nl.hauntedmc.serverfeatures.features.customrecipes.meta.Meta;

public class CustomRecipes extends BukkitBaseFeature<Meta> {

    private RecipeService recipeService;

    public CustomRecipes(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        return defaults;
    }

    /**
     * If you have any default messages you want to provide, you can add them here.
     * For example:
     */
    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("customrecipes.disable_usage", "&eGebruik: /customrecipes disable <key>");
        messages.add("customrecipes.enable_usage", "&eGebruik: /customrecipes enable <key>");
        messages.add("customrecipes.invalid_key", "&cOngeldig key-formaat: {key}");
        messages.add("customrecipes.disabled", "&eRecept {key} uitgeschakeld.");
        messages.add("customrecipes.disable_fail", "&cKon recept {key} niet uitschakelen.");
        messages.add("customrecipes.enabled", "&eRecept {key} ingeschakeld.");
        messages.add("customrecipes.enable_fail", "&cKon recept {key} niet inschakelen.");
        messages.add("customrecipes.active_list_title", "&aActieve Recepten:");
        messages.add("customrecipes.no_active", "&eGeen actieve recepten gevonden.");
        messages.add("customrecipes.list_entry", "&b{key} &7- &e{type}");
        return messages;
    }

    /**
     * Called when the feature is enabled. This method initializes the RecipeService,
     * loads the recipes from recipes.yml, and registers the custom command.
     */
    @Override
    public void initialize() {
        // Initialize RecipeService (which internally uses RecipeConfigHandler).
        recipeService = new RecipeService(this);
        recipeService.loadRecipes();

        getLifecycleManager().getCommandManager().registerFeatureCommand(new CustomRecipesCommand(this));
    }

    /**
     * Called when the feature is disabled. Unregisters the loaded recipes.
     */
    @Override
    public void disable() {
        if (recipeService != null) {
            recipeService.unregisterRecipes();
        }
    }

    public RecipeService getRecipeService() {
        return recipeService;
    }

}
