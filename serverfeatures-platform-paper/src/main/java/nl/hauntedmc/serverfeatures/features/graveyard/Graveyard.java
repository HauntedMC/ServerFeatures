package nl.hauntedmc.serverfeatures.features.graveyard;

import com.github.retrooper.packetevents.PacketEvents;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.serverfeatures.api.graveyard.GraveyardService;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.graveyard.capture.GraveCaptureService;
import nl.hauntedmc.serverfeatures.features.graveyard.capture.GraveDeathListener;
import nl.hauntedmc.serverfeatures.features.graveyard.command.GraveCommand;
import nl.hauntedmc.serverfeatures.features.graveyard.config.GraveyardSettings;
import nl.hauntedmc.serverfeatures.features.graveyard.journal.GraveOperationJournal;
import nl.hauntedmc.serverfeatures.features.graveyard.journal.PlayerOperationReceiptService;
import nl.hauntedmc.serverfeatures.features.graveyard.listener.GravePlayerLifecycleListener;
import nl.hauntedmc.serverfeatures.features.graveyard.meta.Meta;
import nl.hauntedmc.serverfeatures.features.graveyard.packet.GraveInteractionPacketListener;
import nl.hauntedmc.serverfeatures.features.graveyard.persistence.GraveAuditEntity;
import nl.hauntedmc.serverfeatures.features.graveyard.persistence.GraveLeaseEntity;
import nl.hauntedmc.serverfeatures.features.graveyard.persistence.GraveMetadataEntity;
import nl.hauntedmc.serverfeatures.features.graveyard.persistence.GravePayloadCodec;
import nl.hauntedmc.serverfeatures.features.graveyard.persistence.GravePayloadEntity;
import nl.hauntedmc.serverfeatures.features.graveyard.persistence.GraveRepository;
import nl.hauntedmc.serverfeatures.features.graveyard.placement.GravePlacementService;
import nl.hauntedmc.serverfeatures.features.graveyard.placement.LastSafeLocationTracker;
import nl.hauntedmc.serverfeatures.features.graveyard.runtime.GraveExpiryNotifier;
import nl.hauntedmc.serverfeatures.features.graveyard.runtime.GraveManager;
import nl.hauntedmc.serverfeatures.framework.time.ServerActiveClock;

import java.io.IOException;
import java.util.List;

public final class Graveyard extends BukkitBaseFeature<Meta> {
    private static final String DATABASE_IDENTIFIER = "graveyardOrm";

    private GraveyardSettings settings;
    private ORMContext ormContext;
    private GraveRepository repository;
    private GraveOperationJournal journal;
    private GravePayloadCodec payloadCodec;
    private PlayerOperationReceiptService receiptService;
    private LastSafeLocationTracker safeLocationTracker;
    private GravePlacementService placementService;
    private GraveManager manager;
    private GraveExpiryNotifier expiryNotifier;
    private GraveInteractionPacketListener packetListener;
    private ServerActiveClock activeClock;

    public Graveyard(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("mode", "ACTIVE");
        defaults.put("identity.server_id", "");
        defaults.put("identity.inventory_scope", "");
        defaults.put("identity.lease_heartbeat", "5s");
        defaults.put("identity.lease_timeout", "20s");
        defaults.put("storage.journal.maximum_record_bytes", 12_000_000);
        defaults.put("storage.payload.maximum_entries", 64);
        defaults.put("storage.payload.maximum_item_bytes", 2_097_152);
        defaults.put("storage.payload.maximum_total_bytes", 8_388_608);
        defaults.put("storage.retention.expired", "1h");
        defaults.put("storage.retention.claimed", "1h");
        defaults.put("storage.retention.purge_interval", "10m");
        defaults.put("storage.retention.purge_batch_size", 100);
        defaults.put("lifetime.duration", "10m");
        defaults.put("eligibility.disabled_worlds", List.of());
        defaults.put("eligibility.disabled_gamemodes", List.of("CREATIVE", "SPECTATOR"));
        defaults.put("experience.mode", "NATIVE");
        defaults.put("experience.recovery_percentage", 50);
        defaults.put("placement.horizontal_search_radius", 8);
        defaults.put("placement.vertical_search_below", 4);
        defaults.put("placement.vertical_search_above", 6);
        defaults.put("placement.last_safe_location_max_age", "30s");
        defaults.put("render.spawn_distance", 48.0);
        defaults.put("render.despawn_distance", 56.0);
        defaults.put("render.reconciliation_interval_ticks", 20L);
        defaults.put("render.spawn_settle_delay_ticks", 2L);
        defaults.put("render.max_rendered_per_viewer", 64);
        defaults.put("render.base.material", "POLISHED_BLACKSTONE_BRICK_SLAB");
        defaults.put("render.headstone.material", "DARK_OAK_PLANKS");
        defaults.put("render.glow.owner_rgb", "55FFFF");
        defaults.put("render.glow.other_rgb", "00AAAA");
        defaults.put("render.glow.staff_rgb", "FFD700");
        defaults.put("interaction.maximum_distance", 4.5);
        defaults.put("interaction.require_line_of_sight", true);
        defaults.put("claim.partial_claims", true);
        defaults.put("particles.claim.type", "SCULK_SOUL");
        defaults.put("particles.expiry.type", "SCULK_SOUL");
        defaults.put("sounds.claim.sound", "BLOCK_RESPAWN_ANCHOR_CHARGE");
        defaults.put("sounds.expiry.sound", "PARTICLE_SOUL_ESCAPE");
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add(
                "graveyard.created",
                "&7Je graf &b{grave_id}&7 staat op &f{x}, {y}, {z}&7. Gebruik &f/grave locate {grave_id}&7."
        );
        messages.add(
                "graveyard.created_remote",
                "&7Je graf &b{grave_id}&7 kon niet veilig geplaatst worden. Gebruik &f/grave claim {grave_id}&7."
        );
        messages.add(
                "graveyard.storage_failed_vanilla_fallback",
                "&cJe graf kon niet veilig worden opgeslagen; de normale death drops zijn gebruikt."
        );
        messages.add(
                "graveyard.capture_recovery_pending",
                "&eGraf {grave_id} wordt veilig hersteld na een opslagfout. Je items worden niet dubbel uitgegeven."
        );
        messages.add(
                "graveyard.claim_recovery_pending",
                "&eDe claim van graf {grave_id} is opgeslagen en wordt veilig afgerond."
        );
        messages.add(
                "graveyard.expiry_warning",
                "&eJe graf &b{grave_id}&e verdwijnt binnen &f{seconds} seconden&e."
        );
        messages.add("graveyard.expired", "&cJe graf &b{grave_id}&c is verlopen en verdwenen.");
        messages.add("graveyard.keep_inventory", "&aJe inventory is behouden; er is geen graf gemaakt.");
        messages.add("graveyard.claimed", "&aGraf {grave_id} is volledig teruggehaald.");
        messages.add(
                "graveyard.partially_claimed",
                "&eGraf {grave_id} is gedeeltelijk teruggehaald; {remaining} itemstacks blijven over."
        );
        messages.add("graveyard.inventory_full", "&cJe inventory heeft onvoldoende ruimte voor graf {grave_id}.");
        messages.add("graveyard.not_owner", "&cDit graf is van {player}.");
        messages.add(
                "graveyard.staff_use_deliver",
                "&eGraf {grave_id} is van {player}. Gebruik /grave admin deliver {grave_id}."
        );
        messages.add("graveyard.not_found", "&cDat graf bestaat niet of is niet meer beschikbaar.");
        messages.add("graveyard.no_graves", "&7Je hebt geen actieve graven.");
        messages.add("graveyard.list_header", "&8--- &bJouw graven &8---");
        messages.add("graveyard.admin_list_header", "&8--- &bGraven van {player} &8---");
        messages.add("graveyard.admin_list_empty", "&7Geen bekende herstelbare graven voor deze speler.");
        messages.add(
                "graveyard.list_entry",
                "&b{grave_id} &7- {state} &7- &f{world} {x}, {y}, {z} &7- {remaining}"
        );
        messages.add(
                "graveyard.info",
                "&b{grave_id}&7: eigenaar=&f{player}&7, status={state}&7, wereld=&f{world}&7, "
                        + "locatie=&f{x}, {y}, {z}&7, items=&f{items}&7, xp=&f{xp}&7, tijd={remaining}"
        );
        messages.add("graveyard.locate", "&7Graf &b{grave_id}&7 staat op &f{world} {x}, {y}, {z}&7.");
        messages.add("graveyard.track_started", "&aJe volgt nu graf {grave_id}.");
        messages.add("graveyard.track_stopped", "&7Graftracking is gestopt.");
        messages.add("graveyard.remote_claim_unavailable", "&cDit graf moet op locatie worden aangeklikt.");
        messages.add("graveyard.admin_success", "&aGraveyard actie uitgevoerd voor {grave_id}.");
        messages.add("graveyard.admin_failed", "&cGraveyard actie kon niet worden uitgevoerd voor {grave_id}.");
        messages.add("graveyard.admin_diagnostics", "&7Graveyard diagnostics: &f{details}");
        messages.add("graveyard.usage", "&7Gebruik: /grave [list|info|locate|track|claim|admin]");

        messages.add("graveyard.hologram.title", "&7Graf van &f{player}");
        messages.add("graveyard.timer.remaining", "&7Verdwijnt over {remaining}");
        messages.add("graveyard.timer.delivery_pending", "&eBezorging in afwachting");
        messages.add("graveyard.timer.remote_recovery", "&eHerstel op afstand beschikbaar");
        messages.add(
                "graveyard.tracking.same_world",
                "&bGraf {grave_id} &8· &b{distance}m &8· {timer}"
        );
        messages.add(
                "graveyard.tracking.other_world",
                "&bGraf {grave_id} &8· &b{world} &8· {timer}"
        );
        messages.add("graveyard.duration.hours_minutes", "{hours}u {minutes}m");
        messages.add("graveyard.duration.minutes_seconds", "{minutes}m {seconds}s");

        messages.add("graveyard.status.active", "&aactief");
        messages.add("graveyard.status.partial", "&egegedeeltelijk");
        messages.add("graveyard.status.orphaned_world", "&eoffline wereld");
        messages.add("graveyard.status.delivery_pending", "&ebezorging in afwachting");
        messages.add("graveyard.status.claimed", "&ageclaimd");
        messages.add("graveyard.status.expired", "&cverlopen");
        messages.add("graveyard.status.corrupt", "&cbeschadigd");
        messages.add("graveyard.status.admin_recovered", "&adoor beheer hersteld");
        messages.add("graveyard.status.purged", "&8verwijderd");
        return messages;
    }

    @Override
    public void initialize() {
        settings = GraveyardSettings.load(this);
        activeClock = new ServerActiveClock(getPlugin());
        activeClock.start();
        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());
        getLifecycleManager().getDataManager().registerConnection(
                DATABASE_IDENTIFIER,
                DatabaseType.MYSQL,
                "player_data_rw"
        );
        ormContext = getLifecycleManager().getDataManager().createORMContext(
                DATABASE_IDENTIFIER,
                GraveMetadataEntity.class,
                GravePayloadEntity.class,
                GraveAuditEntity.class,
                GraveLeaseEntity.class
        ).orElseThrow(() -> new IllegalStateException("Could not initialize the Graveyard ORM context."));

        payloadCodec = new GravePayloadCodec(
                settings.maximumEntries(),
                settings.maximumItemBytes(),
                settings.maximumPayloadBytes()
        );
        try {
            int configuredJournalBytes = getConfigHandler().get(
                    "storage.journal.maximum_record_bytes",
                    Integer.class,
                    12_000_000
            );
            long encodedPayloadFloor = (settings.maximumPayloadBytes() * 4L + 2L) / 3L + 65_536L;
            int maximumJournalBytes = (int) Math.min(
                    Integer.MAX_VALUE,
                    Math.max(Math.max(65_536L, configuredJournalBytes), encodedPayloadFloor)
            );
            journal = new GraveOperationJournal(
                    getPlugin().getDataFolder().toPath().resolve("features").resolve(getFeatureName()),
                    maximumJournalBytes
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize the Graveyard operation journal.", exception);
        }
        receiptService = new PlayerOperationReceiptService(this);
        safeLocationTracker = new LastSafeLocationTracker(this, settings);
        placementService = new GravePlacementService(settings, safeLocationTracker);
        repository = new GraveRepository(this, ormContext);
        manager = new GraveManager(
                this,
                settings,
                repository,
                journal,
                receiptService,
                payloadCodec,
                placementService
        );

        GraveCaptureService captureService = new GraveCaptureService(
                this,
                settings,
                manager,
                journal,
                receiptService,
                payloadCodec,
                placementService
        );
        getLifecycleManager().getListenerManager().registerListener(new GraveDeathListener(captureService));
        getLifecycleManager().getListenerManager().registerListener(
                new GravePlayerLifecycleListener(this, manager, safeLocationTracker)
        );
        getLifecycleManager().getCommandManager().registerBrigadierCommand(new GraveCommand(this, manager));
        getLifecycleManager().getApiManager().registerService(GraveyardService.class, manager);

        packetListener = new GraveInteractionPacketListener(this, manager);
        PacketEvents.getAPI().getEventManager().registerListener(packetListener);
        manager.initialize();
        expiryNotifier = new GraveExpiryNotifier(this, manager);
        expiryNotifier.start();
    }

    @Override
    public void disable() {
        Throwable failure = null;
        if (manager != null) {
            try {
                manager.shutdown();
            } catch (Throwable throwable) {
                failure = throwable;
            }
        }
        if (packetListener != null) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
            } catch (Throwable throwable) {
                if (failure == null) {
                    failure = throwable;
                } else {
                    failure.addSuppressed(throwable);
                }
            }
            packetListener = null;
        }
        if (activeClock != null) {
            try {
                activeClock.close();
            } catch (Throwable throwable) {
                if (failure == null) {
                    failure = throwable;
                } else {
                    failure.addSuppressed(throwable);
                }
            }
        }
        if (failure != null) {
            throwUnchecked(failure);
        }
    }

    public GraveyardSettings getSettings() {
        return settings;
    }

    public GraveManager getManager() {
        return manager;
    }

    public ServerActiveClock getActiveClock() {
        return activeClock;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable throwable) throws E {
        throw (E) throwable;
    }
}
