package nl.hauntedmc.serverfeatures.features.teleportation.service;

import nl.hauntedmc.serverfeatures.features.teleportation.Teleportation;
import nl.hauntedmc.serverfeatures.features.teleportation.integration.EssentialsHook;
import nl.hauntedmc.serverfeatures.features.teleportation.internal.TeleportAction;
import nl.hauntedmc.serverfeatures.features.teleportation.internal.TeleportState;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class TeleportService {

    private final Teleportation feature;
    private final TeleportState state;
    private final TeleportBounds bounds;
    private final SafeLocationFinder finder;
    private final BackService backService;
    private final TeleportEffects effects;
    private final EssentialsHook essentials;

    public TeleportService(Teleportation feature,
                           TeleportState state,
                           TeleportBounds bounds,
                           SafeLocationFinder finder,
                           BackService backService,
                           TeleportEffects effects) {
        this.feature = feature;
        this.state = state;
        this.bounds = bounds;
        this.finder = finder;
        this.backService = backService;
        this.effects = effects;
        this.essentials = new EssentialsHook();
    }

    /* ----------------------------- */
    /* Public API - Commands         */
    /* ----------------------------- */

    /**
     * /randomtp
     */
    public void randomTp(CommandSender actor, Player target) {
        if (!checkAndStartCooldown(actor, target, TeleportAction.RANDOM_TP)) return;

        actor.sendMessage(feature.getLocalizationHandler()
                .getMessage("teleportation.working.randomtp")
                .forAudience(actor)
                .build());

        // Run synchronously (world access is not thread-safe)
        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
            World world = target.getWorld();

            Location to = finder.findRandomSafeLocation(world); // respects outer WB + inner exclusion + claims
            if (to == null) {
                state.reset(target.getUniqueId(), TeleportAction.RANDOM_TP); // allow retry
                int attempts = finder.maxAttempts();
                actor.sendMessage(feature.getLocalizationHandler()
                        .getMessage("teleportation.randomtp.no_safe_found")
                        .forAudience(actor)
                        .with("attempts", attempts)
                        .build());
                return;
            }

            performTeleport(actor, target, to, TeleportAction.RANDOM_TP, () ->
                    actor.sendMessage(feature.getLocalizationHandler()
                            .getMessage("teleportation.success.randomtp")
                            .forAudience(actor)
                            .build())
            );
        });
    }

    /**
     * /tppos <x> <y> <z>
     */
    public void tpPos(CommandSender actor, Player target, int x, int y, int z) {
        if (!checkAndStartCooldown(actor, target, TeleportAction.TP_POS)) return;

        // Only outer (WorldBorder) check for /tppos unless actor has override
        if (!actor.hasPermission("serverfeatures.feature.teleportation.bypass.worldborder") && !bounds.withinOuter(target.getWorld(), x, z)) {
            actor.sendMessage(feature.getLocalizationHandler()
                    .getMessage("teleportation.tppos.outside_worldborder")
                    .forAudience(actor)
                    .build());
            state.reset(target.getUniqueId(), TeleportAction.TP_POS);
            return;
        }

        actor.sendMessage(feature.getLocalizationHandler()
                .getMessage("teleportation.working.tppos")
                .forAudience(actor)
                .build());

        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
            World world = target.getWorld();

            Location safe = finder.findSafeForTpPos(world, x, y, z);
            if (safe == null) {
                state.reset(target.getUniqueId(), TeleportAction.TP_POS);
                actor.sendMessage(feature.getLocalizationHandler()
                        .getMessage("teleportation.tppos.not_safe")
                        .forAudience(actor)
                        .build());
                return;
            }

            performTeleport(actor, target, safe, TeleportAction.TP_POS, () ->
                    actor.sendMessage(feature.getLocalizationHandler()
                            .getMessage("teleportation.success.tppos")
                            .forAudience(actor)
                            .build())
            );
        });
    }

    /* ----------------------------- */
    /* Core flow                     */
    /* ----------------------------- */

    private void performTeleport(CommandSender actor,
                                 Player target,
                                 Location destination,
                                 TeleportAction action,
                                 Runnable onSuccessMessage) {

        try {
            // record /back with Essentials and local fallback
            backService.recordBackLocation(target.getUniqueId(), target.getLocation());
            essentials.setLastLocationIfAvailable(target);

            // Stabilize motion and teleport
            target.setVelocity(new Vector(0, 0, 0));
            boolean ok = target.teleport(destination);

            if (!ok) {
                state.reset(target.getUniqueId(), action);
                actor.sendMessage(feature.getLocalizationHandler()
                        .getMessage("teleportation.error.internal")
                        .forAudience(actor)
                        .build());
                return;
            }

            // FX
            effects.playFor(target);

            // Success message
            onSuccessMessage.run();

        } catch (Throwable t) {
            state.reset(target.getUniqueId(), action);
            actor.sendMessage(feature.getLocalizationHandler()
                    .getMessage("teleportation.error.internal")
                    .forAudience(actor)
                    .build());
        }
    }

    private boolean checkAndStartCooldown(CommandSender actor, Player target, TeleportAction action) {
        if (actor.hasPermission("serverfeatures.feature.teleportation.bypass.cooldown")) return true;

        long now = System.currentTimeMillis();
        if (state.tryStart(target.getUniqueId(), action, now)) return true;

        long remaining = state.remainingCooldownSeconds(target.getUniqueId(), action, now);
        actor.sendMessage(feature.getLocalizationHandler()
                .getMessage("teleportation.cooldown_active")
                .forAudience(actor)
                .with("seconds", remaining)
                .build());
        return false;
    }
}
