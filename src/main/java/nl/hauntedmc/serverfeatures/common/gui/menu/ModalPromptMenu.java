package nl.hauntedmc.serverfeatures.common.gui.menu;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import nl.hauntedmc.serverfeatures.common.gui.GuiMenu;
import nl.hauntedmc.serverfeatures.common.gui.item.GuiItemHelper;
import nl.hauntedmc.serverfeatures.common.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.lifecycle.FeatureGUIManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Minimal "modal" that captures one line of user input via chat while a GUI is open.
 */
public final class ModalPromptMenu extends GuiMenu implements Listener {

    private static final Map<UUID, ModalPromptMenu> ACTIVE = new ConcurrentHashMap<>();

    private final Component promptLine;
    private final Consumer<String> onSubmit;
    private final Runnable onCancel;
    private final int cancelSlot;
    private final int infoSlot;
    private final int timeoutSeconds;
    private final BukkitBaseFeature<?> feature;

    private volatile boolean finished = false;

    private ModalPromptMenu(
            BukkitBaseFeature<?> feature,
            FeatureGUIManager gui,
            Component title,
            Component promptLine,
            Consumer<String> onSubmit,
            Runnable onCancel,
            int size,
            ItemStack filler,
            int infoSlot,
            int cancelSlot,
            boolean addBackButton,
            int backSlot,
            int timeoutSeconds
    ) {
        super(gui, title, size, false, filler, Map.of(), addBackButton, backSlot);
        this.feature = feature;
        this.promptLine = promptLine;
        this.onSubmit = onSubmit;
        this.onCancel = onCancel;
        this.cancelSlot = cancelSlot;
        this.infoSlot = infoSlot;
        this.timeoutSeconds = timeoutSeconds;
    }

    public static Builder builder(BukkitBaseFeature<?> feature) { return new Builder(feature); }

    @Override
    protected void afterPopulate(Player p, Inventory inv) {
        inv.setItem(infoSlot, GuiItemHelper.info(promptLine));
        inv.setItem(cancelSlot, GuiItemHelper.button(Material.BARRIER, Component.text("Cancel")));
    }

    @Override
    public void onOpen(Player p) {
        super.onOpen(p);
        ACTIVE.put(p.getUniqueId(), this);
        feature.getLifecycleManager().getListenerManager().registerListener(this);
        if (timeoutSeconds > 0) {
            feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
                if (finished) return;
                finished = true;
                try {
                    if (onCancel != null) onCancel.run();
                } finally {
                    gui.goBack(p);
                }
            }, BukkitTime.seconds(timeoutSeconds));
        }
        p.sendMessage(Component.text("Type your response in chat. Use the Cancel button to abort."));
    }

    @Override
    public void onClose(Player p, org.bukkit.event.inventory.InventoryCloseEvent.Reason reason) {
        super.onClose(p, reason);
        cleanup(p.getUniqueId());
    }

    @Override
    public void handleClick(Player p, int slot, org.bukkit.event.inventory.InventoryClickEvent e) {
        if (slot == cancelSlot && !finished) {
            finished = true;
            if (onCancel != null) onCancel.run();
            gui.goBack(p);
            return;
        }
        super.handleClick(p, slot, e);
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        ModalPromptMenu self = ACTIVE.get(p.getUniqueId());
        if (self != this) return;

        e.setCancelled(true);
        if (finished) return;
        finished = true;

        String msg = PlainTextComponentSerializer.plainText().serialize(e.message());

        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
            try {
                if (onSubmit != null) onSubmit.accept(msg);
            } finally {
                gui.goBack(p);
            }
        });
    }

    private void cleanup(UUID uuid) {
        ACTIVE.remove(uuid, this);
        HandlerList.unregisterAll(this);
    }

    public static final class Builder {
        private final BukkitBaseFeature<?> feature;
        private Component title = Component.text("Enter Text");
        private Component promptLine = Component.text("Please type your response in chat...");
        private Consumer<String> onSubmit;
        private Runnable onCancel;
        private int size = 9;
        private ItemStack filler = GuiItemHelper.filler();
        private int infoSlot = 3;
        private int cancelSlot = 5;
        private boolean backButton = true;
        private int backSlot = 8;
        private int timeoutSeconds = 0;

        private Builder(BukkitBaseFeature<?> feature) {
            this.feature = feature;
        }

        public Builder title(Component t) { this.title = t; return this; }
        public Builder prompt(Component p) { this.promptLine = p; return this; }
        public Builder onSubmit(Consumer<String> c) { this.onSubmit = c; return this; }
        public Builder onCancel(Runnable r) { this.onCancel = r; return this; }
        public Builder size(int s) { this.size = s; return this; }
        public Builder filler(ItemStack i) { this.filler = i; return this; }
        public Builder infoSlot(int s) { this.infoSlot = s; return this; }
        public Builder cancelSlot(int s) { this.cancelSlot = s; return this; }
        public Builder backButton(boolean b) { this.backButton = b; return this; }
        public Builder backButtonSlot(int s) { this.backSlot = s; return this; }
        public Builder timeoutSeconds(int secs) { this.timeoutSeconds = Math.max(0, secs); return this; }

        public ModalPromptMenu build() {
            if (size <= 0 || size % 9 != 0 || size > 54) throw new IllegalArgumentException("Invalid size");
            if (infoSlot < 0 || infoSlot >= size) throw new IllegalArgumentException("infoSlot out of bounds");
            if (cancelSlot < 0 || cancelSlot >= size) throw new IllegalArgumentException("cancelSlot out of bounds");
            if (infoSlot == cancelSlot) throw new IllegalArgumentException("infoSlot and cancelSlot must differ");
            GuiMenu.validateNoCollisionsWithBackAndFixed(Map.of(), size, backButton, backSlot, java.util.Set.of(infoSlot, cancelSlot), "ModalPromptMenu");

            FeatureGUIManager gui = feature.getLifecycleManager().getGuiManager();
            return new ModalPromptMenu(feature, gui, title, promptLine, onSubmit, onCancel, size, filler, infoSlot, cancelSlot, backButton, backSlot, timeoutSeconds);
        }
    }
}
