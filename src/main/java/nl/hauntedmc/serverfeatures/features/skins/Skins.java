package nl.hauntedmc.serverfeatures.features.skins;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.skins.command.SkinsCommand;
import nl.hauntedmc.serverfeatures.features.skins.internal.SkinState;
import nl.hauntedmc.serverfeatures.features.skins.listener.SkinsListener;
import nl.hauntedmc.serverfeatures.features.skins.meta.Meta;

public class Skins extends BukkitBaseFeature<Meta> {

    private final SkinState state = new SkinState(this);

    public Skins(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", true);
        cfg.put("cooldown_seconds", 60); // self-command cooldown
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();

        // Usage
        m.add("skins.usage.self",   "&eGebruik: /skin <naam|remove>");
        m.add("skins.usage.other",  "&eGebruik: /skin <speler> <naam|remove>");

        // Success feedback (self)
        m.add("skins.applied.self",         "&aJe skin is aangepast naar &e{skin}&a.");
        m.add("skins.removed.self",         "&aJe aangepaste skin is verwijderd.");
        m.add("skins.none_applied.self",    "&eJe hebt momenteel geen aangepaste skin.");

        // Success feedback (staff -> other)
        m.add("skins.applied.other",        "&aSkin van &e{player}&a is aangepast naar &e{skin}&a.");
        m.add("skins.removed.other",        "&aAangepaste skin van &e{player}&a is verwijderd.");
        m.add("skins.none_applied.other",   "&e{player}&e heeft momenteel geen aangepaste skin.");
        m.add("skins.notify_target_applied","&eJe skin is aangepast naar &6{skin}&e door een staff-lid.");
        m.add("skins.notify_target_removed","&eJe aangepaste skin is verwijderd door een staff-lid.");

        // Errors / validation
        m.add("skins.invalid_name",         "&cOngeldige Minecraft-naam: &e{skin}&c. (3-16 tekens: letters, cijfers, underscore)");
        m.add("skins.player_not_found",     "&cSpeler niet gevonden of offline: &e{player}&c.");
        m.add("skins.lookup_failed",        "&cKon de skin niet ophalen voor &e{skin}&c. Probeer het later opnieuw.");
        m.add("skins.cooldown_active",      "&cJe kunt dit nog niet doen. Wacht &e{seconds}s&c.");

        // Progress / info
        m.add("skins.working",              "&7Bezig met het ophalen en toepassen van de skin &f{skin}&7...");
        m.add("skins.removing",             "&7Bezig met het verwijderen van jouw aangepaste skin...");

        return m;
    }

    @Override
    public void initialize() {
        getLifecycleManager().getCommandManager()
                .registerFeatureCommand(new SkinsCommand(this));
        getLifecycleManager().getListenerManager()
                .registerListener(new SkinsListener(this));
    }

    @Override
    public void disable() {
        state.clearAll();
    }

    public SkinState getState() {
        return state;
    }
}
