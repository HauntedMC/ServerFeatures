package nl.hauntedmc.serverfeatures.features.skins.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after a player's skin has been successfully updated (set or removed).
 * Contains the player, the update type, and the new skin name (canonical).
 */
public class SkinUpdateEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final SkinUpdateType type;
    private final String newSkinName;

    public SkinUpdateEvent(Player player, SkinUpdateType type, String newSkinName) {
        super(); // called on main thread in our service
        this.player = player;
        this.type = type;
        this.newSkinName = newSkinName;
    }

    public Player getPlayer() {
        return player;
    }

    public SkinUpdateType getType() {
        return type;
    }

    /**
     * Canonical Mojang name for the newly-applied skin.
     * For SET: donor's canonical name.
     * For REMOVE: the player's official (own) canonical name from SessionServer.
     */
    public String getNewSkinName() {
        return newSkinName;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
