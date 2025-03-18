package nl.hauntedmc.serverfeatures.features.durabilityalert.internal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import nl.hauntedmc.serverfeatures.features.durabilityalert.DurabilityAlert;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.Map;

public class DurabilityAlertHandler {

    private final DurabilityAlert feature;
    private final int defaultValue;

    public DurabilityAlertHandler(DurabilityAlert feature) {
        this.feature = feature;
        this.defaultValue = (int) feature.getConfigHandler().getSetting("defaultvalue");

    }

    /**
     * Processes a PlayerItemDamageEvent. If the damaged item’s remaining durability
     * (in percentage) is below the threshold (defaultvalue), warn the player.
     *
     * @param event the item damage event
     */
    public void handleDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        // Ensure the item is damageable.
        if (!item.hasItemMeta() || !(item.getItemMeta() instanceof Damageable meta)) {
            return;
        }

        int maxDurability = item.getType().getMaxDurability();
        if (maxDurability <= 0) {
            return;
        }
        int damage = meta.getDamage();
        int remaining = maxDurability - damage;

        double percentage = ((double) remaining / maxDurability) * 100;

        if (percentage <= this.defaultValue) {
            String displayName = LegacyComponentSerializer.legacyAmpersand().serialize(item.effectiveName());
            sendWarning(player, displayName, Math.max(remaining-1, 0));
        }
    }

    /**
     * Sends a warning to the player via an action bar message and plays a sound.
     *
     * @param player              the player to warn
     * @param itemName            the formatted name of the item
     * @param durabilityRemaining the remaining durability value
     */
    private void sendWarning(Player player, String itemName, int durabilityRemaining) {
        Component message;

        if (durabilityRemaining == 0) {
            message = feature.getLocalizationHandler().getMessage("durabilityalert.no_durability", player, Map.of("item", itemName));
        } else {
            Component lowMsg = feature.getLocalizationHandler().getMessage("durabilityalert.low_durability", player, Map.of("item", itemName));
            Component durMsg = feature.getLocalizationHandler().getMessage("durabilityalert.durability_left", player, Map.of("durability", String.valueOf(durabilityRemaining)));
            message = lowMsg.append(durMsg);
        }

        player.sendActionBar(message);

        // Play a sound to alert the player.
        if (durabilityRemaining == 0) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_SNARE, 1.0f, 1.0f);
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.0f, 1.0f);
        }
    }
}
