package nl.hauntedmc.serverfeatures.features.playerdata;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.playerdata.command.PlayerDataCommand;
import nl.hauntedmc.serverfeatures.features.playerdata.meta.Meta;
import nl.hauntedmc.serverfeatures.features.playerdata.service.PlayerDataService;
import org.bukkit.command.CommandSender;

public final class PlayerData extends BukkitBaseFeature<Meta> {

    public static final String INSPECT_PERMISSION = "serverfeatures.feature.playerdata.inspect";

    private PlayerDataService service;

    public PlayerData(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("output.max-entries", 100);
        defaults.put("output.max-value-length", 240);
        defaults.put("offline.max-compressed-bytes", 4 * 1024 * 1024);
        defaults.put("offline.max-decompressed-bytes", 32 * 1024 * 1024);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add(
                "playerdata.usage",
                "&eGebruik: /playerdata <speler|uuid> [overview|runtime|settings|pdc|nbt [pad]]"
        );
        messages.add("playerdata.loading", "&7Playerdata van &f{player} &7wordt gelezen...");
        messages.add("playerdata.not_found", "&cGeen lokale playerdata gevonden voor &f{player}&c.");
        messages.add("playerdata.read_failed", "&cPlayerdata van &f{player} &ckon niet veilig worden gelezen.");
        messages.add("playerdata.offline_runtime", "&e{player} is offline; runtimegegevens zijn alleen live beschikbaar.");
        messages.add(
                "playerdata.online_nbt",
                "&e{player} is online. Raw .dat-NBT wordt dan niet getoond omdat de schijfkopie achter kan lopen; gebruik overview, runtime, settings of pdc."
        );
        messages.add("playerdata.empty", "&7Geen gegevens in dit onderdeel.");
        messages.add(
                "playerdata.truncated",
                "&7Alleen de eerste &f{shown}&7 van &f{total} &7items worden getoond."
        );
        return messages;
    }

    @Override
    public void initialize() {
        int maxEntries = Math.clamp(
                getConfigHandler().get("output.max-entries", Integer.class, 100),
                10,
                500
        );
        int maxValueLength = Math.clamp(
                getConfigHandler().get("output.max-value-length", Integer.class, 240),
                40,
                2000
        );
        int maxCompressedBytes = Math.clamp(
                getConfigHandler().get("offline.max-compressed-bytes", Integer.class, 4 * 1024 * 1024),
                256 * 1024,
                16 * 1024 * 1024
        );
        int configuredDecompressed = getConfigHandler().get(
                "offline.max-decompressed-bytes",
                Integer.class,
                32 * 1024 * 1024
        );
        int maxDecompressedBytes = Math.clamp(
                configuredDecompressed,
                maxCompressedBytes,
                128 * 1024 * 1024
        );

        service = new PlayerDataService(
                this,
                maxEntries,
                maxValueLength,
                maxCompressedBytes,
                maxDecompressedBytes
        );
        getLifecycleManager().getCommandManager().registerBrigadierCommand(new PlayerDataCommand(this));
        getLogger().info("PlayerData loaded as a read-only live/offline playerdata inspector.");
    }

    @Override
    public void disable() {
        if (service != null) {
            service.close();
        }
    }

    public PlayerDataService service() {
        return service;
    }

    public void send(CommandSender sender, String key, String... placeholders) {
        var message = getLocalizationHandler().getMessage(key).forAudience(sender);
        for (int index = 0; index + 1 < placeholders.length; index += 2) {
            message.with(placeholders[index], placeholders[index + 1]);
        }
        sender.sendMessage(message.build());
    }
}
