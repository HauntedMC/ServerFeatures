package nl.hauntedmc.serverfeatures.features.restart;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.restart.command.RestartCommand;
import nl.hauntedmc.serverfeatures.features.restart.internal.AutoRestartScheduler;
import nl.hauntedmc.serverfeatures.features.restart.internal.CommandOverride;
import nl.hauntedmc.serverfeatures.features.restart.internal.RestartService;
import nl.hauntedmc.serverfeatures.features.restart.meta.Meta;

import java.util.List;

public class Restart extends BukkitBaseFeature<Meta> {

    private RestartService service;
    private AutoRestartScheduler auto;

    public Restart(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap c = new ConfigMap();
        c.put("enabled", false);

        // Title timings (ticks)
        c.put("title_fade_in", 20);
        c.put("title_stay", 100);
        c.put("title_fade_out", 20);

        // Announce schedule (seconds remaining)
        c.put("announce.schedule", List.of(60, 30, 10, 0));

        // Auto restart
        c.put("auto.enabled", true);
        c.put("auto.time", "05:00"); // HH:mm (server timezone)
        c.put("auto.wait_after_now_seconds", 5); // seconds to wait after the "now" message

        return c;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();
        m.add("restart.in_progress", "&eEr is al een restart proces gestart.");
        m.add("restart.started", "&aRestart sequentie gestart.");
        m.add("restart.forced", "&f&l[RESTART] &cRestart geforceerd. &7Opslaan en direct herstarten...");
        m.add("restart.countdown.title", "&cServer restart over &f{readable}");
        m.add("restart.countdown.subtitle", "&7Bereid jezelf voor!");
        m.add("restart.countdown.chat", "&f&l[RESTART] &cDe server gaat restarten over &f{readable}&c.");
        m.add("restart.countdown.now.title", "&cServer Restart");
        m.add("restart.countdown.now.subtitle", "&7Tot straks!");
        m.add("restart.countdown.now.chat", "&f&l[RESTART] &cDe server gaat nu restarten...");
        m.add("restart.kick", "&cDe server wordt herstart. Je kunt zo weer joinen.");
        return m;
    }

    @Override
    public void initialize() {

        this.service = new RestartService(this);

        RestartCommand restartCmd = new RestartCommand(this, service);

        CommandOverride.unregisterVanillaRestart(getPlugin().getServer(), getLogger());

        // Aggressively take over restart bind from minecraft, bukkit, spigot and paper
        CommandOverride.takeoverRestart(
                getPlugin().getServer(),
                getLogger(),
                restartCmd,
                getPlugin().getName()
        );

        if (getBoolean("auto.enabled", false)) {
            String time = getString("auto.time", "04:00");
            this.auto = new AutoRestartScheduler(this, service, time);
            this.auto.scheduleNext();
        }

    }

    @Override
    public void disable() {
        if (auto != null) {
            auto.cancel();
            auto = null;
        }
        if (service != null) {
            service.cancelIfRunning();
            service = null;
        }
    }

    /* small helpers */
    public boolean getBoolean(String key, boolean def) {
        Object v = getConfigHandler().get(key);
        return (v instanceof Boolean b) ? b : def;
    }

    public int getInt(String key, int def) {
        Object v = getConfigHandler().get(key);
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Throwable ignored) {
        }
        return def;
    }

    public String getString(String key, String def) {
        Object v = getConfigHandler().get(key);
        return v == null ? def : String.valueOf(v);
    }
}
