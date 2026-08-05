package nl.hauntedmc.serverfeatures.features.fairperks.policy;

import nl.hauntedmc.serverfeatures.api.combat.CombatTagApi;
import nl.hauntedmc.serverfeatures.features.fairperks.config.FairPerksSettings;
import nl.hauntedmc.serverfeatures.features.fairperks.model.PerkChangeResult;
import nl.hauntedmc.serverfeatures.features.fairperks.model.PerkType;
import org.bukkit.entity.Player;

import java.util.Objects;

public final class FairPerksPolicy {

    private final FairPerksSettings settings;
    private final HostileEntityClassifier hostileClassifier;
    private final CombatTagApi combatTagApi;

    public FairPerksPolicy(
            FairPerksSettings settings,
            HostileEntityClassifier hostileClassifier,
            CombatTagApi combatTagApi
    ) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.hostileClassifier = Objects.requireNonNull(hostileClassifier, "hostileClassifier");
        this.combatTagApi = Objects.requireNonNull(combatTagApi, "combatTagApi");
    }

    public PerkChangeResult.Status canEnable(Player player, PerkType perk, boolean bypassActivationGuard) {
        if (!allowsGameMode(player, perk)) {
            return PerkChangeResult.Status.GAME_MODE_BLOCKED;
        }
        if (!allowsWorld(player, perk)) {
            return PerkChangeResult.Status.WORLD_BLOCKED;
        }
        if (bypassActivationGuard) {
            return PerkChangeResult.Status.CHANGED;
        }

        FairPerksSettings.ActivationGuardSettings guard = settings.activationGuard();
        if (guard.combatEnabled() && isCombatTagged(player)) {
            return PerkChangeResult.Status.COMBAT_TAGGED;
        }

        if (guard.hostileNearbyEnabled()
                && hostileClassifier.hasNearbyHostileTargeting(
                        player,
                        guard.horizontalRadius(),
                        guard.verticalRadius()
                )) {
            return PerkChangeResult.Status.HOSTILE_NEARBY;
        }
        return PerkChangeResult.Status.CHANGED;
    }

    public boolean isCombatTagged(Player player) {
        return combatTagApi.isTagged(player);
    }

    public boolean allowsEnvironment(Player player, PerkType perk) {
        return allowsGameMode(player, perk) && allowsWorld(player, perk);
    }

    public boolean allowsFairPerksWorld(Player player) {
        return settings.worlds().allows(player.getWorld());
    }

    public boolean allowsGameMode(Player player, PerkType perk) {
        return switch (perk) {
            case FLY -> settings.flight().allowedGameModes().contains(player.getGameMode());
            case GOD -> settings.god().allowedGameModes().contains(player.getGameMode());
        };
    }

    public boolean allowsWorld(Player player, PerkType perk) {
        if (!allowsFairPerksWorld(player)) {
            return false;
        }
        return switch (perk) {
            case FLY -> settings.flight().worlds().allows(player.getWorld());
            case GOD -> settings.god().worlds().allows(player.getWorld());
        };
    }
}
