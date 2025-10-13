package nl.hauntedmc.serverfeatures.features.afk.internal.engine.event;

import nl.hauntedmc.serverfeatures.features.afk.internal.engine.util.Movement;
import org.bukkit.entity.Player;

public final class AfkEvent {
    private final Player player;
    private final AfkEventType type;
    private final long ts;
    private final String payload;
    private final Movement movement;

    private AfkEvent(Player player, AfkEventType type, long ts, String payload, Movement movement) {
        this.player = player;
        this.type = type;
        this.ts = ts;
        this.payload = payload;
        this.movement = movement;
    }

    public Player player() {
        return player;
    }

    public AfkEventType type() {
        return type;
    }

    public long timestamp() {
        return ts;
    }

    public String payload() {
        return payload;
    }

    public Movement movement() {
        return movement;
    }

    public static AfkEvent simple(Player p, AfkEventType t) {
        return new AfkEvent(p, t, System.currentTimeMillis(), null, null);
    }

    public static AfkEvent command(Player p, String raw) {
        return new AfkEvent(p, AfkEventType.COMMAND, System.currentTimeMillis(), raw, null);
    }

    public static AfkEvent move(Player p, Movement m) {
        return new AfkEvent(p, AfkEventType.MOVE, System.currentTimeMillis(), null, m);
    }

    public static AfkEvent teleport(Player p, Movement m) {
        return new AfkEvent(p, AfkEventType.TELEPORT, System.currentTimeMillis(), null, m);
    }
}
