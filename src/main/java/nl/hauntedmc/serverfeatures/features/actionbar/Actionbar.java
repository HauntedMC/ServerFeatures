package nl.hauntedmc.serverfeatures.features.actionbar;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.actionbar.command.ActionbarBrigadierCommand;
import nl.hauntedmc.serverfeatures.features.actionbar.command.ActionbarCommand;
import nl.hauntedmc.serverfeatures.features.actionbar.internal.ActionbarHandler;
import nl.hauntedmc.serverfeatures.features.actionbar.meta.Meta;

import java.util.Map;

public class Actionbar extends BukkitBaseFeature<Meta> {

    private ActionbarHandler actionbarHandler;

    public Actionbar(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);

        Map<String, Object> messages = new java.util.LinkedHashMap<>();

        Map<String, Object> defaultMsg = new java.util.LinkedHashMap<>();

        defaultMsg.put("duration", 100);
        messages.put("default", defaultMsg);

        defaults.put("messages", messages);
        defaults.put("message_interval", 0);

        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();

        messages.add("actionbar.usage", "&7Gebruik: /actionbar <start|stop|send> [bericht] [tijd in seconden (optioneel)]");
        messages.add("actionbar.started", "&7De ActionBar cyclus is gestart.");
        messages.add("actionbar.stopped", "&7De ActionBar cyclus is gestopt.");
        messages.add("actionbar.send_usage", "&7Gebruik: /actionbar send <bericht> [tijd in seconden]");
        messages.add("actionbar.invalid_time", "&cOngeldige tijd. Gebruik een numerieke waarde voor seconden.");
        messages.add("actionbar.sent_once", "&7ActionBar bericht verzonden: {message}");
        messages.add("actionbar.sent_timer", "&7ActionBar bericht verzonden voor {time} seconden: {message}");
        messages.add("actionbar.already_running", "&cDe ActionBar cyclus is al actief.");
        messages.add("actionbar.not_running", "&cEr is op dit moment geen ActionBar cyclus actief.");
        messages.add("actionbar.default", "&6Example Actionbar");
        return messages;
    }

    @Override
    public void initialize() {
        this.actionbarHandler = new ActionbarHandler(this);
        getLifecycleManager().getCommandManager().registerFeatureCommand(new ActionbarCommand(this));
        getLifecycleManager().getCommandManager().registerBrigadierCommand(new ActionbarBrigadierCommand(this));
        actionbarHandler.startMessageCycle();
    }

    @Override
    public void disable() {
    }

    public ActionbarHandler getActionbarHandler() {
        return actionbarHandler;
    }
}
