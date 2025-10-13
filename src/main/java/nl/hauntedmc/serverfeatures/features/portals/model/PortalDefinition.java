package nl.hauntedmc.serverfeatures.features.portals.model;

import org.bukkit.*;

import java.util.Objects;
import java.util.Optional;

public final class PortalDefinition {

    private final String id;
    private Region region;          // required to trigger
    private PortalMode mode;        // TELEPORT, COMMAND or SERVER

    // TELEPORT fields
    private String targetWorld;     // world name
    private double x, y, z;
    private float yaw, pitch;

    // COMMAND fields
    private String command;         // without leading slash
    private CommandExecutor executor;

    // SERVER fields (proxy server to connect to)
    private String targetServer;

    // Exclusive block requirement
    private Material exclusiveBlock;

    // NEW: feedback effects
    private Sound sound;
    private int soundDelay;         // ticks
    private Particle particle;
    private int particleDelay;      // ticks

    public PortalDefinition(String id) {
        this.id = Objects.requireNonNull(id, "id");
        this.mode = PortalMode.TELEPORT; // sensible default
        this.executor = CommandExecutor.CONSOLE;
        this.soundDelay = 0;
        this.particleDelay = 0;
    }

    public String id() {
        return id;
    }

    public Optional<Region> region() {
        return Optional.ofNullable(region);
    }

    public void setRegion(Region region) {
        this.region = region;
    }

    public PortalMode mode() {
        return mode;
    }

    public void setMode(PortalMode mode) {
        this.mode = mode;
    }

    // TELEPORT
    public void setTeleport(String world, double x, double y, double z, float yaw, float pitch) {
        this.targetWorld = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Optional<String> targetWorld() {
        return Optional.ofNullable(targetWorld);
    }

    public double tx() {
        return x;
    }

    public double ty() {
        return y;
    }

    public double tz() {
        return z;
    }

    public float tyaw() {
        return yaw;
    }

    public float tpitch() {
        return pitch;
    }

    // COMMAND
    public void setCommand(String command, CommandExecutor executor) {
        this.command = command;
        this.executor = executor == null ? CommandExecutor.CONSOLE : executor;
    }

    public Optional<String> command() {
        return Optional.ofNullable(command);
    }

    public CommandExecutor executor() {
        return executor;
    }

    // SERVER
    public void setServerTarget(String serverName) {
        this.targetServer = serverName;
    }

    public Optional<String> serverTarget() {
        return Optional.ofNullable(targetServer);
    }

    // Exclusive block
    public void setExclusiveBlock(Material m) {
        this.exclusiveBlock = m;
    }

    public void clearExclusiveBlock() {
        this.exclusiveBlock = null;
    }

    public Optional<Material> exclusiveBlock() {
        return Optional.ofNullable(exclusiveBlock);
    }

    public Optional<World> resolveTargetWorld() {
        if (targetWorld == null) return Optional.empty();
        return Optional.ofNullable(Bukkit.getWorld(targetWorld));
    }

    // sound
    public void setSound(Sound s, int delayTicks) {
        this.sound = s;
        this.soundDelay = Math.max(0, delayTicks);
    }

    public void clearSound() {
        this.sound = null;
        this.soundDelay = 0;
    }

    public Optional<Sound> sound() {
        return Optional.ofNullable(sound);
    }

    public int soundDelay() {
        return soundDelay;
    }

    // particle
    public void setParticle(Particle p, int delayTicks) {
        this.particle = p;
        this.particleDelay = Math.max(0, delayTicks);
    }

    public void clearParticle() {
        this.particle = null;
        this.particleDelay = 0;
    }

    public Optional<Particle> particle() {
        return Optional.ofNullable(particle);
    }

    public int particleDelay() {
        return particleDelay;
    }
}
