package nl.hauntedmc.serverfeatures.features.invtools;

import de.tr7zw.changeme.nbtapi.NBT;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.invtools.command.InvToolsCommand;
import nl.hauntedmc.serverfeatures.features.invtools.listener.InvToolsListener;
import nl.hauntedmc.serverfeatures.features.invtools.meta.Meta;
import nl.hauntedmc.serverfeatures.features.invtools.service.InvToolsService;

public final class InvTools extends BukkitBaseFeature<Meta> {

    private InvToolsService service;

    public InvTools(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        // Feature opt-in: disabled by default because this can inspect and mutate player data.
        defaults.put("enabled", false);
        // Tick interval for refreshing online target views. Lower values are more responsive but
        // capture inventories more often; the service always revalidates a slot before editing it.
        defaults.put("online_sync_interval_ticks", 5);
        // Maximum time an async pre-login thread may wait for a pending offline save or clear.
        // InvTools clamps this to 1–30 seconds to avoid holding Paper's login threads indefinitely.
        defaults.put("offline_io_timeout_seconds", 10);
        // Concurrent offline opens and clears. This bounds playerdata disk work and fails excess
        // requests fast instead of queuing them on Paper's shared asynchronous workers.
        defaults.put("max_offline_sessions", 4);
        // Emits structured audit entries for every edit and clear, including offline save outcomes.
        defaults.put("audit_edits", true);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("invtools.invalid_name", "&cDe spelersnaam &e{player}&c is ongeldig.");
        messages.add("invtools.not_played_here",
                "&cGeen lokale spelerdata gevonden voor &e{player}&c op deze server.");
        messages.add("invtools.loading", "&7Offline spelerdata van &f{player}&7 wordt veilig geladen...");
        messages.add("invtools.busy",
                "&eHet maximum aantal offline inventarisacties is bereikt. Probeer het zo opnieuw.");
        messages.add("invtools.load_failed",
                "&cDe offline spelerdata van &e{player}&c kon niet veilig worden geladen; er is niets gewijzigd.");
        messages.add("invtools.save_failed",
                "&cWijzigingen voor &e{player}&c zijn niet opgeslagen; de oorspronkelijke spelerdata is behouden.");
        messages.add("invtools.offline_saved",
                "&aDe offline wijzigingen voor &e{player}&a zijn veilig opgeslagen.");
        messages.add("invtools.opened_inspect", "&7Je bekijkt de opslag van &f{player}&7 alleen-lezen.");
        messages.add("invtools.opened_edit", "&cJe bewerkt nu de opslag van &e{player}&c.");
        messages.add("invtools.read_only", "&cDeze weergave is alleen-lezen; je mag deze opslag niet bewerken.");
        messages.add("invtools.permission_revoked",
                "&cJe InvTools-weergave is gesloten omdat je kijktoegang is ingetrokken.");
        messages.add("invtools.edit_permission_revoked",
                "&cJe InvTools-weergave is gesloten omdat je bewerkingstoegang is ingetrokken.");
        messages.add("invtools.cursor_not_empty",
                "&cMaak eerst je cursor leeg voordat je offline spelerdata bewerkt.");
        messages.add("invtools.open_failed",
                "&cDe opslag van &e{player}&c kon niet veilig worden geopend.");
        messages.add("invtools.save_conflict",
                "&cWijzigingen voor &e{player}&c zijn niet opgeslagen omdat de spelerdata ondertussen veranderde.");
        messages.add("invtools.self", "&cJe kunt je eigen opslag niet via /inv openen.");
        messages.add("invtools.already_open",
                "&cDe offline spelerdata van &e{player}&c wordt al door een andere staffactie gebruikt.");
        messages.add("invtools.already_editing",
                "&eDe opslag van {player} wordt al bewerkt; jouw weergave is daarom alleen-lezen.");
        messages.add("invtools.target_went_offline",
                "&e{player}&c is offline gegaan; de live weergave is gesloten.");
        messages.add("invtools.target_logging_in",
                "&e{player}&c logt in; de offline weergave is gesloten om spelerdata te beschermen.");
        messages.add("invtools.join_conflict_discarded",
                "&cNiet-opgeslagen wijzigingen voor &e{player}&c zijn verworpen omdat de speler tijdens het openen inlogde.");
        messages.add("invtools.login_retry",
                "&cJe spelerdata wordt nog veilig afgerond. Probeer over enkele seconden opnieuw.");
        messages.add("invtools.clearing", "&7De offline opslag van &f{player}&7 wordt veilig geleegd...");
        messages.add("invtools.cleared", "&aDe geselecteerde opslag van &e{player}&a is veilig geleegd.");
        messages.add("invtools.clear_failed",
                "&cDe opslag van &e{player}&c kon niet veilig worden geleegd; er is niets gewijzigd.");
        messages.add("invtools.clear_conflict",
                "&cDe opslag van &e{player}&c is niet geleegd omdat de spelerdata ondertussen veranderde.");
        messages.add("invtools.clear_cancelled",
                "&eHet legen van de opslag van &f{player}&e is geannuleerd omdat de speler inlogde.");
        messages.add("invtools.clear_editing",
                "&cDe opslag van &e{player}&c wordt nog bewerkt; sluit die sessie eerst.");
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

        getLifecycleManager().getCommandManager().registerBrigadierCommand(new InvToolsCommand(this));
        getLifecycleManager().getListenerManager()
                .registerListener(new InvToolsListener(this, service));

        int interval = Math.max(1, getConfigHandler().get(
                "online_sync_interval_ticks",
                Integer.class,
                5
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
