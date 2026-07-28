package nl.hauntedmc.serverfeatures.features.invtools;

import de.tr7zw.changeme.nbtapi.NBT;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.invtools.command.InventorySeeCommand;
import nl.hauntedmc.serverfeatures.features.invtools.listener.InvToolsListener;
import nl.hauntedmc.serverfeatures.features.invtools.meta.Meta;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.service.InvToolsService;

public final class InvTools extends BukkitBaseFeature<Meta> {

    private InvToolsService service;

    public InvTools(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("online_sync_interval_ticks", 2);
        defaults.put("offline_io_timeout_seconds", 10);
        defaults.put("audit_edits", true);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("invtools.usage.invsee", "&eGebruik: /invsee <naam>");
        messages.add("invtools.usage.endersee", "&eGebruik: /endersee <naam>");
        messages.add("invtools.invalid_name", "&cOngeldige spelersnaam: &e{player}&c.");
        messages.add("invtools.not_played_here",
                "&cSpeler &e{player}&c heeft nog geen spelerdata op deze server.");
        messages.add("invtools.loading", "&7Offline inventaris van &f{player}&7 wordt geladen...");
        messages.add("invtools.load_failed",
                "&cDe offline inventaris van &e{player}&c kon niet veilig worden geladen.");
        messages.add("invtools.save_failed",
                "&cWijzigingen voor &e{player}&c zijn niet opgeslagen omdat de spelerdata veranderde of een fout optrad.");
        messages.add("invtools.offline_saved",
                "&aOffline wijzigingen voor &e{player}&a zijn veilig opgeslagen.");
        messages.add("invtools.opened_inspect", "&7Je inspecteert nu &f{player}&7 (alleen-lezen).");
        messages.add("invtools.opened_edit", "&cJe bewerkt nu de inventaris van &e{player}&c.");
        messages.add("invtools.read_only", "&cJe hebt geen toestemming om deze inventaris te bewerken.");
        messages.add("invtools.permission_revoked",
                "&cJe InvTools-weergave is gesloten omdat je inspectietoegang is ingetrokken.");
        messages.add("invtools.edit_permission_revoked",
                "&cJe InvTools-weergave is gesloten omdat je bewerkingstoegang is ingetrokken.");
        messages.add("invtools.cursor_not_empty",
                "&cMaak eerst het item op je cursor vrij voordat je offline spelerdata opent.");
        messages.add("invtools.open_failed",
                "&cDe inventaris van &e{player}&c kon niet veilig worden geopend.");
        messages.add("invtools.save_conflict",
                "&cWijzigingen voor &e{player}&c zijn niet opgeslagen omdat de spelerdata ondertussen veranderde.");
        messages.add("invtools.self", "&cGebruik je normale inventaris om je eigen items te bekijken.");
        messages.add("invtools.already_open",
                "&cDe offline inventaris van &e{player}&c wordt al door een ander stafflid gebruikt.");
        messages.add("invtools.already_editing",
                "&eDe inventaris van {player} wordt al bewerkt; je weergave is daarom alleen-lezen.");
        messages.add("invtools.target_went_offline",
                "&e{player}&c ging offline; de live weergave is veilig gesloten.");
        messages.add("invtools.target_logging_in",
                "&e{player}&c logt in; de offline weergave is veilig gesloten.");
        messages.add("invtools.join_conflict_discarded",
                "&cNiet-opgeslagen wijzigingen voor &e{player}&c zijn vervallen omdat de speler tijdens het openen inlogde.");
        messages.add("invtools.outdated_data_read_only",
                "&eDe spelerdata-versie van {player} wordt niet veilig ondersteund. Laat de speler eenmaal inloggen op deze serverversie voordat je offline wijzigingen maakt.");
        messages.add("invtools.login_retry",
                "&cJe spelerdata werd zojuist door staff bijgewerkt. Probeer over enkele seconden opnieuw.");
        return messages;
    }

    @Override
    public void initialize() {
        if (!NBT.preloadApi()) {
            throw new IllegalStateException(
                    "The bundled Item-NBT-API is not compatible with this server version."
            );
        }
        service = new InvToolsService(this);

        getLifecycleManager().getCommandManager()
                .registerFeatureCommand(new InventorySeeCommand(this, InventoryKind.PLAYER));
        getLifecycleManager().getCommandManager()
                .registerFeatureCommand(new InventorySeeCommand(this, InventoryKind.ENDER_CHEST));
        getLifecycleManager().getListenerManager()
                .registerListener(new InvToolsListener(this, service));

        int interval = Math.max(1, getConfigHandler().get(
                "online_sync_interval_ticks",
                Integer.class,
                2
        ));
        getLifecycleManager().getTaskManager().scheduleRepeatingTask(
                service::refreshOnlineViews,
                BukkitTime.ticks(interval),
                BukkitTime.ticks(interval)
        );
    }

    @Override
    public void disable() {
        if (service != null) {
            service.shutdown();
        }
    }

    public InvToolsService getService() {
        return service;
    }
}
