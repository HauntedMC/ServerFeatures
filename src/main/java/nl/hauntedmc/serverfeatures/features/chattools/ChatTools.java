package nl.hauntedmc.serverfeatures.features.chattools;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.chattools.command.ChatCommand;
import nl.hauntedmc.serverfeatures.features.chattools.listener.ChatListener;
import nl.hauntedmc.serverfeatures.features.chattools.meta.Meta;

public class ChatTools extends BukkitBaseFeature<Meta> {

    private volatile boolean chatLocked = false;

    public boolean isChatLocked()               { return chatLocked; }
    public void    setChatLocked(boolean lock)  { chatLocked = lock; }

    public ChatTools(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);
        cfg.put("clear_lines", 150); // how many blank lines to send
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();
        m.add("chattools.usage",            "&eGebruik: /chat <lock|unlock|clear>");
        m.add("chattools.already_locked",   "&cDe chat is al vergrendeld.");
        m.add("chattools.not_locked",       "&cDe chat is niet vergrendeld.");
        m.add("chattools.locked_broadcast", "&eDe chat is &cvergrendeld&e door een staff‑lid.");
        m.add("chattools.unlocked_broadcast","&eDe chat is weer &aontgrendeld&e.");
        m.add("chattools.cleared_broadcast","&eDe chat is geleegd door een staff‑lid.");
        m.add("chattools.locked_cant_chat", "&cJe kunt momenteel niet chatten: de chat is vergrendeld.");
        return m;
    }

    /* ----------------------------------------------- */
    /*  Lifecycle                                      */
    /* ----------------------------------------------- */
    @Override
    public void initialize() {
        getLifecycleManager().getCommandManager()
                .registerFeatureCommand(new ChatCommand(this));
        getLifecycleManager().getListenerManager()
                .registerListener(new ChatListener(this));
    }
    @Override public void disable() {
    }
}
