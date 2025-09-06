package nl.hauntedmc.serverfeatures.features.teleportation.service;

import nl.hauntedmc.serverfeatures.features.teleportation.Teleportation;
import nl.hauntedmc.serverfeatures.features.teleportation.integration.EssentialsHook;
import nl.hauntedmc.serverfeatures.features.teleportation.internal.TeleportAction;
import nl.hauntedmc.serverfeatures.features.teleportation.internal.TeleportState;
import nl.hauntedmc.serverfeatures.features.teleportation.util.Msg;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

public class TeleportService {

    private final Teleportation feature;
    private final TeleportState state;
    private final SafeLocationFinder finder;
    private final EssentialsHook essentials;
    private final TeleportBounds bounds;

    public TeleportService(Teleportation feature) {
        this.feature = feature;
        this.state = feature.getState();
        this.bounds = new TeleportBounds(feature);
        this.finder = new SafeLocationFinder(feature, bounds); // inject bounds for consistency
        this.essentials = new EssentialsHook();
    }

    /* ----------------------------- */
    /* Helpers */
    /* ----------------------------- */

    private boolean tryStartCooldown(Player p, TeleportAction action, CommandSender actor) {
        long now = System.currentTimeMillis();
        if (state.tryStart(p.getUniqueId(), action, now)) return true;

        long remaining = state.remainingCooldownSeconds(p.getUniqueId(), action, now);
        Msg.send(feature, actor, "teleportation.cooldown_active", Map.of("seconds", String.valueOf(remaining)));
        return false;
    }

    private boolean playSoundsEnabled() {
        Object v = feature.getConfigHandler().getSetting("play_sounds");
        return (v instanceof Boolean b) ? b : true;
    }

    private void playEffects(Player p) {
        if (playSoundsEnabled()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 10f, 1.5f);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 10f, 1.5f);
        }
    }

    /* ----------------------------- */
    /* Public API */
    /* ----------------------------- */

    /** /randomtp */
    public void randomTp(CommandSender actor, Player target) {
        if (!tryStartCooldown(target, TeleportAction.RANDOM_TP, actor)) return;

        Msg.send(feature, actor, "teleportation.working.randomtp", Map.of());

        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
            World world = target.getWorld();
            Location to = finder.findRandomSafeLocation(world); // respects outer WB + inner exclusion

            if (to == null) {
                state.reset(target.getUniqueId(), TeleportAction.RANDOM_TP); // allow retry
                int attempts = finder.maxAttempts();
                Msg.send(feature, actor, "teleportation.randomtp.no_safe_found", Map.of("attempts", String.valueOf(attempts)));
                return;
            }

            essentials.setLastLocationIfAvailable(target); // for /back
            target.setVelocity(new Vector(0, 0, 0));
            target.teleport(to);
            playEffects(target);

            Msg.send(feature, actor, "teleportation.success.randomtp", Map.of());
        });
    }

    /** /tppos <x> <y> <z> */
    public void tpPos(CommandSender actor, Player target, int x, int y, int z) {
        if (!tryStartCooldown(target, TeleportAction.TP_POS, actor)) return;

        // Only outer (WorldBorder) check for /tppos
        if (!bounds.withinOuter(target.getWorld(), x, z)) {
            Msg.send(feature, actor, "teleportation.outside_worldborder", Map.of());
            state.reset(target.getUniqueId(), TeleportAction.TP_POS);
            return;
        }

        Msg.send(feature, actor, "teleportation.working.tppos", Map.of());

        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
            World world = target.getWorld();

            // New: find a safe standing spot ON GROUND: above ground if y is above,
            // otherwise nearest safe floor BELOW (if any). Else, fail with message.
            Location safe = finder.findSafeForTpPos(world, x, y, z);

            if (safe == null) {
                state.reset(target.getUniqueId(), TeleportAction.TP_POS);
                Msg.send(feature, actor, "teleportation.tppos.not_safe", Map.of());
                return;
            }

            essentials.setLastLocationIfAvailable(target); // for /back
            target.setVelocity(new Vector(0, 0, 0));
            target.teleport(safe);
            playEffects(target);

            Msg.send(feature, actor, "teleportation.success.tppos", Map.of());

        });
    }
}
