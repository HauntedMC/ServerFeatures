package nl.hauntedmc.serverfeatures.features.villageroptimizer.internal;

import nl.hauntedmc.serverfeatures.features.villageroptimizer.VillagerOptimizer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class VillagerAIHandler {

    private final VillagerOptimizer feature;
    private final VillagerRestockHandler villagerRestockHandler;
    private final VillagerLevelHandler villagerLevelHandler;
    private final long cooldown;

    private static final String COOLDOWN_KEY = "cooldown";
    private static final String TIME_KEY = "time";
    private static final String LEVEL_COOLDOWN_KEY = "levelCooldown";
    private static final String DISABLED_BY_BLOCK_KEY = "disabledByBlock";


    public VillagerAIHandler(VillagerOptimizer feature, VillagerRestockHandler villagerRestockHandler, VillagerLevelHandler villagerLevelHandler) {
        this.feature = feature;
        this.cooldown = (long) (int) feature.getConfigHandler().get("cooldown");
        this.villagerRestockHandler = villagerRestockHandler;
        this.villagerLevelHandler = villagerLevelHandler;
    }

    public void toggleVillagerAI(Villager vil, Player player) {
        if (vil.isAware()) {
            if (hasCooldown(vil, player))
                return;
            vil.setAware(false);
            setDisabledByBlock(vil, true);
            setNewCooldown(vil, cooldown);
            player.sendMessage(feature.getLocalizationHandler().getMessage("villageroptimizer.AIdisabled")
                    .forAudience(player)
                    .build());
        } else {
            // Re-Enabling AI

            if (hasCooldown(vil, player))
                return;
            vil.setAware(true);
            setNewCooldown(vil, cooldown);
            setDisabledByBlock(vil, false);
            player.sendMessage(feature.getLocalizationHandler().getMessage("villageroptimizer.AIenabled")
                    .forAudience(player)
                    .build());
        }
    }

    public void setDisabledByBlock(Villager v, Boolean disabledByBlock) {
        PersistentDataContainer container = v.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(feature.getPlugin(), DISABLED_BY_BLOCK_KEY);
        container.set(key, PersistentDataType.STRING, disabledByBlock.toString());
    }

    public boolean hasDisabledByBlock(Villager v) {
        PersistentDataContainer container = v.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(feature.getPlugin(), DISABLED_BY_BLOCK_KEY);
        return container.has(key, PersistentDataType.STRING);
    }

    public boolean getDisabledByBlock(Villager v) {
        PersistentDataContainer container = v.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(feature.getPlugin(), DISABLED_BY_BLOCK_KEY);
        return Boolean.parseBoolean(container.get(key, PersistentDataType.STRING));
    }

    public void setNewCooldown(Villager v, Long cooldown) {
        PersistentDataContainer container = v.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(feature.getPlugin(), COOLDOWN_KEY);
        container.set(key, PersistentDataType.LONG, (System.currentTimeMillis() / 1000) + cooldown);
    }

    public boolean hasCooldown(Villager v) {
        PersistentDataContainer container = v.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(feature.getPlugin(), COOLDOWN_KEY);
        return container.has(key, PersistentDataType.LONG);
    }

    public long getCooldown(Villager v) {
        PersistentDataContainer container = v.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(feature.getPlugin(), COOLDOWN_KEY);
        return container.get(key, PersistentDataType.LONG);
    }

    public void restock(Villager v) {
        List<MerchantRecipe> recipes = v.getRecipes();
        for (MerchantRecipe r : recipes) {
            r.setUses(0);
        }
    }

    public void setNewTime(Villager v) {
        PersistentDataContainer container = v.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(feature.getPlugin(), TIME_KEY);
        container.set(key, PersistentDataType.LONG, v.getWorld().getFullTime());
    }

    public boolean hasTime(Villager v) {
        PersistentDataContainer container = v.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(feature.getPlugin(), TIME_KEY);
        return (container.has(key, PersistentDataType.LONG));
    }

    public long getTime(Villager v) {
        PersistentDataContainer container = v.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(feature.getPlugin(), TIME_KEY);
        return container.get(key, PersistentDataType.LONG);
    }

    public void setLevelCooldown(Villager v, Long cooldown) {
        PersistentDataContainer container = v.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(feature.getPlugin(), LEVEL_COOLDOWN_KEY);
        container.set(key, PersistentDataType.LONG, (System.currentTimeMillis() / 1000) + cooldown);
    }

    public boolean hasLevelCooldown(Villager v) {
        PersistentDataContainer container = v.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(feature.getPlugin(), LEVEL_COOLDOWN_KEY);
        return container.has(key, PersistentDataType.LONG);
    }

    public long getLevelCooldown(Villager v) {
        PersistentDataContainer container = v.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(feature.getPlugin(), LEVEL_COOLDOWN_KEY);
        return container.get(key, PersistentDataType.LONG);
    }

    public void sanityChecks(Villager vil, long currentTime) {
        long vilLevelCooldown = getLevelCooldown(vil);
        long vilCooldown = getCooldown(vil);
        long vilTime = getTime(vil);

        if (vilLevelCooldown > currentTime + getVillagerLevelHandler().getCooldown() * 2)
            setLevelCooldown(vil, getVillagerLevelHandler().getCooldown());

        if (vilCooldown > currentTime + getCooldown() * 2)
            setNewCooldown(vil, getCooldown());

        if (vilTime > vil.getWorld().getFullTime())
            setNewTime(vil);
    }

    public boolean hasCooldown(Villager vil, Player player) {
        if (player.hasPermission("serverfeatures.feature.villageroptimizer.toggle.bypass"))
            return false;

        long vilCooldown = getCooldown(vil);

        long currentTime = System.currentTimeMillis() / 1000;

        if (vilCooldown > currentTime) {

            long totalSeconds = vilCooldown - currentTime;
            long sec = totalSeconds % 60;
            long min = (totalSeconds - sec) / 60;

            player.sendMessage(feature.getLocalizationHandler().getMessage("villageroptimizer.cooldownBlockMessage")
                    .forAudience(player)
                    .with("time_min", min)
                    .with("time_sec", sec)
                    .build());
            return true;
        }
        return false;
    }

    public VillagerRestockHandler getVillagerRestockHandler() {
        return villagerRestockHandler;
    }

    public long getCooldown() {
        return cooldown;
    }

    public VillagerLevelHandler getVillagerLevelHandler() {
        return villagerLevelHandler;
    }

}
