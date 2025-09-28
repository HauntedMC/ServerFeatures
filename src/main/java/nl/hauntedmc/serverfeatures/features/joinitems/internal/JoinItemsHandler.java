package nl.hauntedmc.serverfeatures.features.joinitems.internal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import nl.hauntedmc.serverfeatures.config.ConfigNode;
import nl.hauntedmc.serverfeatures.features.joinitems.JoinItems;
import nl.hauntedmc.serverfeatures.features.joinitems.model.JoinItemDefinition;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Core logic:
 * - Parse & hold definitions and global options from ConfigHandler (typed + ConfigNode).
 * - Create and tag ItemStacks (PDC) for robust identification.
 * - Give/remove items; detect managed items using PDC.
 * <p>
 * Note: Minecraft applies italics to custom item names/lore by default.
 * We explicitly disable italics on both display name and lore.
 */
public final class JoinItemsHandler {

    private static final String KEY_ITEM_ID = "joinitems_item"; // PDC key name (no colon)

    private final JoinItems feature;
    private final NamespacedKey pdcItemKey;
    // Item definitions, deterministic order (config insertion order preserved)
    private final Map<String, JoinItemDefinition> defsById = new LinkedHashMap<>();
    // (defIdLower -> ItemStack template). Rebuilt on reload.
    private final Map<String, ItemStack> templateCache = new ConcurrentHashMap<>();
    // Global options
    private volatile boolean includeAllItems;
    private volatile boolean removeOnJoin;
    private volatile boolean removeOnLeave;
    private volatile int joinDelayTicks;

    public JoinItemsHandler(JoinItems feature) {
        this.feature = feature;
        this.pdcItemKey = new NamespacedKey(feature.getPlugin(), KEY_ITEM_ID);
    }

    // Convenience for right-hand interactions only
    public static boolean isMainHand(EquipmentSlot slot) {
        return slot == EquipmentSlot.HAND;
    }

    public void reloadFromConfig() {
        defsById.clear();
        templateCache.clear();

        // ---- Global options (typed getters)
        includeAllItems = feature.getConfigHandler().getSetting("include-all-items", Boolean.class, false);
        removeOnJoin = feature.getConfigHandler().getSetting("remove-on-join", Boolean.class, true);
        removeOnLeave = feature.getConfigHandler().getSetting("remove-on-leave", Boolean.class, true);
        joinDelayTicks = feature.getConfigHandler().getSetting("join-delay", Integer.class, 2);

        // ---- Items section using ConfigNode (normalized + typed)
        ConfigNode items = feature.getConfigHandler().node("items");
        int loaded = 0;

        for (Map.Entry<String, ConfigNode> entry : items.children().entrySet()) {
            String idKey = entry.getKey();
            ConfigNode n = entry.getValue();

            // Normalize id to lowercase for consistent lookups & PDC tags
            String id = idKey.toLowerCase(Locale.ROOT);

            String materialStr = n.get("material").as(String.class, "STONE");
            int slot = n.get("slot").as(Integer.class, 0);
            String nameStr = n.get("name").as(String.class, "");

            List<String> lore = Optional.ofNullable(n.get("lore").listOf(String.class)).orElseGet(List::of);
            // Merge command/commands and strip leading slash if present
            List<String> cmds = n.mergedStringList("command", "commands").stream()
                    .map(s -> {
                        String c = s == null ? "" : s.trim();
                        return c.startsWith("/") ? c.substring(1) : c;
                    })
                    .filter(s -> !s.isBlank())
                    .toList();

            boolean locked = n.get("locked").as(Boolean.class, true);
            boolean unmovable = n.get("unmovable").as(Boolean.class, true);
            boolean undroppable = n.get("undroppable").as(Boolean.class, true);

            Material mat = Material.matchMaterial(materialStr);
            if (mat == null) {
                feature.getLogger().warning("Unknown material '" + materialStr + "' for item '" + idKey + "'. Defaulting to STONE.");
                mat = Material.STONE;
            }

            JoinItemDefinition def = new JoinItemDefinition(
                    id, mat, slot,
                    JoinItemDefinition.toComponent(nameStr),
                    JoinItemDefinition.toComponents(lore),
                    cmds,
                    locked, unmovable, undroppable
            );
            defsById.put(id, def);
            templateCache.put(id, buildTemplate(def));
            loaded++;
        }

        feature.getLogger().info("Loaded " + loaded + " join items");
    }

    public boolean isIncludeAllItems() {
        return includeAllItems;
    }

    public boolean isRemoveOnJoin() {
        return removeOnJoin;
    }

    public boolean isRemoveOnLeave() {
        return removeOnLeave;
    }

    public int getJoinDelayTicks() {
        return Math.max(0, joinDelayTicks);
    }

    public Collection<JoinItemDefinition> definitions() {
        return Collections.unmodifiableCollection(defsById.values());
    }

    public Optional<JoinItemDefinition> find(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(defsById.get(id.toLowerCase(Locale.ROOT)));
    }

    /**
     * Give all configured items, overwriting any existing stacks at those slots.
     */
    public void giveAll(Player player) {
        PlayerInventory inv = player.getInventory();
        for (JoinItemDefinition def : defsById.values()) {
            int slot = def.slot();
            if (slot < 0 || slot >= inv.getSize()) continue;
            inv.setItem(slot, cloneTagged(def));
        }
        player.updateInventory();
    }

    /**
     * Remove either all items or only managed items, depending on includeAllItems.
     */
    public void purgeFor(Player player) {
        if (includeAllItems) {
            clearAllButEquipment(player);
            return;
        }
        removeOnlyManaged(player);
    }

    /**
     * Returns the managed item id if present, else empty.
     */
    public Optional<String> readManagedId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return Optional.empty();
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String id = pdc.get(pdcItemKey, PersistentDataType.STRING);
        if (id == null || id.isBlank()) return Optional.empty();
        return Optional.of(id.toLowerCase(Locale.ROOT));
    }

    /**
     * Helper: is this an item managed by JoinItems?
     */
    public boolean isManaged(ItemStack stack) {
        return readManagedId(stack).isPresent();
    }

    /**
     * Get the definition for a managed item stack (by tag).
     */
    public Optional<JoinItemDefinition> definitionOf(ItemStack stack) {
        return readManagedId(stack).flatMap(this::find);
    }

    /**
     * Create a tagged, immutable template for a definition. Ensures no italics on name/lore.
     */
    private ItemStack buildTemplate(JoinItemDefinition def) {
        ItemStack is = new ItemStack(def.material());
        ItemMeta meta = is.getItemMeta();

        // Display name (explicitly disable italics)
        Component name = def.name();
        if (name != null && !Component.empty().equals(name)) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        }

        // Lore (explicitly disable italics on each line)
        if (!def.lore().isEmpty()) {
            List<Component> deItalic = def.lore().stream()
                    .map(c -> c.decoration(TextDecoration.ITALIC, false))
                    .toList();
            meta.lore(deItalic);
        }

        // Tag item id (lowercase) for robust identification
        meta.getPersistentDataContainer().set(pdcItemKey, PersistentDataType.STRING, def.id());

        is.setItemMeta(meta);
        return is;
    }

    /**
     * Clone a tagged instance for handover to a player.
     */
    private ItemStack cloneTagged(JoinItemDefinition def) {
        String key = def.id().toLowerCase(Locale.ROOT);
        ItemStack template = templateCache.get(key);
        if (template == null) template = buildTemplate(def);
        return template.clone();
    }

    /**
     * Clear everything except armor & offhand.
     */
    private void clearAllButEquipment(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] armor = inv.getArmorContents();
        ItemStack offhand = inv.getItemInOffHand();

        inv.clear(); // clears storage+hotbar
        inv.setArmorContents(armor);
        inv.setItemInOffHand(offhand);

        player.updateInventory();
    }

    /**
     * Remove only our managed items anywhere in the inventory.
     */
    private void removeOnlyManaged(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (isManaged(it)) inv.setItem(i, null);
        }
        if (isManaged(inv.getItemInOffHand())) inv.setItemInOffHand(null);
        player.updateInventory();
    }
}
