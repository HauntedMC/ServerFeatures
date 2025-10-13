package nl.hauntedmc.serverfeatures.features.joinitems;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.joinitems.internal.JoinItemsHandler;
import nl.hauntedmc.serverfeatures.features.joinitems.listener.JoinItemsListener;
import nl.hauntedmc.serverfeatures.features.joinitems.meta.Meta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JoinItems feature:
 *  - Gives configured items at fixed inventory slots after join (with a delay).
 *  - Items are tagged via PDC for robust identification.
 *  - Enforces immovable/undroppable/etc. protections and executes commands on click.
 */
public final class JoinItems extends BukkitBaseFeature<Meta> {

    private JoinItemsHandler handler;

    public JoinItems(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);
        cfg.put("include-all-items", false);
        cfg.put("remove-on-join", true);
        cfg.put("remove-on-leave", true);
        cfg.put("join-delay", 2); // ticks


        Map<String, Object> items = new LinkedHashMap<>();

        Map<String, Object> cosmetic = new LinkedHashMap<>();
        cosmetic.put("material", "END_CRYSTAL");
        cosmetic.put("slot", 4);
        cosmetic.put("name", "&d&lCosmetics");
        cosmetic.put("command", List.of("cosmetics"));
        cosmetic.put("lore", List.of("&7Klik om het menu te openen."));
        cosmetic.put("locked", true);
        cosmetic.put("unmovable", true);
        cosmetic.put("undroppable", true);
        items.put("cosmetic-item", cosmetic);

        Map<String, Object> server = new LinkedHashMap<>();
        server.put("material", "COMPASS");
        server.put("slot", 0);
        server.put("name", "&b&lHaunted&6&lMC &e&lMenu");
        server.put("command", List.of("menu"));
        server.put("lore", List.of("&7Klik om het menu te openen."));
        server.put("locked", true);
        server.put("unmovable", true);
        server.put("undroppable", true);
        items.put("server-item", server);

        Map<String, Object> lobby = new LinkedHashMap<>();
        lobby.put("material", "NETHER_STAR");
        lobby.put("slot", 8);
        lobby.put("name", "&e&lLobby Selector");
        lobby.put("command", List.of("lobbymenu"));
        lobby.put("lore", List.of("&7Klik om het menu te openen."));
        lobby.put("locked", true);
        lobby.put("unmovable", true);
        lobby.put("undroppable", true);
        items.put("lobby-item", lobby);

        cfg.put("items", items);

        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        this.handler = new JoinItemsHandler(this);
        this.handler.reloadFromConfig();
        getLifecycleManager().getListenerManager().registerListener(new JoinItemsListener(this, handler));
    }

    @Override
    public void disable() {
    }

    public JoinItemsHandler getHandler() {
        return handler;
    }
}
