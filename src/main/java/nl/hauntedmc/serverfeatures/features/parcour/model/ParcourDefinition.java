package nl.hauntedmc.serverfeatures.features.parcour.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

public final class ParcourDefinition {
    private final String id;

    private ParcourRegion start; // START (-1)
    private ParcourRegion end;   // END (Integer.MAX_VALUE)

    // Numbered checkpoints (0..N)
    private final Map<Integer, ParcourRegion> checkpointsByOrder = new TreeMap<>();

    // Exit spawn (for /parcour leave and optional delayed finish-teleport)
    private String exitWorld;
    private double exitX, exitY, exitZ;
    private float exitYaw, exitPitch;

    // Progress notify (chat)
    private boolean notifyProgress;
    private String checkpointSoundName; // org.bukkit.Sound enum name, null = none
    private String endSoundName;        // org.bukkit.Sound enum name, null = none
    private boolean useActionBar;
    private int finishTeleportDelaySeconds;
    private String regionHighlightParticleName;
    private boolean hungerEnabled = true;
    private boolean damageEnabled = true;
    private int checkpointCooldownSeconds = 3;
    private final List<String> startKitEncoded = new ArrayList<>();

    public ParcourDefinition(String id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public String id() { return id; }

    // ===== START / END =====
    public Optional<ParcourRegion> startRegion() { return Optional.ofNullable(start); }
    public void setStartRegion(ParcourRegion r) { this.start = r; }
    public boolean clearStartRegion() { boolean had = this.start != null; this.start = null; return had; }

    public Optional<ParcourRegion> endRegion() { return Optional.ofNullable(end); }
    public void setEndRegion(ParcourRegion r) { this.end = r; }
    public boolean clearEndRegion() { boolean had = this.end != null; this.end = null; return had; }

    // ===== Checkpoints =====
    public Collection<ParcourRegion> checkpoints() {
        return new ArrayList<>(checkpointsByOrder.values());
    }

    public Optional<ParcourRegion> checkpoint(int order) {
        return Optional.ofNullable(checkpointsByOrder.get(order));
    }

    public void putCheckpoint(ParcourRegion r) {
        if (r.type() != ParcourRegionType.CHECKPOINT) throw new IllegalArgumentException("not a checkpoint");
        checkpointsByOrder.put(r.order(), r);
    }

    public boolean removeCheckpoint(int order) {
        return checkpointsByOrder.remove(order) != null;
    }

    public SortedSet<Integer> orders() {
        return new TreeSet<>(checkpointsByOrder.keySet());
    }

    public int totalRegions() {
        return (start != null ? 1 : 0) + (end != null ? 1 : 0) + checkpointsByOrder.size();
    }

    public int totalCheckpoints() {
        return checkpointsByOrder.size();
    }

    // ===== Exit spawn =====
    public void setExitSpawn(String world, double x, double y, double z, float yaw, float pitch) {
        this.exitWorld = world;
        this.exitX = x; this.exitY = y; this.exitZ = z;
        this.exitYaw = yaw; this.exitPitch = pitch;
    }

    public Optional<Location> exitSpawn() {
        if (exitWorld == null) return Optional.empty();
        World w = Bukkit.getWorld(exitWorld);
        if (w == null) return Optional.empty();
        return Optional.of(new Location(w, exitX, exitY, exitZ, exitYaw, exitPitch));
    }

    public Location fallbackWorldSpawn() {
        World w = Bukkit.getWorlds().get(0);
        return w.getSpawnLocation();
    }

    // ===== Progress toggle (chat) =====
    public boolean notifyProgress() { return notifyProgress; }
    public void setNotifyProgress(boolean v) { this.notifyProgress = v; }

    // ===== Sounds (map-level) =====
    public Optional<String> checkpointSoundName() { return Optional.ofNullable(checkpointSoundName); }
    public void setCheckpointSoundName(String name) { this.checkpointSoundName = (name == null || name.isBlank()) ? null : name; }

    public Optional<String> endSoundName() { return Optional.ofNullable(endSoundName); }
    public void setEndSoundName(String name) { this.endSoundName = (name == null || name.isBlank()) ? null : name; }

    // ===== Actionbar toggle =====
    public boolean useActionBar() { return useActionBar; }
    public void setUseActionBar(boolean use) { this.useActionBar = use; }

    // ===== Finish delayed teleport =====
    public int finishTeleportDelaySeconds() { return finishTeleportDelaySeconds; }
    public void setFinishTeleportDelaySeconds(int seconds) { this.finishTeleportDelaySeconds = Math.max(0, seconds); }

    // ===== Region highlight particle =====
    public Optional<String> regionHighlightParticleName() { return Optional.ofNullable(regionHighlightParticleName); }
    public void setRegionHighlightParticleName(String name) { this.regionHighlightParticleName = (name == null || name.isBlank()) ? null : name; }

    // ===== Toggles =====
    public boolean hungerEnabled() { return hungerEnabled; }
    public void setHungerEnabled(boolean v) { this.hungerEnabled = v; }

    public boolean damageEnabled() { return damageEnabled; }
    public void setDamageEnabled(boolean v) { this.damageEnabled = v; }

    // ===== Checkpoint cooldown =====
    public int checkpointCooldownSeconds() { return checkpointCooldownSeconds; }
    public void setCheckpointCooldownSeconds(int seconds) { this.checkpointCooldownSeconds = Math.max(0, seconds); }

    // ===== Start kit (encoded ItemStacks) =====
    public List<String> startKitEncoded() { return Collections.unmodifiableList(startKitEncoded); }
    public void clearStartKit() { startKitEncoded.clear(); }
    public void addStartKitSerialized(String base64) { if (base64 != null && !base64.isBlank()) startKitEncoded.add(base64); }
    public boolean removeStartKitIndex(int oneBasedIndex) {
        int idx = oneBasedIndex - 1;
        if (idx < 0 || idx >= startKitEncoded.size()) return false;
        startKitEncoded.remove(idx);
        return true;
    }
}
