package nl.hauntedmc.serverfeatures.features.parcour;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.parcour.command.ParcourCommand;
import nl.hauntedmc.serverfeatures.features.parcour.internal.ParcourHandler;
import nl.hauntedmc.serverfeatures.features.parcour.listener.*;
import nl.hauntedmc.serverfeatures.features.parcour.meta.Meta;
import nl.hauntedmc.serverfeatures.features.parcour.registry.ParcourRegistry;

public final class Parcour extends BukkitBaseFeature<Meta> {

    private ParcourRegistry registry;
    private ParcourHandler handler;

    public Parcour(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);
        cfg.put("parcours", new java.util.LinkedHashMap<String, Object>());
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();
        m.add("parcour.starting", "&aParcour &f{name} &agestart! Succes!");
        m.add("parcour.started_teleport", "&7Je bent naar het startpunt geteleporteerd.");
        m.add("parcour.checkpoint.set", "&bJe hebt een nieuw checkpoint gehaald!");
        m.add("parcour.checkpoint.teleport", "&7Teruggezet naar je laatste checkpoint.");
        m.add("parcour.finished", "&aGefeliciteerd! Je voltooide &f{name} &ain &f{seconds}s&a.");
        m.add("parcour.already_playing", "&cJe speelt al een parcour: &f{name}&c. Gebruik &f/parcour leave&c om te stoppen.");
        m.add("parcour.not_playing", "&cJe speelt momenteel geen parcour.");
        m.add("parcour.not_found", "&cParcour &f{name} &cbestaat niet.");
        m.add("parcour.left", "&7Je hebt &f{name} &7verlaten.");
        m.add("parcour.no_checkpoint", "&cGeen checkpoint beschikbaar, je gaat terug naar de start.");
        m.add("parcour.cannot_start_missing", "&cParcour &f{name} &cis onjuist geconfigureerd (mist START en/of END).");
        m.add("parcour.progress", "&7Je maakt progressie! Je bent bij tussenpunt &f{current}&7/&f{total}");
        m.add("parcour.actionbar", "&6{seconds}s &7• &eProgressie &f{current}&7/&f{total}");
        m.add("parcour.admin.created", "&aParcour &f{id} &aaangemaakt.");
        m.add("parcour.admin.deleted", "&aParcour &f{id} &averwijderd.");
        m.add("parcour.admin.exists", "&cParcour &f{id} &cbestaat al.");
        m.add("parcour.admin.region.deleted", "&aRegio verwijderd: &f{type} &8(key {order})");
        m.add("parcour.admin.region.missing", "&cRegio ontbreekt: selecteer eerst een WorldEdit regio (&f//wand&c, linker/rechter klik).");
        m.add("parcour.admin.region.world_mismatch", "&cPos1 en Pos2 moeten in dezelfde wereld liggen.");
        m.add("parcour.admin.region.not_found", "&cGeen regio met sleutel &f{order}&c (gebruik &fSTART&c, &fEND&c of een nummer).");
        m.add("parcour.admin.region.added", "&aRegio toegevoegd: &f{type} &8(key {order}) &7restore={restore}");
        m.add("parcour.admin.region.added_no_restore", "&aRegio toegevoegd: &f{type} &8(key {order})");
        m.add("parcour.admin.restore.set", "&aRestore checkpoint voor sleutel &f{order} &agezet op &f{restore}");
        m.add("parcour.admin.leave.set", "&aLeave locatie gezet: &7{world} {x} {y} {z} {yaw} {pitch}");
        m.add("parcour.admin.finish.set", "&aFinish locatie gezet: &7{world} {x} {y} {z} {yaw} {pitch}");
        m.add("parcour.admin.cmd.added", "&aCommand toegevoegd aan sleutel &f{order}&a: &7/{cmd}");
        m.add("parcour.admin.cmd.cleared", "&aCommands leeggemaakt voor sleutel &f{order}&a.");
        m.add("parcour.admin.info.header", "&6&lParcour info &7({id})");
        m.add("parcour.admin.info.prop", "&7- &f{key}&7: &f{value}");
        m.add("parcour.admin.list.header", "&6&lParcours &7({count})");
        m.add("parcour.admin.list.entry", "&7- &f{id} &8[&7regions=&f{regions}&8]");
        m.add("parcour.admin.restoreloc.set", "&aRestore locatie gezet voor sleutel &f{order}&a: &7{world} {x} {y} {z} {yaw} {pitch}");
        m.add("parcour.admin.restoreloc.not_applicable", "&cRestore locatie kan niet gezet worden voor sleutel &f{order}&c.");
        m.add("parcour.admin.progress.notify.set", "&aProgress-notificatie voor &f{id} &agingesteld op &f{value}");
        m.add("parcour.admin.sound.set", "&aSound voor &f{type}&a ingesteld op &f{sound}&a.");
        m.add("parcour.admin.sound.cleared", "&aSound voor &f{type}&a verwijderd.");
        m.add("parcour.admin.sound.invalid", "&cOngeldige sound: &f{sound}&c.");
        m.add("parcour.admin.sound.invalid_type", "&cOngeldig type: &f{type}&c. Gebruik CHECKPOINT of END.");
        m.add("parcour.admin.actionbar.set", "&aActionbar-weergave voor &f{id}&a ingesteld op &f{value}&a.");
        m.add("parcour.admin.finishdelay.set", "&aFinish-teleport voor &f{id}&a ingesteld op &f{seconds}&as.");
        m.add("parcour.item.leave.name", "&cParcour verlaten");
        m.add("parcour.item.leave.lore", "&7Rechtsklik om het parcour te verlaten.");
        m.add("parcour.item.checkpoint.name", "&eNaar checkpoint");
        m.add("parcour.item.checkpoint.lore", "&7Rechtsklik om terug te keren naar je laatste checkpoint.");
        m.add("parcour.admin.hunger.set", "&aHonger voor &f{id}&a ingesteld op &f{value}&a.");
        m.add("parcour.admin.damage.set", "&aSchade voor &f{id}&a ingesteld op &f{value}&a.");
        m.add("parcour.checkpoint.cooldown", "&cWacht nog &f{seconds}s &cvoordat je terug naar je checkpoint kunt.");
        m.add("parcour.admin.checkpointcooldown.set", "&aCheckpoint-cooldown voor &f{id} &agingesteld op &f{seconds}&as.");
        m.add("parcour.admin.startkit.added", "&aStartkit-item toegevoegd aan &f{id}&a: &7{item}");
        m.add("parcour.admin.startkit.cleared", "&aStartkit leeggemaakt voor &f{id}&a.");
        m.add("parcour.admin.startkit.removed", "&aStartkit-item &f#{index}&a verwijderd voor &f{id}&a.");
        m.add("parcour.admin.startkit.list.header", "&6&lStartkit &7({id})");
        m.add("parcour.admin.startkit.list.entry", "&7- &f#{index}&7: &f{item}");
        m.add("parcour.admin.startcountdown.set", "&aStart-countdown voor &f{id}&a ingesteld op &f{seconds}&as.");
        m.add("parcour.admin.startpos.set", "&aStartpositie gezet: &7{world} {x} {y} {z} {yaw} {pitch}");
        m.add("parcour.admin.startpos.cleared", "&aStartpositie verwijderd.");
        m.add("parcour.countdown.go", "&aStart!");
        m.add("parcour.countdown.blocked", "&cJe kunt dit nog niet: de start telt nog af.");

        return m;
    }

    @Override
    public void initialize() {
        this.registry = new ParcourRegistry(this);
        this.registry.reloadFromConfig();
        this.handler = new ParcourHandler(this, registry);

        getLifecycleManager().getListenerManager().registerListener(new ParcourListener(handler));
        getLifecycleManager().getListenerManager().registerListener(new ParcourDeathVoidListener(this, handler));
        getLifecycleManager().getListenerManager().registerListener(new ParcourItemListener(handler));
        getLifecycleManager().getListenerManager().registerListener(new ParcourProtectionListener(this, handler));
        getLifecycleManager().getListenerManager().registerListener(new ParcourQuitListener(handler));
        getLifecycleManager().getCommandManager().registerBrigadierCommand(new ParcourCommand(this, handler));

        getLogger().info("Initialized. Loaded " + registry.size() + " parcour(s).");
    }

    @Override
    public void disable() {
        if (handler != null) {
            int restored = handler.restoreAllAndClearSessions();
            getLogger().info("Parcour disabled: restored " + restored + " active player session(s).");
        }
    }

    public ParcourRegistry getRegistry() {
        return registry;
    }

    public ParcourHandler getHandler() {
        return handler;
    }
}
