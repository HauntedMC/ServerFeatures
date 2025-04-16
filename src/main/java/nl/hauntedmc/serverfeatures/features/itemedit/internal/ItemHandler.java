package nl.hauntedmc.serverfeatures.features.itemedit.internal;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.common.util.CastUtils;
import nl.hauntedmc.serverfeatures.features.itemedit.ItemEdit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public class ItemHandler {

    private final ItemEdit feature;
    private final List<String> blockedNames;
    private final List<String> blockAnvilItemNames;

    public ItemHandler(ItemEdit feature) {
        this.feature = feature;
        this.blockedNames = CastUtils.safeCastToList(feature.getConfigHandler().getSetting("blockedNames"), String.class);
        this.blockAnvilItemNames = CastUtils.safeCastToList(feature.getConfigHandler().getSetting("blockedAnvilItems"), String.class);
    }

    public void renameItemInAnvil(PrepareAnvilEvent event) {
        String renameText = event.getView().getRenameText();

        if (renameText == null || !renameText.contains("&")) {
            return;
        }
        HumanEntity player = event.getView().getPlayer();
        if (!player.hasPermission("serverfeatures.feature.itemedit.anvilcolors")) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission_rank").forAudience(player).withPlaceholders(Map.of("rank", "&6Elite")).build());
            return;
        }

        String coloredName = ChatColor.translateAlternateColorCodes('&', renameText);
        String rawName = ChatColor.stripColor(coloredName).trim();

        for (String blocked : this.blockedNames) {
            if (rawName.equalsIgnoreCase(blocked)) {
                return;
            }
        }

        ItemStack result = event.getResult();
        if (result == null) {
            return;
        }

        if (blockAnvilItemNames.contains(result.getType().name())) {
            event.setResult(new ItemStack(Material.AIR));
            return;
        }

        if (result.hasItemMeta()) {
            ItemMeta meta = result.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(coloredName));
                result.setItemMeta(meta);
                event.setResult(result);
            }
        }
    }
}
