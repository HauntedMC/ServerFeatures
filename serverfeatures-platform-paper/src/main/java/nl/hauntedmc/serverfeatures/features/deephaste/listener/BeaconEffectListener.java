package nl.hauntedmc.serverfeatures.features.deephaste.listener;

import com.destroystokyo.paper.event.block.BeaconEffectEvent;
import nl.hauntedmc.serverfeatures.features.deephaste.DeepHaste;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Our event listener handling the Paper-specific BeaconEffectEvent and PlayerMoveEvent.
 */
public class BeaconEffectListener implements Listener {


    private final DeepHaste feature;

    public BeaconEffectListener(DeepHaste feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBeaconEffect(BeaconEffectEvent event) {
        Player player = event.getPlayer();

        int yLevel = (int) feature.getConfigHandler().get("y_level");

        if (player.getLocation().getY() < yLevel) {
            if (event.getEffect().getType() == PotionEffectType.HASTE) {
                int amplifier = (int) feature.getConfigHandler().get("haste_amplifier");
                event.setEffect(new PotionEffect(PotionEffectType.HASTE, 320, amplifier));
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        int yLevel = (int) feature.getConfigHandler().get("y_level");

        PotionEffect hasteEffect = player.getPotionEffect(PotionEffectType.HASTE);
        if (hasteEffect != null) {
            int amplifier = hasteEffect.getAmplifier();
            int configuredAmplifier = (int) feature.getConfigHandler().get("haste_amplifier");

            if (player.getLocation().getY() > yLevel && amplifier == configuredAmplifier) {
                player.removePotionEffect(PotionEffectType.HASTE);
            }
        }
    }
}