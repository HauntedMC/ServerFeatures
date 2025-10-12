package nl.hauntedmc.serverfeatures.features.portals;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.portals.command.PortalsCommand;
import nl.hauntedmc.serverfeatures.features.portals.internal.PortalsHandler;
import nl.hauntedmc.serverfeatures.features.portals.listener.PortalOverrideListener;
import nl.hauntedmc.serverfeatures.features.portals.listener.PortalsListener;
import nl.hauntedmc.serverfeatures.features.portals.listener.WandListener;
import nl.hauntedmc.serverfeatures.features.portals.meta.Meta;
import nl.hauntedmc.serverfeatures.features.portals.registry.PortalRegistry;

public class Portals extends BukkitBaseFeature<Meta> {

    private PortalRegistry registry;
    private PortalsHandler handler;

    public Portals(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);
        cfg.put("portals", new java.util.LinkedHashMap<String, Object>());
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();

        m.add("portals.created", "&aPortal &f{id} &aaangemaakt.");
        m.add("portals.already_exists", "&cPortal &f{id} &cbestaat al.");
        m.add("portals.not_found", "&cPortal &f{id} &cniet gevonden.");
        m.add("portals.deleted", "&aPortal &f{id} &averwijderd.");
        m.add("portals.select.current", "&7Geselecteerde portal: &f{id}");
        m.add("portals.select.none", "&cJe hebt geen portal geselecteerd. Gebruik: &f/portals select <id>");
        m.add("portals.pos1.set", "&aPos1 gezet op &7{world} {x} {y} {z}");
        m.add("portals.pos2.set", "&aPos2 gezet op &7{world} {x} {y} {z}");
        m.add("portals.region.saved", "&aRegion opgeslagen voor &f{id}");
        m.add("portals.region.missing", "&cRegion ontbreekt: zet &f/portals pos1 &cen &f/portals pos2&c.");
        m.add("portals.region.world_mismatch", "&cPos1 en Pos2 moeten in dezelfde wereld liggen.");
        m.add("portals.mode.set", "&aMode van &f{id} &agezet op &f{mode}");
        m.add("portals.teleport.set", "&aTeleport-bestemming gezet: &7{world} {x} {y} {z} {yaw} {pitch}");
        m.add("portals.command.set", "&aCommand gezet: &7\"{command}\" &8(as: {executor})");
        m.add("portals.list.header", "&6&lPortals &7({count})");
        m.add("portals.list.entry", "&7- &f{id} &8[&7mode=&f{mode}&8, &7world=&f{world}&8]");
        m.add("portals.wand.given", "&aJe hebt de &fPortals Wand &agekregen. Selecteer 2 punten met &fLinker (&7Pos1&f) &aen &fRechter (&7Pos2&f) &amuis.");
        m.add("portals.wand.select_first", "&cSelecteer eerst een portal: &f/portals select <id>");
        m.add("portals.wand.must_hold", "&cHoud de &fPortals Wand &cin je hand om punten te selecteren.");
        m.add("portals.wand.block_click_denied", "&cJe kunt dit blok niet gebruiken terwijl je de &fPortals Wand &choudt.");
        m.add("portals.server.set", "&aServer-bestemming voor &f{id} &agezet op &f{server}");
        m.add("portals.server.missing", "&cGeen servernaam ingesteld voor deze portal.");
        m.add("portals.block.set", "&aExclusieve portal-block voor &f{id} &agezet op &f{block}");
        m.add("portals.block.cleared", "&aExclusieve portal-block voor &f{id} &aopgeheven.");
        m.add("portals.block.invalid", "&cOngeldige blocknaam: &f{block}&c. Gebruik een plaatsbaar block.");
        m.add("portals.sound.set", "&aGeluid voor &f{id} &agezet op &f{sound} &8(delay: {delay} ticks)");
        m.add("portals.sound.cleared", "&aGeluid voor &f{id} &aopgeheven.");
        m.add("portals.sound.invalid", "&cOngeldig geluid: &f{sound}&c.");
        m.add("portals.particle.set", "&aParticle voor &f{id} &agezet op &f{particle} &8(delay: {delay} ticks)");
        m.add("portals.particle.cleared", "&aParticle voor &f{id} &aopgeheven.");
        m.add("portals.particle.invalid", "&cOngeldige particle: &f{particle}&c.");
        m.add("portals.info.header", "&6&lPortal info &7({id})");
        m.add("portals.info.prop", "&7- &f{key}&7: &f{value}");
        return m;
    }

    @Override
    public void initialize() {
        this.registry = new PortalRegistry(this);
        this.registry.reloadFromConfig();
        this.handler  = new PortalsHandler(this, registry);
        getLifecycleManager().getListenerManager().registerListener(new PortalsListener(handler));
        getLifecycleManager().getListenerManager().registerListener(new WandListener(this, handler));
        getLifecycleManager().getListenerManager().registerListener(new PortalOverrideListener(this, handler));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new PortalsCommand(this, handler));
        getPlugin().getServer().getMessenger().registerOutgoingPluginChannel(getPlugin(), "BungeeCord");
        getLogger().info("Initialized. Loaded " + registry.size() + " portal(s).");
    }

    @Override
    public void disable() {
        getPlugin().getServer().getMessenger().unregisterOutgoingPluginChannel(getPlugin(), "BungeeCord");
    }

    public PortalRegistry getRegistry() { return registry; }
    public PortalsHandler getHandler() { return handler; }
}
