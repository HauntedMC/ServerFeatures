package nl.hauntedmc.serverfeatures.features.villageroptimizer;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.villageroptimizer.internal.VillagerAIHandler;
import nl.hauntedmc.serverfeatures.features.villageroptimizer.internal.VillagerLevelHandler;
import nl.hauntedmc.serverfeatures.features.villageroptimizer.internal.VillagerRestockHandler;
import nl.hauntedmc.serverfeatures.features.villageroptimizer.listener.VillagerEventListener;
import nl.hauntedmc.serverfeatures.features.villageroptimizer.meta.Meta;

import java.util.List;

public class VillagerOptimizer extends BukkitBaseFeature<Meta> {

    private VillagerAIHandler villagerAIHandler;

    public VillagerOptimizer(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    /**
     * Returns default config values for the Titles feature.
     */
    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("cooldown", 600L);
        defaults.put("restockTimes", List.of(
                1000L,
                13000L
        ));

        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("villageroptimizer.AIdisabled", "&7De AI van deze Villager is &cuitgeschakeld&7, je kunt nu traden.");
        messages.add("villageroptimizer.AIenabled", "&7De AI van deze Villager is &aingeschakeld&7, je kunt nu breeden en farmen.");
        messages.add("villageroptimizer.cooldownBlockMessage", "&cJe kan de AI van deze villager weer aanpassen over {time_min} minuten en {time_sec} seconden.");
        messages.add("villageroptimizer.cooldownLevelupMessage", "&eDe villager is aan het levelen! Je kunt de villager weer gebruiken over {time_sec} &eseconden.");
        messages.add("villageroptimizer.nextRestock", "&eDe volgende restock van deze villager is over {time_min} &eminuten en {time_sec} &eseconden.");
        messages.add("villageroptimizer.villagerMustBeDisabled", "&eVillagers zijn heel zwaar voor de server. Om te kunnen &cTraden &emet een villager moet je de AI van de villager uit zetten. Rechtsklik met een &aEmerald Block &eop de villager om dit te doen.");
        return messages;
    }

    @Override
    public void initialize() {
        VillagerLevelHandler villagerLevelHandler = new VillagerLevelHandler(this);
        VillagerRestockHandler villagerRestockHandler = new VillagerRestockHandler(this);
        villagerAIHandler = new VillagerAIHandler(this, villagerRestockHandler, villagerLevelHandler);
        getLifecycleManager().getListenerManager().registerListener(new VillagerEventListener(this));
    }

    @Override
    public void disable() {
    }

    public VillagerAIHandler getVillagerAIHandler() {
        return villagerAIHandler;
    }
}
