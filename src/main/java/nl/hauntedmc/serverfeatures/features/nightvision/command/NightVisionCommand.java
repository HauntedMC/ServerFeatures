package nl.hauntedmc.serverfeatures.features.nightvision.command;

import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.api.command.meta.CommandMeta;
import nl.hauntedmc.serverfeatures.features.nightvision.NightVision;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class NightVisionCommand extends FeatureCommand {

    private final NightVision feature;

    public NightVisionCommand(NightVision feature) {
        super(new CommandMeta.Builder("nightvision").build());
        this.feature = feature;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(sender).build());
            return true;
        }

        // Check if the player has permission to use /nv.
        if (!player.hasPermission("serverfeatures.feature.nightvision.command.nv")) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission_rank")
                    .forAudience(player)
                    .with("rank", "&3Supreme")
                    .build());
            return true;
        }

        // Toggle the Night Vision effect.
        if (player.hasPotionEffect(PotionEffectType.NIGHT_VISION) && Objects.requireNonNull(player.getPotionEffect(PotionEffectType.NIGHT_VISION)).getDuration() == PotionEffect.INFINITE_DURATION) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            player.sendMessage(feature.getLocalizationHandler().getMessage("nightvision.status")
                    .forAudience(player)
                    .with("status", "&cuitgeschakeld")
                    .build());
        } else {
            if (player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            }
            PotionEffect nvEffect = new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false);
            player.addPotionEffect(nvEffect);
            player.sendMessage(feature.getLocalizationHandler().getMessage("nightvision.status")
                    .forAudience(player)
                    .with("status", "&aingeschakeld")
                    .build());
        }
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String @NotNull [] args) {
        return Collections.emptyList();
    }
}
