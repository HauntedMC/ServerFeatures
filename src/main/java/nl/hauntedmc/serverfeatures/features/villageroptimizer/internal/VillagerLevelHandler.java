package nl.hauntedmc.serverfeatures.features.villageroptimizer.internal;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.villageroptimizer.VillagerOptimizer;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;

public class VillagerLevelHandler {

    private final VillagerOptimizer feature;
    private final long cooldown;

    public VillagerLevelHandler(VillagerOptimizer feature) {
        this.feature = feature;
        this.cooldown = 5L;
    }

    public void updateVillagerLevel(Villager vil, Player player) {
        int vilLevel = vil.getVillagerLevel();
        long newLevel = calculateVillagerLevel(vil);
        long currentTime = System.currentTimeMillis() / 1000;

        long vilLevelCooldown = feature.getVillagerAIHandler().getLevelCooldown(vil);
        long totalSeconds = vilLevelCooldown - currentTime;
        long sec = totalSeconds % 60;

        if (vilLevelCooldown > currentTime) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("villageroptimizer.cooldownLevelupMessage")
                    .forAudience(player)
                    .withPlaceholders(Map.of("time_sec",  Long.toString(sec)))
                    .build());
            return;
        }

        if (vilLevel < newLevel) {
            feature.getVillagerAIHandler().setLevelCooldown(vil, cooldown);
            vil.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, (int)(cooldown * 20)+20, 120, false, false));
            vil.setAware(true);
        } else return;

        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask( () -> vil.setAware(false), BukkitTime.ticks(100L));
    }

    private long calculateVillagerLevel(Villager vil) {
        int vilEXP = vil.getVillagerExperience();
        // Villager Level depending on their XP
        // source: https://minecraft.fandom.com/wiki/Trading#Mechanics
        if (vilEXP >= 250) return 5;
        if (vilEXP >= 150) return 4;
        if (vilEXP >= 70) return 3;
        if (vilEXP >= 10) return 2;
        // default level is 1
        return 1;
    }

    public long getCooldown() {
        return cooldown;
    }
}
