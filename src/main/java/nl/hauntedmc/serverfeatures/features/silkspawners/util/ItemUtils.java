package nl.hauntedmc.serverfeatures.features.silkspawners.util;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ItemUtils {

    public static @NotNull ItemStack createSpawnerItem(EntityType type) {
        ItemStack spawnerItem = new ItemStack(Material.SPAWNER);
        BlockStateMeta meta = (BlockStateMeta) spawnerItem.getItemMeta();
        if (meta != null) {
            String pretty = Arrays.stream(type.name().split("_"))
                    .map(part -> part.substring(0,1).toUpperCase() + part.substring(1).toLowerCase())
                    .collect(Collectors.joining(" "));

            CreatureSpawner csm = (CreatureSpawner) meta.getBlockState();
            csm.setSpawnedType(type);
            meta.setBlockState(csm);
            meta.displayName(Component.text(pretty + " Spawner").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.empty().decoration(TextDecoration.ITALIC, false),
                    Component.text("Right-click: ")
                            .color(NamedTextColor.AQUA)
                            .decoration(TextDecoration.ITALIC, false)
                            .append(
                                    Component.text("Toggle Mob Spawning")
                                            .color(NamedTextColor.GRAY)
                                            .decoration(TextDecoration.ITALIC, false)
                            ),
                    Component.text("Mineable: ")
                            .color(NamedTextColor.AQUA)
                            .decoration(TextDecoration.ITALIC, false)
                            .append(
                                    Component.text("Legend")
                                            .color(NamedTextColor.DARK_GREEN)
                                            .decoration(TextDecoration.ITALIC, false)
                            )
                            .append(
                                    Component.text(" Rank+")
                                            .color(NamedTextColor.GRAY)
                                            .decoration(TextDecoration.ITALIC, false)
                            )
            ));
            // 1.21.8
            // Keep attributes hidden, replace deprecated HIDE_ADDITIONAL_TOOLTIP with TooltipDisplay
//            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
//            spawnerItem.setItemMeta(meta);
//            spawnerItem.setData(
//                    DataComponentTypes.TOOLTIP_DISPLAY,
//                    TooltipDisplay.tooltipDisplay().hideTooltip(true).build()
//            );

            // 1.21.4
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            spawnerItem.setItemMeta(meta);
        }
        return spawnerItem;
    }
}
