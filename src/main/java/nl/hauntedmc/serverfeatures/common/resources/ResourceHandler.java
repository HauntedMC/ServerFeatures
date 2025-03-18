package nl.hauntedmc.serverfeatures.common.resources;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class ResourceHandler {
    private final ServerFeatures plugin;
    private final File file;
    private FileConfiguration config;

    public ResourceHandler(ServerFeatures plugin, String fileName) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            // Saves the resource from the jar if it doesn't exist.
            plugin.saveResource(fileName, false);
        }
        load();
    }

    /**
     * Loads the YAML file into the FileConfiguration.
     */
    public void load() {
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Returns the current configuration.
     */
    public FileConfiguration getConfig() {
        return config;
    }

    /**
     * Saves any changes to the file.
     */
    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save " + file.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Reloads the configuration from the file.
     */
    public void reload() {
        load();
    }
}
