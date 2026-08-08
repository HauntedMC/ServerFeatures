package nl.hauntedmc.serverfeatures.features.phantomtoggle;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.phantomtoggle.command.PhantomToggleCommand;
import nl.hauntedmc.serverfeatures.features.phantomtoggle.listener.PhantomSpawnListener;
import nl.hauntedmc.serverfeatures.features.phantomtoggle.meta.Meta;
import nl.hauntedmc.serverfeatures.features.phantomtoggle.persistence.PhantomPreferenceService;
import org.bukkit.entity.Player;

public final class PhantomToggle extends BukkitBaseFeature<Meta> {

    public static final String USE_PERMISSION = "serverfeatures.feature.phantomtoggle.use";

    private PhantomPreferenceService preferenceService;

    public PhantomToggle(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("default-phantoms-enabled", true);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("phantomtoggle.enabled", "&aPhantoms kunnen weer voor je spawnen.");
        messages.add("phantomtoggle.disabled", "&7Phantoms spawnen niet meer voor je.");
        messages.add("phantomtoggle.already_enabled", "&ePhantoms konden al voor je spawnen.");
        messages.add("phantomtoggle.already_disabled", "&ePhantoms waren al voor je uitgeschakeld.");
        messages.add("phantomtoggle.status.enabled", "&aPhantoms kunnen voor je spawnen.");
        messages.add("phantomtoggle.status.disabled", "&7Phantoms spawnen niet voor je.");
        return messages;
    }

    @Override
    public void initialize() {
        boolean defaultPhantomsEnabled = getConfigHandler()
                .node("default-phantoms-enabled")
                .as(Boolean.class, true);

        preferenceService = new PhantomPreferenceService(defaultPhantomsEnabled);

        getLifecycleManager().getListenerManager().registerListener(new PhantomSpawnListener(this));
        getLifecycleManager().getCommandManager().registerBrigadierCommand(new PhantomToggleCommand(this));

        getLogger().info("PhantomToggle loaded with local PDC-persisted player preferences.");
    }

    @Override
    public void disable() {
    }

    public PhantomPreferenceService preferences() {
        return preferenceService;
    }

    public void sendPlayerMessage(Player player, String key) {
        player.sendMessage(getLocalizationHandler().getMessage(key)
                .forAudience(player)
                .build());
    }
}
