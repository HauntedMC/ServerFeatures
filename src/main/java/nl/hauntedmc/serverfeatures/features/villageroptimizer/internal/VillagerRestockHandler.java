package nl.hauntedmc.serverfeatures.features.villageroptimizer.internal;

import nl.hauntedmc.serverfeatures.api.util.type.CastUtils;
import nl.hauntedmc.serverfeatures.features.villageroptimizer.VillagerOptimizer;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import java.util.List;

public class VillagerRestockHandler {
    private final VillagerOptimizer feature;
    private final List<Integer> restockTimes;

    public VillagerRestockHandler(VillagerOptimizer feature) {
        this.feature = feature;
        this.restockTimes = CastUtils.safeCastToList(feature.getConfigHandler().getSetting("restockTimes"), Integer.class);
    }

    public void sendRestockMessage(long timeTillNextRestock, Player player) {
        long totalsec = timeTillNextRestock / 20;
        long sec = totalsec % 60;
        long min = (totalsec - sec) / 60;
        player.sendMessage(feature.getLocalizationHandler().getMessage("villageroptimizer.nextRestock")
                .forAudience(player)
                .with("time_min",  min)
                .with("time_sec",  sec)
                .build());
    }

    public boolean handleRestock(Villager vil, long currDayTimeTick) {
        long curTick = vil.getWorld().getFullTime();

        long currentDay = curTick - currDayTimeTick;

        long vilTick = feature.getVillagerAIHandler().getTime(vil);

        for (long restockTime : restockTimes) {
            long todayRestock = currentDay + restockTime;
            if (curTick >= todayRestock && vilTick < todayRestock) {
                feature.getVillagerAIHandler().restock(vil);
                feature.getVillagerAIHandler().setNewTime(vil);
                return true;
            }
        }
        return false;
    }

    public void restockVillager(Villager vil, Player player) {
        long currDayTimeTick = vil.getWorld().getTime();

        if (player.hasPermission("serverfeatures.feature.villageroptimizer.restock.bypass")) {
            feature.getVillagerAIHandler().restock(vil);
            feature.getVillagerAIHandler().setNewTime(vil);
            return;
        }
        if (!feature.getVillagerAIHandler().hasTime(vil)) {
            feature.getVillagerAIHandler().restock(vil);
            feature.getVillagerAIHandler().setNewTime(vil);
            return;
        }
        if (handleRestock(vil, currDayTimeTick))
            return;

        if (player.hasPermission("serverfeatures.feature.villageroptimizer.restock.notify")) {
            long timeTillNextRestock = getTimeTillNextRestock(vil, currDayTimeTick);

            sendRestockMessage(timeTillNextRestock, player);
        }
    }

    private long getTimeTillNextRestock(Villager vil, long currDayTimeTick) {
        long timeTillNextRestock = Long.MAX_VALUE;
        long currentDay = vil.getWorld().getFullTime() - currDayTimeTick;

        for (long restockTime : restockTimes) {
            long restockTick = currentDay + restockTime;
            if (vil.getWorld().getFullTime() < restockTick) {
                timeTillNextRestock = Math.min(timeTillNextRestock, restockTick - vil.getWorld().getFullTime());
            }
        }

        if (timeTillNextRestock == Long.MAX_VALUE) {
            timeTillNextRestock = (24000 + currentDay + restockTimes.getFirst()) - vil.getWorld().getFullTime();
        }
        return timeTillNextRestock;
    }
}
