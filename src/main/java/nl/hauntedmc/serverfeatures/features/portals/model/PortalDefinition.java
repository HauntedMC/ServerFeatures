package nl.hauntedmc.serverfeatures.features.portals.model;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;

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

    // NEW: exclusive block requirement
    private Material exclusiveBlock;

    public PortalDefinition(String id) {
        this.id = Objects.requireNonNull(id, "id");
        this.mode = PortalMode.TELEPORT; // sensible default
        this.executor = CommandExecutor.CONSOLE;
    }

    public String id() { return id; }

    public Optional<Region> region() { return Optional.ofNullable(region); }
    public void setRegion(Region region) { this.region = region; }

    public PortalMode mode() { return mode; }
    public void setMode(PortalMode mode) { this.mode = mode; }

    // TELEPORT
    public void setTeleport(String world, double x, double y, double z, float yaw, float pitch) {
        this.targetWorld = world;
        this.x = x; this.y = y; this.z = z;
        this.yaw = yaw; this.pitch = pitch;
    }
    public Optional<String> targetWorld() { return Optional.ofNullable(targetWorld); }
    public double tx() { return x; }
    public double ty() { return y; }
    public double tz() { return z; }
    public float tyaw() { return yaw; }
    public float tpitch() { return pitch; }

    // COMMAND
    public void setCommand(String command, CommandExecutor executor) {
        this.command = command;
        this.executor = executor == null ? CommandExecutor.CONSOLE : executor;
    }
    public Optional<String> command() { return Optional.ofNullable(command); }
    public CommandExecutor executor() { return executor; }

    // SERVER
    public void setServerTarget(String serverName) { this.targetServer = serverName; }
    public Optional<String> serverTarget() { return Optional.ofNullable(targetServer); }

    public void setExclusiveBlock(Material m) { this.exclusiveBlock = m; }
    public void clearExclusiveBlock() { this.exclusiveBlock = null; }
    public Optional<Material> exclusiveBlock() { return Optional.ofNullable(exclusiveBlock); }

    public Optional<World> resolveTargetWorld() {
        if (targetWorld == null) return Optional.empty();
        return Optional.ofNullable(Bukkit.getWorld(targetWorld));
    }
}
