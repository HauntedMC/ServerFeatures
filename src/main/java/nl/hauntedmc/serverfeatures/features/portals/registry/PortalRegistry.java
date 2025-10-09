package nl.hauntedmc.serverfeatures.features.portals.registry;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.features.portals.Portals;
import nl.hauntedmc.serverfeatures.features.portals.model.CommandExecutor;
import nl.hauntedmc.serverfeatures.features.portals.model.PortalDefinition;
import nl.hauntedmc.serverfeatures.features.portals.model.PortalMode;
import nl.hauntedmc.serverfeatures.features.portals.model.Region;
import nl.hauntedmc.serverfeatures.features.portals.util.RegistryUtil;
import nl.hauntedmc.serverfeatures.internal.FeatureLogger;
import org.bukkit.Material;

import java.util.*;

/**
 * Loads/saves portals; stores sound/particle as namespaced keys in config,
 * resolves via Paper registry at runtime (no deprecated enum calls).
 */
public final class PortalRegistry {

    private final Portals feature;
    private final Map<String, PortalDefinition> byId = new LinkedHashMap<>();

    public PortalRegistry(Portals feature) {
        this.feature = feature;
    }

    public void reloadFromConfig() {
        byId.clear();

        ConfigNode root = feature.getConfigHandler().node("portals");
        Map<String, ConfigNode> children = root.children();
        FeatureLogger log = feature.getLogger();

        int loaded = 0;
        for (Map.Entry<String, ConfigNode> e : children.entrySet()) {
            String rawId = e.getKey();
            String id = rawId.trim();
            if (id.isEmpty()) continue;

            try {
                PortalDefinition def = new PortalDefinition(id);

                ConfigNode n = e.getValue();
                String mode = n.get("mode").as(String.class, "TELEPORT");
                def.setMode(PortalMode.valueOf(mode.toUpperCase(Locale.ROOT)));

                // region
                ConfigNode r = n.get("region");
                String w = r.get("world").as(String.class, null);
                Integer x1 = r.get("x1").as(Integer.class, null);
                Integer y1 = r.get("y1").as(Integer.class, null);
                Integer z1 = r.get("z1").as(Integer.class, null);
                Integer x2 = r.get("x2").as(Integer.class, null);
                Integer y2 = r.get("y2").as(Integer.class, null);
                Integer z2 = r.get("z2").as(Integer.class, null);
                if (w != null && x1 != null && y1 != null && z1 != null && x2 != null && y2 != null && z2 != null) {
                    def.setRegion(new Region(w, x1, y1, z1, x2, y2, z2));
                }

                // teleport
                ConfigNode t = n.get("teleport");
                String tw = t.get("world").as(String.class, null);
                Double tx = t.get("x").as(Double.class, null);
                Double ty = t.get("y").as(Double.class, null);
                Double tz = t.get("z").as(Double.class, null);
                Float yaw = t.get("yaw").as(Float.class, 0f);
                Float pitch = t.get("pitch").as(Float.class, 0f);
                if (tw != null && tx != null && ty != null && tz != null) {
                    def.setTeleport(tw, tx, ty, tz, yaw, pitch);
                }

                // command
                ConfigNode c = n.get("command");
                String cmd = c.get("value").as(String.class, null);
                String ex = c.get("executor").as(String.class, "CONSOLE");
                if (cmd != null) {
                    def.setCommand(cmd, CommandExecutor.fromString(ex, CommandExecutor.CONSOLE));
                }

                // server
                ConfigNode s = n.get("server");
                String targetServer = s.get("name").as(String.class, null);
                if (targetServer != null && !targetServer.isBlank()) {
                    def.setServerTarget(targetServer);
                }

                // exclusive block
                String blockName = n.get("exclusive_block").as(String.class, null);
                if (blockName != null && !blockName.isBlank()) {
                    Material m = Material.matchMaterial(blockName.toUpperCase(Locale.ROOT));
                    if (m != null && m.isBlock() && !m.isAir()) {
                        def.setExclusiveBlock(m);
                    } else {
                        log.warning("Portal '" + id + "' has invalid exclusive_block: " + blockName);
                    }
                }

                // sound (namespaced key)
                ConfigNode snd = n.get("sound");
                String soundKey = snd.get("name").as(String.class, null);
                Integer soundDelay = snd.get("delay").as(Integer.class, 0);
                RegistryUtil.resolveSound(soundKey).ifPresent(sndVal -> def.setSound(sndVal, Math.max(0, soundDelay)));

                // particle (namespaced key)
                ConfigNode part = n.get("particle");
                String particleKey = part.get("name").as(String.class, null);
                Integer particleDelay = part.get("delay").as(Integer.class, 0);
                RegistryUtil.resolveParticle(particleKey).ifPresent(pVal -> def.setParticle(pVal, Math.max(0, particleDelay)));

                byId.put(id.toLowerCase(Locale.ROOT), def);
                loaded++;
            } catch (Exception ex) {
                log.warning("Failed to load portal '" + rawId + "': " + ex.getMessage());
            }
        }
        feature.getLogger().info("Loaded " + loaded + " portal(s).");
    }

    public void savePortal(PortalDefinition def) {
        var cfg = feature.getConfigHandler();
        String keyId = def.id().toLowerCase(Locale.ROOT);
        String base = "portals." + keyId;

        cfg.batch(b -> {
            b.put(base + ".mode", def.mode().name());

            // region
            def.region().ifPresent(r -> {
                b.put(base + ".region.world", r.worldName());
                b.put(base + ".region.x1", r.minX());
                b.put(base + ".region.y1", r.minY());
                b.put(base + ".region.z1", r.minZ());
                b.put(base + ".region.x2", r.maxX());
                b.put(base + ".region.y2", r.maxY());
                b.put(base + ".region.z2", r.maxZ());
            });

            // teleport
            def.targetWorld().ifPresent(w -> {
                b.put(base + ".teleport.world", w);
                b.put(base + ".teleport.x", def.tx());
                b.put(base + ".teleport.y", def.ty());
                b.put(base + ".teleport.z", def.tz());
                b.put(base + ".teleport.yaw", def.tyaw());
                b.put(base + ".teleport.pitch", def.tpitch());
            });

            // command
            def.command().ifPresent(cmd -> {
                b.put(base + ".command.value", cmd);
                b.put(base + ".command.executor", def.executor().name());
            });

            // server
            def.serverTarget().ifPresent(srv -> b.put(base + ".server.name", srv));

            // exclusive block
            def.exclusiveBlock().ifPresentOrElse(
                    mat -> b.put(base + ".exclusive_block", mat.name()),
                    () -> b.remove(base + ".exclusive_block")
            );

            // sound
            def.sound().ifPresentOrElse(
                    s -> {
                        String k = RegistryUtil.keyString(s);
                        if (!k.equals("<unregistered>")) {
                            b.put(base + ".sound.name", k);
                            b.put(base + ".sound.delay", def.soundDelay());
                        } else {
                            b.remove(base + ".sound");
                        }
                    },
                    () -> b.remove(base + ".sound")
            );

            // particle
            def.particle().ifPresentOrElse(
                    p -> {
                        String k = RegistryUtil.keyString(p);
                        if (!k.equals("<unregistered>")) {
                            b.put(base + ".particle.name", k);
                            b.put(base + ".particle.delay", def.particleDelay());
                        } else {
                            b.remove(base + ".particle");
                        }
                    },
                    () -> b.remove(base + ".particle")
            );
        });

        byId.put(keyId, def);
    }

    public boolean deletePortal(String id) {
        if (id == null || id.isBlank()) return false;
        String keyLower = id.toLowerCase(Locale.ROOT);
        byId.remove(keyLower);
        String base = "portals." + keyLower;

        feature.getConfigHandler().batch(b -> {
            b.remove(base + ".mode");
            b.remove(base + ".region");
            b.remove(base + ".teleport");
            b.remove(base + ".command");
            b.remove(base + ".server");
            b.remove(base + ".exclusive_block");
            b.remove(base + ".sound");
            b.remove(base + ".particle");
            b.remove(base);
        });

        return true;
    }

    public Optional<PortalDefinition> get(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<PortalDefinition> all() { return List.copyOf(byId.values()); }
    public int size() { return byId.size(); }
}
