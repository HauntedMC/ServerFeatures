package nl.hauntedmc.serverfeatures.features.liquidtank;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BaseFeature;
import nl.hauntedmc.serverfeatures.features.liquidtank.command.LiquidTankCommand;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.LiquidTankManager;
import nl.hauntedmc.serverfeatures.features.liquidtank.meta.Meta;
import nl.hauntedmc.serverfeatures.localization.MessageMap;

import java.util.HashMap;
import java.util.Map;

public class LiquidTank extends BaseFeature<Meta> {

    private LiquidTankManager tankManager;

    public LiquidTank(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", true);
        defaults.put("item-name", "&bLiquid Tank");
        defaults.put("enable-items", true);
        defaults.put("enable-crafting", false);
        defaults.put("amount-per-chunk", 16);
        defaults.put("lava-amount", 128);
        defaults.put("water-amount", 128);
        defaults.put("milk-amount", 128);
        defaults.put("mushroomStew-amount", 128);
        defaults.put("rabbitStew-amount", 128);
        defaults.put("dragonBreath-amount", 128);
        defaults.put("beetroot-amount", 128);
        defaults.put("honey-amount", 128);
        defaults.put("need-redstone-for-export", false);
        defaults.put("water-fountains-enabled", false);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("liquidtank.given", "&aGiven {player} {amount} liquid tank(s)!");
        messages.add("liquidtank.player_offline", "&cPlayer {player} is not online!");
        messages.add("liquidtank.invalid_amount", "&cInvalid amount! Must be a number greater than 0.");
        return messages;
    }

    @Override
    public void initialize() {
        this.tankManager = new LiquidTankManager(this);
        getLifecycleManager().getCommandManager().registerFeatureCommand(new LiquidTankCommand(this));
    }

    @Override
    public void disable() {
    }

    public LiquidTankManager getTankManager() {
        return tankManager;
    }
}
