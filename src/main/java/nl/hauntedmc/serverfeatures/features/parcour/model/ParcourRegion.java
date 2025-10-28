// File: nl/hauntedmc/serverfeatures/features/parcour/model/ParcourRegion.java
package nl.hauntedmc.serverfeatures.features.parcour.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class ParcourRegion {
    private final int order; // 0 for START, 1..N for checkpoints, N+1 for END
    private ParcourRegionType type;
    private Region region;
    private boolean restoreCheckpoint;
    private final List<String> commands; // executed on enter, as console, {player} placeholder

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

    public boolean restoreCheckpoint() { return restoreCheckpoint; }
    public void setRestoreCheckpoint(boolean restore) { this.restoreCheckpoint = restore; }

    public List<String> commands() { return Collections.unmodifiableList(commands); }
    public void addCommand(String cmd) { if (cmd != null && !cmd.isBlank()) commands.add(stripSlash(cmd)); }
    public void clearCommands() { commands.clear(); }

    private static String stripSlash(String s) {
        return s.startsWith("/") ? s.substring(1) : s;
    }
}
