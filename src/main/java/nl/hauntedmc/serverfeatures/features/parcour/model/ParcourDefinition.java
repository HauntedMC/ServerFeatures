package nl.hauntedmc.serverfeatures.features.parcour.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

public final class ParcourDefinition {
    private final String id;

    private ParcourRegion start;
    private ParcourRegion end;

    private final Map<Integer, ParcourRegion> checkpointsByOrder = new TreeMap<>();

    private String leaveWorld;
    private double leaveX, leaveY, leaveZ;
    private float leaveYaw, leavePitch;

    private String finishWorld;
    private double finishX, finishY, finishZ;
    private float finishYaw, finishPitch;

    private boolean notifyProgress;
    private String checkpointSoundName;
    private String endSoundName;
    private boolean useActionBar;
    private int finishTeleportDelaySeconds;
    private String regionHighlightParticleName;
    private boolean hungerEnabled = true;
    private boolean damageEnabled = true;
    private int checkpointCooldownSeconds = 3;

    private int startCountdownSeconds = 0;

    private String startPosWorld;
    private double startPosX, startPosY, startPosZ;
    private float startPosYaw, startPosPitch;

    private final List<String> startKitEncoded = new ArrayList<>();

    private String effectTypeName; // e.g. "SPEED"
    private int effectAmplifier = 0;

    public ParcourDefinition(String id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public String id() {
        return id;
    }

    public Optional<ParcourRegion> startRegion() {
        return Optional.ofNullable(start);
    }

    public void setStartRegion(ParcourRegion r) {
        this.start = r;
    }

    public boolean clearStartRegion() {
        boolean had = this.start != null;
        this.start = null;
        return had;
    }

    public Optional<ParcourRegion> endRegion() {
        return Optional.ofNullable(end);
    }

    public void setEndRegion(ParcourRegion r) {
        this.end = r;
    }

    public boolean clearEndRegion() {
        boolean had = this.end != null;
        this.end = null;
        return had;
    }

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

    public void setLeaveSpawn(String world, double x, double y, double z, float yaw, float pitch) {
        this.leaveWorld = world;
        this.leaveX = x;
        this.leaveY = y;
        this.leaveZ = z;
        this.leaveYaw = yaw;
        this.leavePitch = pitch;
    }

    public Optional<Location> leaveSpawn() {
        if (leaveWorld == null) return Optional.empty();
        World w = Bukkit.getWorld(leaveWorld);
        if (w == null) return Optional.empty();
        return Optional.of(new Location(w, leaveX, leaveY, leaveZ, leaveYaw, leavePitch));
    }

    public void setFinishSpawn(String world, double x, double y, double z, float yaw, float pitch) {
        this.finishWorld = world;
        this.finishX = x;
        this.finishY = y;
        this.finishZ = z;
        this.finishYaw = yaw;
        this.finishPitch = pitch;
    }

    public Optional<Location> finishSpawn() {
        if (finishWorld == null) return Optional.empty();
        World w = Bukkit.getWorld(finishWorld);
        if (w == null) return Optional.empty();
        return Optional.of(new Location(w, finishX, finishY, finishZ, finishYaw, finishPitch));
    }

    public Location fallbackWorldSpawn() {
        World w = Bukkit.getWorlds().getFirst();
        return w.getSpawnLocation();
    }

    public boolean notifyProgress() {
        return notifyProgress;
    }

    public void setNotifyProgress(boolean v) {
        this.notifyProgress = v;
    }

    public Optional<String> checkpointSoundName() {
        return Optional.ofNullable(checkpointSoundName);
    }

    public void setCheckpointSoundName(String name) {
        this.checkpointSoundName = (name == null || name.isBlank()) ? null : name;
    }

    public Optional<String> endSoundName() {
        return Optional.ofNullable(endSoundName);
    }

    public void setEndSoundName(String name) {
        this.endSoundName = (name == null || name.isBlank()) ? null : name;
    }

    public boolean useActionBar() {
        return useActionBar;
    }

    public void setUseActionBar(boolean use) {
        this.useActionBar = use;
    }

    public int finishTeleportDelaySeconds() {
        return finishTeleportDelaySeconds;
    }

    public void setFinishTeleportDelaySeconds(int seconds) {
        this.finishTeleportDelaySeconds = Math.max(0, seconds);
    }

    public Optional<String> regionHighlightParticleName() {
        return Optional.ofNullable(regionHighlightParticleName);
    }

    public void setRegionHighlightParticleName(String name) {
        this.regionHighlightParticleName = (name == null || name.isBlank()) ? null : name;
    }

    public boolean hungerEnabled() {
        return hungerEnabled;
    }

    public void setHungerEnabled(boolean v) {
        this.hungerEnabled = v;
    }

    public boolean damageEnabled() {
        return damageEnabled;
    }

    public void setDamageEnabled(boolean v) {
        this.damageEnabled = v;
    }

    public int checkpointCooldownSeconds() {
        return checkpointCooldownSeconds;
    }

    public void setCheckpointCooldownSeconds(int seconds) {
        this.checkpointCooldownSeconds = Math.max(0, seconds);
    }

    public int startCountdownSeconds() {
        return startCountdownSeconds;
    }

    public void setStartCountdownSeconds(int seconds) {
        this.startCountdownSeconds = Math.max(0, seconds);
    }

    public void setStartPosition(String world, double x, double y, double z, float yaw, float pitch) {
        this.startPosWorld = world;
        this.startPosX = x;
        this.startPosY = y;
        this.startPosZ = z;
        this.startPosYaw = yaw;
        this.startPosPitch = pitch;
    }

    public void clearStartPosition() {
        this.startPosWorld = null;
    }

    public Optional<Location> startPosition() {
        if (startPosWorld == null) return Optional.empty();
        World w = Bukkit.getWorld(startPosWorld);
        if (w == null) return Optional.empty();
        return Optional.of(new Location(w, startPosX, startPosY, startPosZ, startPosYaw, startPosPitch));
    }

    public List<String> startKitEncoded() {
        return Collections.unmodifiableList(startKitEncoded);
    }

    public void clearStartKit() {
        startKitEncoded.clear();
    }

    public void addStartKitSerialized(String base64) {
        if (base64 != null && !base64.isBlank()) startKitEncoded.add(base64);
    }

    public boolean removeStartKitIndex(int oneBasedIndex) {
        int idx = oneBasedIndex - 1;
        if (idx < 0 || idx >= startKitEncoded.size()) return false;
        startKitEncoded.remove(idx);
        return true;
    }

    public Optional<String> effectTypeName() {
        return Optional.ofNullable(effectTypeName);
    }

    public int effectAmplifier() {
        return effectAmplifier;
    }

    public void setEffect(String typeNameOrNull, Integer amplifierOrNull) {
        if (typeNameOrNull == null || typeNameOrNull.isBlank()) {
            this.effectTypeName = null;
            this.effectAmplifier = 0;
        } else {
            this.effectTypeName = typeNameOrNull.trim().toUpperCase(java.util.Locale.ROOT);
            this.effectAmplifier = Math.max(0, amplifierOrNull == null ? 0 : amplifierOrNull);
        }
    }
}
