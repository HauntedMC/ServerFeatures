package nl.hauntedmc.serverfeatures.features.antiraidfarm;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.antiraidfarm.command.AntiRaidFarmCommand;
import nl.hauntedmc.serverfeatures.features.antiraidfarm.internal.AntiRaidFarmHandler;
import nl.hauntedmc.serverfeatures.features.antiraidfarm.listener.AntiRaidFarmListener;
import nl.hauntedmc.serverfeatures.features.antiraidfarm.meta.Meta;

public final class AntiRaidFarm extends BukkitBaseFeature<Meta> {

    private AntiRaidFarmHandler handler;

    public AntiRaidFarm(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);
        cfg.put("raid_cooldown_seconds", 600);
        cfg.put("notify", true);
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();
        m.add("antiraidfarm.blocked", "&cJe hebt recent een raid getriggerd. Wacht &f{seconds}s &calsjeblieft.");
        m.add("antiraidfarm.list.header", "&6&lAntiRaidFarm Cooldowns &7({count})");
        m.add("antiraidfarm.list.entry", "&7- &f{player}&7: &f{remaining}s &8(of {total}s)");
        m.add("antiraidfarm.list.none", "&7Geen actieve cooldowns.");

        return m;
    }

    @Override
    public void initialize() {
        int cooldownSeconds = getConfigHandler().node("raid_cooldown_seconds").as(Integer.class, 600);
        boolean notify = getConfigHandler().node("notify").as(Boolean.class, true);

        this.handler = new AntiRaidFarmHandler(cooldownSeconds, notify);

        getLifecycleManager().getListenerManager().registerListener(new AntiRaidFarmListener(this, handler));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new AntiRaidFarmCommand(this, handler));
    }

    @Override
    public void disable() {
    }

    public AntiRaidFarmHandler getHandler() {
        return handler;
    }
}
