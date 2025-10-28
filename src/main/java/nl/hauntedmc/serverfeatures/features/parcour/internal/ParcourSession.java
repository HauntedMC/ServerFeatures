// File: nl/hauntedmc/serverfeatures/features/parcour/internal/ParcourSession.java
package nl.hauntedmc.serverfeatures.features.parcour.internal;

import nl.hauntedmc.serverfeatures.features.parcour.model.ParcourDefinition;
import nl.hauntedmc.serverfeatures.features.parcour.model.ParcourRegion;
import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ParcourSession {
    public final UUID playerId;
    public final String parcourId;
    public final long startMillis;

    // Expected next order. After START (order 0) -> expect 1; then 2; ...; finally END order
    private int expectedNextOrder;

    // Last restore/respawn location
    private Location restoreLocation;

    // Regions triggered in this session (avoid re-running commands on the same region)
    private final Set<Integer> triggeredOrders = new HashSet<>();

    public ParcourSession(UUID playerId, ParcourDefinition def, Location startRestore, int firstExpectedOrder) {
        this.playerId = playerId;
        this.parcourId = def.id();
        this.startMillis = System.currentTimeMillis();
        this.restoreLocation = startRestore;
        this.expectedNextOrder = firstExpectedOrder;
    }

    public int expectedNextOrder() { return expectedNextOrder; }
    public void advanceExpectedOrder() { expectedNextOrder++; }
    public void setExpectedOrder(int ord) { expectedNextOrder = ord; }

    public Location restoreLocation() { return restoreLocation; }
    public void setRestoreLocation(Location loc) { this.restoreLocation = loc; }

    public boolean markTriggered(ParcourRegion region) {
        return triggeredOrders.add(region.order());
    }

    public boolean alreadyTriggered(ParcourRegion region) {
        return triggeredOrders.contains(region.order());
    }
}
