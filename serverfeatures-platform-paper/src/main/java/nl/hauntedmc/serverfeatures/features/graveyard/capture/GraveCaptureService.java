package nl.hauntedmc.serverfeatures.features.graveyard.capture;

import nl.hauntedmc.serverfeatures.api.graveyard.GraveStatus;
import nl.hauntedmc.serverfeatures.features.graveyard.Graveyard;
import nl.hauntedmc.serverfeatures.features.graveyard.config.GraveyardMode;
import nl.hauntedmc.serverfeatures.features.graveyard.config.GraveyardSettings;
import nl.hauntedmc.serverfeatures.features.graveyard.journal.CaptureJournalRecord;
import nl.hauntedmc.serverfeatures.features.graveyard.journal.CaptureJournalState;
import nl.hauntedmc.serverfeatures.features.graveyard.journal.GraveOperationJournal;
import nl.hauntedmc.serverfeatures.features.graveyard.journal.PlayerOperationReceiptService;
import nl.hauntedmc.serverfeatures.features.graveyard.model.Grave;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GraveItemEntry;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GraveLocation;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GravePayload;
import nl.hauntedmc.serverfeatures.features.graveyard.persistence.EncodedGravePayload;
import nl.hauntedmc.serverfeatures.features.graveyard.persistence.GravePayloadCodec;
import nl.hauntedmc.serverfeatures.features.graveyard.placement.GravePlacementResult;
import nl.hauntedmc.serverfeatures.features.graveyard.placement.GravePlacementService;
import nl.hauntedmc.serverfeatures.features.graveyard.runtime.GraveManager;
import nl.hauntedmc.serverfeatures.features.vanish.internal.VanishAPI;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Synchronous death-event ownership transfer. The event is not mutated until a complete PREPARED
 * record has been fsynced to the local operation journal.
 */
public final class GraveCaptureService {
    private static final String KEEP_INVENTORY_PERMISSION =
            "serverfeatures.feature.graveyard.keepinventory";

    private final Graveyard feature;
    private final GraveyardSettings settings;
    private final GraveManager manager;
    private final GraveOperationJournal journal;
    private final PlayerOperationReceiptService receipts;
    private final GravePayloadCodec payloadCodec;
    private final GravePlacementService placementService;
    private final DeathDropMatcher dropMatcher = new DeathDropMatcher();
    private final PostDeathInventoryBuilder postDeathInventoryBuilder = new PostDeathInventoryBuilder();

    public GraveCaptureService(
            Graveyard feature,
            GraveyardSettings settings,
            GraveManager manager,
            GraveOperationJournal journal,
            PlayerOperationReceiptService receipts,
            GravePayloadCodec payloadCodec,
            GravePlacementService placementService
    ) {
        this.feature = feature;
        this.settings = settings;
        this.manager = manager;
        this.journal = journal;
        this.receipts = receipts;
        this.payloadCodec = payloadCodec;
        this.placementService = placementService;
    }

    public void handleDeath(DeathInventorySnapshot snapshot, PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (!eligible(player, event)) {
            return;
        }

        try {
            PreparedCapture prepared = prepare(snapshot, event);
            if (prepared == null) {
                return;
            }
            if (settings.mode() == GraveyardMode.OBSERVE) {
                feature.getLogger().info(
                        "Graveyard observe: validated death capture for " + player.getName()
                                + " with " + prepared.payload().entries().size() + " entries."
                );
                return;
            }
            commit(snapshot, event, prepared);
        } catch (IOException exception) {
            feature.getLogger().log(
                    Level.SEVERE,
                    "Graveyard capture preparation failed for " + player.getName()
                            + "; vanilla death handling was preserved.",
                    exception
            );
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("graveyard.storage_failed_vanilla_fallback")
                    .forAudience(player)
                    .build());
        } catch (RuntimeException exception) {
            feature.getLogger().log(
                    Level.SEVERE,
                    "Unexpected Graveyard capture failure for " + player.getName()
                            + "; vanilla death handling was preserved where possible.",
                    exception
            );
        }
    }

    private PreparedCapture prepare(
            DeathInventorySnapshot snapshot,
            PlayerDeathEvent event
    ) throws IOException {
        Player player = event.getPlayer();
        List<ItemAllocation> allocations = dropMatcher.allocate(
                snapshot.inventory().occupiedSlots(),
                cloneItems(event.getDrops())
        );
        List<GraveItemEntry> entries = new ArrayList<>(allocations.size());
        for (ItemAllocation allocation : allocations) {
            entries.add(payloadCodec.createEntry(
                    UUID.randomUUID(),
                    allocation.preferredSlot(),
                    allocation.item()
            ));
        }
        int experience = Math.max(
                0,
                (int) Math.floor(event.getDroppedExp() * (settings.experiencePercentage() / 100.0))
        );
        if (entries.isEmpty() && experience == 0) {
            return null;
        }

        UUID graveId = UUID.randomUUID();
        UUID operationToken = UUID.randomUUID();
        String shortId = shortId(graveId);
        GravePlacementResult placement = placementService.place(player, snapshot.deathLocation());
        long activeNow = feature.getPlugin().getServerActiveClock().nowMillis();
        GravePayload payload = new GravePayload(0L, entries, experience);
        EncodedGravePayload encoded = payloadCodec.encode(payload);
        GraveLocation deathLocation = GraveLocation.from(snapshot.deathLocation());
        boolean vanished = feature.getLifecycleManager().getApiManager()
                .findService(VanishAPI.class)
                .map(api -> api.isVanished(player.getUniqueId()))
                .orElse(false);
        Grave grave = new Grave(
                graveId,
                shortId,
                player.getUniqueId(),
                player.getName(),
                settings.serverId(),
                settings.inventoryScope(),
                deathLocation,
                placement.location(),
                placement.type(),
                GraveStatus.ACTIVE,
                System.currentTimeMillis(),
                activeNow,
                Math.addExact(activeNow, settings.lifetimeMillis()),
                null,
                entries.size(),
                experience,
                payload.revision(),
                encoded.checksum(),
                event.getDamageSource().getDamageType().getKey().asString(),
                vanished
        );
        CaptureJournalRecord record = new CaptureJournalRecord(
                operationToken,
                CaptureJournalState.PREPARED,
                grave,
                encoded
        );
        journal.writeCapture(record);
        return new PreparedCapture(record, payload);
    }

    private void commit(
            DeathInventorySnapshot snapshot,
            PlayerDeathEvent event,
            PreparedCapture prepared
    ) {
        Player player = event.getPlayer();
        EventState eventState = EventState.capture(event);
        PlayerInventoryState postDeath = postDeathInventoryBuilder.build(snapshot, event.getItemsToKeep());
        CaptureJournalRecord record = prepared.record();
        boolean playerDataSaved = false;
        try {
            receipts.putCapture(player, record.operationToken(), record.grave().graveId());
            postDeath.apply(player);
            player.setExperienceLevelAndProgress(Math.max(0, event.getNewTotalExp()));
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            player.saveData();
            playerDataSaved = true;

            CaptureJournalRecord committed = record.withState(CaptureJournalState.COMMITTED);
            journal.writeCapture(committed);
            receipts.clearCapture(player);
            saveReceiptCleanup(player);
            manager.acceptCommittedCapture(committed, prepared.payload());
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage(record.grave().placementType().name().equals("REMOTE_ONLY")
                            ? "graveyard.created_remote"
                            : "graveyard.created")
                    .with("grave_id", record.grave().shortId())
                    .with("x", Integer.toString((int) Math.floor(record.grave().location().x())))
                    .with("y", Integer.toString((int) Math.floor(record.grave().location().y())))
                    .with("z", Integer.toString((int) Math.floor(record.grave().location().z())))
                    .forAudience(player)
                    .build());
        } catch (IOException exception) {
            if (!playerDataSaved) {
                rollbackBeforeSave(snapshot, event, eventState, record, player, exception);
                return;
            }
            feature.getLogger().log(
                    Level.SEVERE,
                    "Playerdata was saved for grave " + record.grave().graveId()
                            + " but its COMMITTED journal transition failed. The capture receipt was retained.",
                    exception
            );
        } catch (RuntimeException exception) {
            if (!playerDataSaved) {
                rollbackBeforeSave(snapshot, event, eventState, record, player, exception);
                return;
            }
            feature.getLogger().log(
                    Level.SEVERE,
                    "Graveyard capture failed after playerdata save for grave " + record.grave().graveId()
                            + "; startup/join recovery will finish it.",
                    exception
            );
        }
    }

    private void rollbackBeforeSave(
            DeathInventorySnapshot snapshot,
            PlayerDeathEvent event,
            EventState eventState,
            CaptureJournalRecord record,
            Player player,
            Throwable failure
    ) {
        snapshot.inventory().apply(player);
        player.setExperienceLevelAndProgress(snapshot.totalExperience());
        receipts.clearCapture(player);
        eventState.restore(event);
        try {
            journal.writeCapture(record.withState(CaptureJournalState.ABORTED));
        } catch (IOException journalFailure) {
            failure.addSuppressed(journalFailure);
        }
        feature.getLogger().log(
                Level.SEVERE,
                "Graveyard capture rolled back before playerdata save for " + player.getName(),
                failure
        );
    }

    private boolean eligible(Player player, PlayerDeathEvent event) {
        if (settings.mode() == GraveyardMode.ACTIVE && !manager.canMutate()) {
            return false;
        }
        if (player.hasPermission(KEEP_INVENTORY_PERMISSION)) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("graveyard.keep_inventory")
                    .forAudience(player)
                    .build());
            return false;
        }
        if (event.getKeepInventory()) {
            return false;
        }
        String worldName = player.getWorld().getName().toLowerCase(Locale.ROOT);
        String worldKey = player.getWorld().getKey().asString().toLowerCase(Locale.ROOT);
        if (settings.disabledWorlds().contains(worldName)
                || settings.disabledWorlds().contains(worldKey)) {
            return false;
        }
        GameMode gameMode = player.getGameMode();
        return !settings.disabledGameModes().contains(gameMode);
    }

    private void saveReceiptCleanup(Player player) {
        try {
            player.saveData();
        } catch (RuntimeException exception) {
            feature.getLogger().warning(
                    "Could not immediately persist Graveyard capture receipt cleanup for " + player.getName()
            );
        }
    }

    private static List<ItemStack> cloneItems(List<ItemStack> items) {
        return items.stream()
                .filter(item -> item != null && !item.getType().isAir() && item.getAmount() > 0)
                .map(ItemStack::clone)
                .toList();
    }

    private static String shortId(UUID graveId) {
        return graveId.toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
    }

    private record PreparedCapture(CaptureJournalRecord record, GravePayload payload) {
    }

    private record EventState(
            boolean keepInventory,
            boolean keepLevel,
            int droppedExperience,
            List<ItemStack> drops
    ) {
        static EventState capture(PlayerDeathEvent event) {
            return new EventState(
                    event.getKeepInventory(),
                    event.getKeepLevel(),
                    event.getDroppedExp(),
                    cloneItems(event.getDrops())
            );
        }

        void restore(PlayerDeathEvent event) {
            event.setKeepInventory(keepInventory);
            event.setKeepLevel(keepLevel);
            event.setDroppedExp(droppedExperience);
            event.getDrops().clear();
            event.getDrops().addAll(cloneItems(drops));
        }
    }
}
