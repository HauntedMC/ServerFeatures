package nl.hauntedmc.serverfeatures.features.deephaste;

import com.destroystokyo.paper.event.block.BeaconEffectEvent;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.BaseFeature;
import nl.hauntedmc.serverfeatures.features.deephaste.meta.Meta;
import nl.hauntedmc.serverfeatures.localization.MessageMap;
import org.bukkit.Bukkit;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;

public class DeepHaste extends BaseFeature<Meta> {

    public DeepHaste(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);
        defaults.put("y_level", 6);            // Below this y-level we boost the haste
        defaults.put("haste_amplifier", 7);    // Amplifier for the FAST_DIGGING effect
        return defaults;
    }

    /**
     * If you have any default messages you want to provide, you can add them here.
     * For example:
     */
    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    /**
     * Called when the feature is enabled.
     */
    @Override
    public void initialize() {
        // Make sure we are running on Paper, since BeaconEffectEvent is Paper-only
        if (!isPaperServer()) {
            getPlugin().getLogger().warning("DeepHaste feature requires Paper (BeaconEffectEvent). Disabling this feature.");
            return;
        }
        getLifecycleManager().registerListener(new DeepHasteListener());
    }

    /**
     * Utility to detect if the server is Paper.
     */
    private boolean isPaperServer() {
        return Bukkit.getServer().getName().equalsIgnoreCase("Paper")
                || Bukkit.getServer().getVersion().contains("Paper");
    }

    /**
     * Our event listener handling the Paper-specific BeaconEffectEvent and PlayerMoveEvent.
     */
    private class DeepHasteListener implements Listener {

        @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
        public void onBeaconEffect(BeaconEffectEvent event) {
            Player player = event.getPlayer();

            int yLevel = (int) getConfigHandler().getSetting("y_level");

            if (player.getLocation().getY() < yLevel) {
                if (event.getEffect().getType() == PotionEffectType.HASTE) {
                    int amplifier = (int) getConfigHandler().getSetting("haste_amplifier");
                    event.setEffect(new PotionEffect(PotionEffectType.HASTE, 320, amplifier));
                }
            }
        }

        @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
        public void onMove(PlayerMoveEvent event) {
            Player player = event.getPlayer();

            int yLevel = (int) getConfigHandler().getSetting("y_level");

            PotionEffect hasteEffect = player.getPotionEffect(PotionEffectType.HASTE);
            if (hasteEffect != null) {
                int amplifier = hasteEffect.getAmplifier();
                int configuredAmplifier = (int) getConfigHandler().getSetting("haste_amplifier");

                if (player.getLocation().getY() > yLevel && amplifier == configuredAmplifier) {
                    player.removePotionEffect(PotionEffectType.HASTE);
                }
            }
        }
    }
}
