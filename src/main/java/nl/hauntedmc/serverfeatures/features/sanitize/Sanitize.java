package nl.hauntedmc.serverfeatures.features.sanitize;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.SanitizeService;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.impl.*;
import nl.hauntedmc.serverfeatures.features.sanitize.meta.Meta;

public class Sanitize extends BukkitBaseFeature<Meta> {

    private SanitizeService service;

    public Sanitize(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);
        cfg.put("clean_cache_on_startup", true);
        cfg.put("clean_versions_on_startup", true);
        cfg.put("enforce_default_configs_on_startup", true);
        cfg.put("enforce_server_properties_on_startup", true);
        cfg.put("enforce_bukkit_yml_on_startup", true);
        cfg.put("enforce_spigot_yml_on_startup", true);
        cfg.put("enforce_paper_global_yml_on_startup", true);
        cfg.put("check_gamerules_on_startup", true);
        cfg.put("clean_logs_on_startup", true);
        cfg.put("log_retention_days", 7);
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        this.service = new SanitizeService(this);

        if (getBoolean("clean_cache_on_startup", false)) {
            service.addTask(new CacheSanitizeTask());
        }
        if (getBoolean("clean_versions_on_startup", false)) {
            service.addTask(new VersionsSanitizeTask());
        }

        if (getBoolean("enforce_default_configs_on_startup", false)) {
            service.addTask(new DefaultConfigsSanitizeTask());
        }

        if (getBoolean("enforce_server_properties_on_startup", false)) {
            service.addTask(new ServerPropertiesSanitizeTask());
        }

        if (getBoolean("enforce_bukkit_yml_on_startup", false)) {
            service.addTask(new BukkitYmlSanitizeTask());
        }

        if (getBoolean("enforce_spigot_yml_on_startup", false)) {
            service.addTask(new SpigotYmlSanitizeTask());
        }

        if (getBoolean("enforce_paper_global_yml_on_startup", false)) {
            service.addTask(new PaperGlobalYmlSanitizeTask());
        }

        if (getBoolean("check_gamerules_on_startup", false)) {
            service.addTask(new GameRulesCheckTask(getLogger()));
        }

        if (getBoolean("clean_logs_on_startup", false)) {
            int days = getInt("log_retention_days", 7);
            service.addTask(new LogSanitizeTask(days));
        }

        getLifecycleManager().getTaskManager().scheduleAsyncTask(() -> {
            try {
                service.runStartupSanitize();
            } catch (Throwable t) {
                getLogger().warning("Startup sanitize failed: " + t.getMessage());
            }
        });
    }

    @Override
    public void disable() {
        this.service = null;
    }

    private boolean getBoolean(String key, boolean def) {
        Object v = getConfigHandler().getSetting(key);
        return (v instanceof Boolean b) ? b : def;
    }

    private int getInt(String key, int def) {
        Object v = getConfigHandler().getSetting(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return def;
    }
}
