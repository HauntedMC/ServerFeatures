package nl.hauntedmc.serverfeatures.features.parcour.model;

import org.bukkit.Location;
import org.bukkit.Server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class ParcourRegion {
    private final int order; // -1 for START, 0..N for checkpoints, Integer.MAX_VALUE for END
    private ParcourRegionType type;
    private Region region;
    private boolean restoreCheckpoint; // ignored for END
    private final List<String> commands; // executed on enter, as console, {player}

    // NEW: explicit restore location (for START/CHECKPOINT only)
    private String restoreWorld;
    private Double restoreX, restoreY, restoreZ;
    private Float restoreYaw, restorePitch;

    public ParcourRegion(int order, ParcourRegionType type) {
        this.order = order;
        this.type = type;
        this.restoreCheckpoint = false;
        this.commands = new ArrayList<>();
    }

    public int order() { return order; }
    public ParcourRegionType type() { return type; }
    public void setType(ParcourRegionType t) { this.type = t; }

    public Optional<Region> region() { return Optional.ofNullable(region); }
    public void setRegion(Region r) { this.region = r; }

    public boolean restoreCheckpoint() { return type != ParcourRegionType.END && restoreCheckpoint; }
    public void setRestoreCheckpoint(boolean restore) {
        if (type != ParcourRegionType.END) this.restoreCheckpoint = restore;
    }

    public List<String> commands() { return Collections.unmodifiableList(commands); }
    public void addCommand(String cmd) { if (cmd != null && !cmd.isBlank()) commands.add(stripSlash(cmd)); }
    public void clearCommands() { commands.clear(); }

    // NEW: explicit restore location controls (ignored for END)
    public void setExplicitRestore(String world, double x, double y, double z, float yaw, float pitch) {
        if (type == ParcourRegionType.END) return; // not applicable
        this.restoreWorld = world;
        this.restoreX = x; this.restoreY = y; this.restoreZ = z;
        this.restoreYaw = yaw; this.restorePitch = pitch;
    }
    public void clearExplicitRestore() {
        this.restoreWorld = null; this.restoreX = this.restoreY = this.restoreZ = null;
        this.restoreYaw = this.restorePitch = null;
    }
    public boolean hasExplicitRestore() {
        return restoreWorld != null && restoreX != null && restoreY != null && restoreZ != null && restoreYaw != null && restorePitch != null;
    }
    public Optional<Location> explicitRestore(Server server) {
        if (!hasExplicitRestore()) return Optional.empty();
        var world = server.getWorld(restoreWorld);
        if (world == null) return Optional.empty();
        return Optional.of(new Location(world, restoreX, restoreY, restoreZ, restoreYaw, restorePitch));
    }

    // NEW: choose explicit restore if present, else region center (may return null)
    public Location resolveRestoreLocation(Server server) {
        return explicitRestore(server).orElseGet(() -> region != null ? region.center(server) : null);
    }

    private static String stripSlash(String s) {
        return s.startsWith("/") ? s.substring(1) : s;
    }
}
