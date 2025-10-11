package nl.hauntedmc.serverfeatures.features.itemedit.internal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import nl.hauntedmc.serverfeatures.api.util.type.CastUtils;
import nl.hauntedmc.serverfeatures.features.itemedit.ItemEdit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public class ItemHandler {

    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

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
            player.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("general.no_permission_rank")
                            .forAudience(player)
                            .withPlaceholders(Map.of("rank", "&2Legend"))
                            .build()
            );
            return;
        }

        // Convert &-codes -> Component, then strip colors to compare against blocked names
        Component coloredNameComponent = AMPERSAND.deserialize(renameText);
        String rawName = PLAIN.serialize(coloredNameComponent).trim();

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
                meta.displayName(coloredNameComponent);
                result.setItemMeta(meta);
                event.setResult(result);
            }
        }
    }
}
