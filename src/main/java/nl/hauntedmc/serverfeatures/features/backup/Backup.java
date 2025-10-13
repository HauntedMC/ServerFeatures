package nl.hauntedmc.serverfeatures.features.backup;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.backup.internal.BackupService;
import nl.hauntedmc.serverfeatures.features.backup.meta.Meta;

import java.util.List;

public class Backup extends BukkitBaseFeature<Meta> {

    private BackupService service;

    public Backup(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);

        cfg.put("backup_folder_name", "backups");
        cfg.put("zip_name_prefix", "backup_");
        cfg.put("compression_level", 6); // 0-9

        cfg.put("include.paths", List.of(
                "plugins",
                "config",
                "bukkit.yml",
                "commands.yml",
                "server.properties",
                "spigot.yml"
        ));

        cfg.put("retention.daily_days", 7);
        cfg.put("retention.keep_monthly", 1);
        cfg.put("retention.monthly_threshold_days", 30);
        cfg.put("retention.keep_quarterly", 1);
        cfg.put("retention.quarterly_threshold_days", 90);

        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        this.service = new BackupService(this);

        getLifecycleManager().getTaskManager().scheduleAsyncDelayedTask(() -> {
            try {
                service.runStartupBackup();
            } catch (Throwable t) {
                getLogger().warning("Startup backup failed: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
            }
        }, BukkitTime.seconds(5));
    }

    @Override
    public void disable() {
        this.service = null;
    }

}
