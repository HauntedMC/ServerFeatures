package nl.hauntedmc.serverfeatures.features.balloons.model;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Definition of a balloon: either an item (material + cmd) or a skull-head texture.
 */
public final class BalloonDefinition {

    private final String id;
    private final String permission;
    private final Component displayName;

    private final Material itemMaterial;
    private final Integer customModelData;
    private final String skullTextureBase64;

    public BalloonDefinition(
            String id,
            String permission,
            Component displayName,
            Material itemMaterial,
            Integer customModelData,
            String skullTextureBase64
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.permission = Objects.requireNonNull(permission, "permission");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.itemMaterial = itemMaterial;
        this.customModelData = customModelData;
        this.skullTextureBase64 = skullTextureBase64;
    }

    public String id() {
        return id;
    }

    public String permission() {
        return permission;
    }

    public Component displayName() {
        return displayName;
    }

    public boolean isHead() {
        return skullTextureBase64 != null && !skullTextureBase64.isBlank();
    }

    public boolean isItem() {
        return itemMaterial != null;
    }

    /**
     * Helmet item to render on the floating ArmorStand.
     */
    public ItemStack asHelmetItem() {
        if (isItem()) {
            ItemStack it = new ItemStack(itemMaterial);
            if (customModelData != null && customModelData > 0) {
                ItemMeta meta = it.getItemMeta();
                meta.setCustomModelData(customModelData);
                it.setItemMeta(meta);
            }
            return it;
        }
        // Head fallback
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
        profile.setProperty(new ProfileProperty("textures", skullTextureBase64));
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }

    /**
     * Icon for the menu. Use the same helmet item for clarity.
     */
    public ItemStack asMenuIcon() {
        return asHelmetItem().clone();
    }

    public Optional<Integer> customModelData() {
        return Optional.ofNullable(customModelData);
    }
}
