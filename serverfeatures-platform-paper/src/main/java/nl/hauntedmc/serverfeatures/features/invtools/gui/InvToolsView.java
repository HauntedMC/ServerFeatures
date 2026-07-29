package nl.hauntedmc.serverfeatures.features.invtools.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;
import nl.hauntedmc.serverfeatures.features.invtools.persistence.OfflinePlayerData;
import nl.hauntedmc.serverfeatures.framework.localization.LocalizationHandler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class InvToolsView implements InventoryHolder {

    private final UUID sessionId = UUID.randomUUID();
    private final LocalizationHandler localization;
    private final Audience viewerAudience;
    private final UUID viewerId;
    private final String viewerName;
    private final UUID targetId;
    private final String targetName;
    private final InventoryKind kind;
    private final boolean onlineSession;
    private final boolean editable;
    private final OfflinePlayerData originalOfflineData;
    private final Inventory inventory;

    private InventorySnapshot snapshot;
    private ItemStack cursor;
    private State state = State.ACTIVE;
    private boolean dirty;
    private boolean saveOutcomeAudited;
    private CompletableFuture<SaveResult> saveCompletion;

    public InvToolsView(
            LocalizationHandler localization,
            Player viewer,
            UUID targetId,
            String targetName,
            InventoryKind kind,
            boolean onlineSession,
            boolean editable,
            InventorySnapshot snapshot,
            OfflinePlayerData originalOfflineData
    ) {
        Player checkedViewer = Objects.requireNonNull(viewer, "viewer");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.viewerAudience = checkedViewer;
        this.viewerId = checkedViewer.getUniqueId();
        this.viewerName = checkedViewer.getName();
        this.targetId = Objects.requireNonNull(targetId, "targetId");
        this.targetName = Objects.requireNonNull(targetName, "targetName");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.onlineSession = onlineSession;
        this.editable = editable;
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.originalOfflineData = originalOfflineData;
        if (onlineSession == (originalOfflineData != null)) {
            throw new IllegalArgumentException("Exactly one online/offline backing source is required");
        }

        this.inventory = Bukkit.createInventory(
                this,
                InventorySlotLayout.guiSize(kind),
                title()
        );
        render();
    }

    public synchronized boolean updateBackingSlot(
            int backingSlot,
            ItemStack item,
            ItemStack cursorItem
    ) {
        if (state != State.ACTIVE) {
            return false;
        }
        snapshot = snapshot.withBackingSlot(kind, backingSlot, item);
        cursor = cloneOrNull(cursorItem);
        dirty = true;
        InventorySlotLayout.guiSlot(kind, backingSlot)
                .ifPresent(guiSlot -> inventory.setItem(guiSlot, snapshot.itemAt(kind, backingSlot)));
        return true;
    }

    public synchronized void refresh(InventorySnapshot changedSnapshot) {
        if (state != State.ACTIVE) {
            return;
        }
        InventorySnapshot replacement = Objects.requireNonNull(changedSnapshot, "changedSnapshot");
        int[] changedSlots = snapshot.changedBackingSlots(kind, replacement);
        snapshot = replacement;
        for (int backingSlot : changedSlots) {
            InventorySlotLayout.guiSlot(kind, backingSlot).ifPresent(guiSlot ->
                    inventory.setItem(guiSlot, snapshot.itemAt(kind, backingSlot))
            );
        }
    }

    public synchronized OfflineSavePlan beginOfflineSave() {
        if (onlineSession) {
            throw new IllegalStateException("Online sessions cannot be persisted through playerdata");
        }
        if (state == State.SAVING) {
            return new OfflineSavePlan(
                    originalOfflineData,
                    kind,
                    snapshot,
                    dirty,
                    saveCompletion,
                    false
            );
        }
        if (state == State.CLOSED) {
            return null;
        }
        settleOfflineCursor();
        state = State.SAVING;
        saveCompletion = new CompletableFuture<>();
        return new OfflineSavePlan(
                originalOfflineData,
                kind,
                snapshot,
                dirty,
                saveCompletion,
                true
        );
    }

    public synchronized void freeze() {
        if (state == State.ACTIVE) {
            state = State.FROZEN;
        }
    }

    public synchronized void closeWithoutSaving() {
        state = State.CLOSED;
        if (saveCompletion != null && !saveCompletion.isDone()) {
            saveCompletion.complete(SaveResult.DISCARDED);
        }
    }

    public synchronized void finishSave(SaveResult result) {
        state = State.CLOSED;
        if (saveCompletion != null) {
            saveCompletion.complete(Objects.requireNonNull(result, "result"));
        }
    }

    public synchronized boolean isInteractive() {
        return state == State.ACTIVE;
    }

    public synchronized InventorySnapshot snapshot() {
        return snapshot;
    }

    public synchronized ItemStack cursor() {
        return cloneOrNull(cursor);
    }

    public UUID viewerId() {
        return viewerId;
    }

    public String viewerName() {
        return viewerName;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public UUID targetId() {
        return targetId;
    }

    public String targetName() {
        return targetName;
    }

    public InventoryKind kind() {
        return kind;
    }

    public boolean onlineSession() {
        return onlineSession;
    }

    public boolean editable() {
        return editable;
    }

    public boolean isolatesViewerCursor() {
        return !onlineSession && editable;
    }

    public synchronized boolean markSaveOutcomeAudited() {
        if (onlineSession || !dirty || saveOutcomeAudited) {
            return false;
        }
        saveOutcomeAudited = true;
        return true;
    }

    public boolean owns(Inventory candidate) {
        return candidate != null && candidate.getHolder(false) == this;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    private Component title() {
        NamedTextColor color = editable ? NamedTextColor.DARK_RED : NamedTextColor.DARK_AQUA;
        return message(kind == InventoryKind.PLAYER
                ? "invtools.gui.title.inventory"
                : "invtools.gui.title.enderchest")
                .append(Component.text(targetName, color))
                .decoration(TextDecoration.ITALIC, false);
    }

    private void render() {
        ItemStack filler = menuItem(
                Material.GRAY_STAINED_GLASS_PANE,
                Component.empty()
        );
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (InventorySlotLayout.backingSlot(kind, slot).isEmpty()) {
                inventory.setItem(slot, filler);
            }
        }
        renderMappedItems();

        int informationSlot = kind == InventoryKind.PLAYER ? 46 : 28;
        int modeSlot = kind == InventoryKind.PLAYER ? 52 : 34;
        inventory.setItem(informationSlot, menuItem(
                Material.PLAYER_HEAD,
                Component.text(targetName, NamedTextColor.GOLD),
                informationLore()
        ));
        inventory.setItem(modeSlot, menuItem(
                editable ? Material.REDSTONE_TORCH : Material.SPYGLASS,
                message(editable ? "invtools.gui.mode.edit.name" : "invtools.gui.mode.inspect.name"),
                List.of(message(editable
                        ? "invtools.gui.mode.edit.lore"
                        : "invtools.gui.mode.inspect.lore"))
        ));
        inventory.setItem(InventorySlotLayout.closeSlot(kind), menuItem(
                Material.BARRIER,
                message("invtools.gui.close.name")
        ));
    }

    private Component message(String key) {
        return localization.getMessage(key)
                .forAudience(viewerAudience)
                .build();
    }

    private List<Component> informationLore() {
        Component connectionStatus = message(onlineSession
                ? "invtools.gui.info.online"
                : "invtools.gui.info.offline");
        if (kind == InventoryKind.PLAYER) {
            return List.of(
                    connectionStatus,
                    message("invtools.gui.info.inventory.armor_offhand"),
                    message("invtools.gui.info.inventory.main"),
                    message("invtools.gui.info.inventory.hotbar")
            );
        }
        return List.of(
                connectionStatus,
                message("invtools.gui.info.enderchest.slots"),
                message("invtools.gui.info.enderchest.storage")
        );
    }

    private void renderMappedItems() {
        InventorySlotLayout.mappedSlots(kind).forEach((guiSlot, backingSlot) ->
                inventory.setItem(guiSlot, snapshot.itemAt(kind, backingSlot))
        );
    }

    private void settleOfflineCursor() {
        if (cursor == null) {
            return;
        }
        InventorySnapshot.InsertionResult insertion = snapshot.insert(kind, cursor);
        if (insertion.remainder() != null) {
            throw new IllegalStateException(
                    "The isolated offline editor cursor no longer fits in the target inventory"
            );
        }
        snapshot = insertion.snapshot();
        cursor = null;
        dirty = true;
    }

    private static ItemStack menuItem(Material material, Component name) {
        return menuItem(material, name, List.of());
    }

    private static ItemStack menuItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) {
            meta.lore(lore.stream()
                    .map(line -> line.decoration(TextDecoration.ITALIC, false))
                    .toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0
                ? null
                : item.clone();
    }

    private enum State {
        ACTIVE,
        FROZEN,
        SAVING,
        CLOSED
    }

    public enum SaveResult {
        SAVED,
        UNCHANGED,
        DISCARDED,
        CONFLICT,
        FAILED
    }

    public record OfflineSavePlan(
            OfflinePlayerData original,
            InventoryKind kind,
            InventorySnapshot changedSnapshot,
            boolean dirty,
            CompletableFuture<SaveResult> completion,
            boolean newlyStarted
    ) {
        public OfflineSavePlan {
            Objects.requireNonNull(original, "original");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(changedSnapshot, "changedSnapshot");
            Objects.requireNonNull(completion, "completion");
        }
    }
}
