package nl.hauntedmc.serverfeatures.features.itemedit.internal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import nl.hauntedmc.serverfeatures.api.util.text.format.ComponentFormatter;
import nl.hauntedmc.serverfeatures.api.util.text.format.TextFormatter;
import nl.hauntedmc.serverfeatures.api.util.text.format.inspect.FormatInspector;
import nl.hauntedmc.serverfeatures.api.util.type.CastUtils;
import nl.hauntedmc.serverfeatures.features.itemedit.ItemEdit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.EnumSet;
import java.util.List;

public class ItemHandler {

    private final ItemEdit feature;
    private final List<String> blockedNames;
    private final List<String> blockAnvilItemNames;

    public ItemHandler(ItemEdit feature) {
        this.feature = feature;
        this.blockedNames = CastUtils.safeCastToList(feature.getConfigHandler().get("blockedNames"), String.class);
        this.blockAnvilItemNames = CastUtils.safeCastToList(feature.getConfigHandler().get("blockedAnvilItems"), String.class);
    }

    public void renameItemInAnvil(PrepareAnvilEvent event) {
        String renameText = event.getView().getRenameText();
        if (renameText == null || renameText.isEmpty()) {
            return;
        }

        // Detect whether the rename contains any formatting we support (legacy + hex variants + MiniMessage)
        boolean hasFormatting = FormatInspector.containsFormatting(
                renameText,
                EnumSet.of(
                        TextFormatter.InputFormat.LEGACY_AMPERSAND,
                        TextFormatter.InputFormat.LEGACY_SECTION,
                        TextFormatter.InputFormat.HEX_POUND,
                        TextFormatter.InputFormat.HEX_BUNGEE_AMP,
                        TextFormatter.InputFormat.HEX_BUNGEE_SECTION,
                        TextFormatter.InputFormat.HEX_MINI,
                        TextFormatter.InputFormat.MINIMESSAGE
                )
        );

        // If no formatting is present, do nothing (let vanilla anvil naming proceed)
        if (!hasFormatting) {
            return;
        }

        HumanEntity player = event.getView().getPlayer();
        if (!player.hasPermission("serverfeatures.feature.itemedit.anvilcolors")) {
            player.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("general.no_permission_rank")
                            .forAudience(player)
                            .with("rank", "&2Legend")
                            .build()
            );
            return;
        }

        // Convert to a Component while only allowing visual formatting (no click/hover etc.)
        Component coloredNameComponent = ComponentFormatter.deserialize(renameText)
                .expect(TextFormatter.InputFormat.MIXED_INPUT)
                .features(
                        ComponentFormatter.Feature.COLORS,
                        ComponentFormatter.Feature.DECORATIONS,
                        ComponentFormatter.Feature.GRADIENT,
                        ComponentFormatter.Feature.RAINBOW,
                        ComponentFormatter.Feature.RESET
                )
                .toComponent();


        // Remove italics from the component, as it's not supported by anvil naming
        coloredNameComponent = coloredNameComponent.decoration(TextDecoration.ITALIC, false);

        // Get plain text for validation checks against blocked list
        String rawName = ComponentFormatter.serialize(coloredNameComponent)
                .format(ComponentFormatter.Serializer.Format.PLAIN)
                .build()
                .trim();

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
