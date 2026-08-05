package nl.hauntedmc.serverfeatures.features.combattag.event;

import nl.hauntedmc.serverfeatures.api.combat.CombatTagResult;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired synchronously after CombatTag has successfully created or refreshed a player's tag.
 */
public final class CombatTagAppliedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final CombatTagResult result;

    public CombatTagAppliedEvent(Player player, CombatTagResult result) {
        this.player = Objects.requireNonNull(player, "player");
        this.result = Objects.requireNonNull(result, "result");
        if (result != CombatTagResult.TAGGED && result != CombatTagResult.RETAGGED) {
            throw new IllegalArgumentException("Result must represent an applied combat tag: " + result);
        }
    }

    public Player getPlayer() {
        return player;
    }

    public CombatTagResult getResult() {
        return result;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
