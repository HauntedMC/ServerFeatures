package nl.hauntedmc.serverfeatures.features.parcour.registry;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.features.parcour.Parcour;
import nl.hauntedmc.serverfeatures.features.parcour.model.ParcourDefinition;
import nl.hauntedmc.serverfeatures.features.parcour.model.ParcourRegion;
import nl.hauntedmc.serverfeatures.features.parcour.model.ParcourRegionType;
import nl.hauntedmc.serverfeatures.features.parcour.model.Region;
import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import org.bukkit.Particle;
import org.bukkit.Sound;

import java.util.*;

public final class ParcourRegistry {

    private final Parcour feature;
    private final Map<String, ParcourDefinition> byId = new LinkedHashMap<>();

    public ParcourRegistry(Parcour feature) {
        this.feature = feature;
    }

    public void reloadFromConfig() {
        byId.clear();

        ConfigNode root = feature.getConfigHandler().node("parcours");
        Map<String, ConfigNode> children = root.children();
        FeatureLogger log = feature.getLogger();

        int loaded = 0;
        for (Map.Entry<String, ConfigNode> e : children.entrySet()) {
            String rawId = e.getKey();
            String id = rawId.trim();
            if (id.isEmpty()) continue;

            try {
                ParcourDefinition def = new ParcourDefinition(id);
                ConfigNode n = e.getValue();

                // notify_progress flag (default false)
                def.setNotifyProgress(n.get("notify_progress").as(Boolean.class, false));

                // NEW: use_actionbar toggle (default false)
                def.setUseActionBar(n.get("use_actionbar").as(Boolean.class, false));

                // NEW: finish delayed teleport seconds (default 0 = disabled)
                def.setFinishTeleportDelaySeconds(n.get("finish_teleport_delay_seconds").as(Integer.class, 0));

                // NEW: sounds (map-level)
                ConfigNode sounds = n.get("sounds");
                String cpSound = sounds.get("checkpoint").as(String.class, null);
                String endSound = sounds.get("end").as(String.class, null);

                if (isValidSound(cpSound, log, id, "checkpoint")) def.setCheckpointSoundName(cpSound);
                if (isValidSound(endSound, log, id, "end")) def.setEndSoundName(endSound);

                // NEW: region highlight particle (map-level)
                String particleName = n.get("region_highlight_particle").as(String.class, null);
                if (isValidParticle(particleName, log, id)) {
                    def.setRegionHighlightParticleName(particleName);
                }

                // NEW: per-map toggles (default true)
                def.setHungerEnabled(n.get("hunger_enabled").as(Boolean.class, true));
                def.setDamageEnabled(n.get("damage_enabled").as(Boolean.class, true));

                // Exit spawn (optional)
                ConfigNode exit = n.get("exit_spawn");
                String w = exit.get("world").as(String.class, null);
                Double x = exit.get("x").as(Double.class, null);
                Double y = exit.get("y").as(Double.class, null);
                Double z = exit.get("z").as(Double.class, null);
                Float yaw = exit.get("yaw").as(Float.class, 0f);
                Float pitch = exit.get("pitch").as(Float.class, 0f);
                if (w != null && x != null && y != null && z != null) {
                    def.setExitSpawn(w, x, y, z, yaw, pitch);
                }

                // Regions
                ConfigNode rs = n.get("regions");

                // START
                ParcourRegion start = readRegionKey(rs, "START", ParcourRegionType.START, -1, log, id);
                if (start != null) def.setStartRegion(start);

                // END
                ParcourRegion end = readRegionKey(rs, "END", ParcourRegionType.END, Integer.MAX_VALUE, log, id);
                if (end != null) def.setEndRegion(end);

                // CHECKPOINTS (numeric keys)
                Map<String, ConfigNode> rChildren = rs.children();
                for (Map.Entry<String, ConfigNode> rc : rChildren.entrySet()) {
                    String key = rc.getKey();
                    if ("START".equalsIgnoreCase(key) || "END".equalsIgnoreCase(key)) continue;
                    Integer order = parseOrder(key);
                    if (order == null) continue;

                    ParcourRegion cp = readRegion(rc.getValue(), ParcourRegionType.CHECKPOINT, order, log, id, key);
                    if (cp != null) def.putCheckpoint(cp);
                }

                // Validation: require START and END with defined region
                boolean valid = def.startRegion().isPresent()
                        && def.startRegion().get().region().isPresent()
                        && def.endRegion().isPresent()
                        && def.endRegion().get().region().isPresent();

                if (!valid) {
                    log.warning("Parcour '" + id + "' is invalid: missing START and/or END region. It will not be loaded.");
                    continue; // skip loading invalid parcours
                }

                byId.put(id.toLowerCase(Locale.ROOT), def);
                loaded++;
            } catch (Exception ex) {
                log.warning("Failed to load parcour '" + rawId + "': " + ex.getMessage());
            }
        }
        feature.getLogger().info("Loaded " + loaded + " parcour(s).");
    }

    private static boolean isValidSound(String name, FeatureLogger log, String parcourId, String kind) {
        if (name == null || name.isBlank()) return false;
        try {
            Sound.valueOf(name.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException ex) {
            log.warning("Parcour '" + parcourId + "': invalid " + kind + " sound '" + name + "'. Ignoring.");
            return false;
        }
    }

    private static boolean isValidParticle(String name, FeatureLogger log, String parcourId) {
        if (name == null || name.isBlank()) return false;
        try {
            Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException ex) {
            log.warning("Parcour '" + parcourId + "': invalid region_highlight_particle '" + name + "'. Ignoring.");
            return false;
        }
    }

    private static Integer parseOrder(String k) {
        try { return Integer.parseInt(k); } catch (NumberFormatException e) { return null; }
    }

    private ParcourRegion readRegionKey(ConfigNode parent, String key, ParcourRegionType type, int orderForInternal, FeatureLogger log, String parcourId) {
        ConfigNode node = parent.get(key);
        // if region node is missing, return null
        if (node.isNull()) return null;
        return readRegion(node, type, orderForInternal, log, parcourId, key);
    }

    private ParcourRegion readRegion(ConfigNode node, ParcourRegionType type, int orderForInternal, FeatureLogger log, String parcourId, String key) {
        try {
            // END has no restore flag and no explicit restore location
            boolean restore = (type != ParcourRegionType.END) && node.get("restore").as(Boolean.class, false);

            ConfigNode r = node.get("region");
            String world = r.get("world").as(String.class, null);
            Integer x1 = r.get("x1").as(Integer.class, null);
            Integer y1 = r.get("y1").as(Integer.class, null);
            Integer z1 = r.get("z1").as(Integer.class, null);
            Integer x2 = r.get("x2").as(Integer.class, null);
            Integer y2 = r.get("y2").as(Integer.class, null);
            Integer z2 = r.get("z2").as(Integer.class, null);

            Region region = null;
            if (world != null && x1 != null && y1 != null && z1 != null && x2 != null && y2 != null && z2 != null) {
                region = new Region(world, x1, y1, z1, x2, y2, z2);
            }

            ParcourRegion pr = new ParcourRegion(orderForInternal, type);
            pr.setRestoreCheckpoint(restore);
            if (region != null) pr.setRegion(region);

            // commands (optional)
            List<String> cmds = node.get("commands").listOf(String.class);
            if (cmds != null) {
                for (String c : cmds) pr.addCommand(c);
            }

            // explicit restore location (START/CHECKPOINT only)
            if (type != ParcourRegionType.END) {
                ConfigNode rl = node.get("restore_location");
                String rw = rl.get("world").as(String.class, null);
                Double rx = rl.get("x").as(Double.class, null);
                Double ry = rl.get("y").as(Double.class, null);
                Double rz = rl.get("z").as(Double.class, null);
                Float ryaw = rl.get("yaw").as(Float.class, null);
                Float rpitch = rl.get("pitch").as(Float.class, null);
                if (rw != null && rx != null && ry != null && rz != null && ryaw != null && rpitch != null) {
                    pr.setExplicitRestore(rw, rx, ry, rz, ryaw, rpitch);
                }
            }

            return pr;
        } catch (Exception ex) {
            log.warning("Failed to parse region '" + key + "' for parcour '" + parcourId + "': " + ex.getMessage());
            return null;
        }
    }

    public void saveParcour(ParcourDefinition def) {
        var cfg = feature.getConfigHandler();
        String keyId = def.id();
        String base = "parcours." + keyId;

        cfg.batch(b -> {
            // progress toggle
            b.put(base + ".notify_progress", def.notifyProgress());

            // NEW: use_actionbar
            b.put(base + ".use_actionbar", def.useActionBar());

            // NEW: finish delayed teleport seconds
            b.put(base + ".finish_teleport_delay_seconds", def.finishTeleportDelaySeconds());

            // NEW: sounds
            def.checkpointSoundName().ifPresent(name -> b.put(base + ".sounds.checkpoint", name));
            def.endSoundName().ifPresent(name -> b.put(base + ".sounds.end", name));

            // NEW: region highlight particle
            def.regionHighlightParticleName().ifPresent(name -> b.put(base + ".region_highlight_particle", name));

            // NEW: per-map toggles
            b.put(base + ".hunger_enabled", def.hungerEnabled());
            b.put(base + ".damage_enabled", def.damageEnabled());

            // exit spawn
            def.exitSpawn().ifPresent(l -> {
                b.put(base + ".exit_spawn.world", l.getWorld().getName());
                b.put(base + ".exit_spawn.x", l.getX());
                b.put(base + ".exit_spawn.y", l.getY());
                b.put(base + ".exit_spawn.z", l.getZ());
                b.put(base + ".exit_spawn.yaw", l.getYaw());
                b.put(base + ".exit_spawn.pitch", l.getPitch());
            });

            // clear regions node before re-writing (avoid stale keys)
            b.remove(base + ".regions");

            // START
            def.startRegion().ifPresent(pr -> writeRegion(b, base + ".regions.START", pr));

            // CHECKPOINTS
            for (Integer ord : def.orders()) {
                ParcourRegion pr = def.checkpoint(ord).orElse(null);
                if (pr == null) continue;
                writeRegion(b, base + ".regions." + ord, pr);
            }

            // END
            def.endRegion().ifPresent(pr -> writeRegion(b, base + ".regions.END", pr));
        });

        byId.put(keyId.toLowerCase(Locale.ROOT), def);
    }

    private void writeRegion(FeatureConfigHandler.FeatureBatch b, String path, ParcourRegion pr) {
        // Only START/CHECKPOINT have restore + restore_location
        if (pr.type() != ParcourRegionType.END) {
            b.put(path + ".restore", pr.restoreCheckpoint());
            if (pr.hasExplicitRestore()) {
                var locOpt = pr.explicitRestore(feature.getPlugin().getServer());
                if (locOpt.isPresent()) {
                    var l = locOpt.get();
                    b.put(path + ".restore_location.world", l.getWorld().getName());
                    b.put(path + ".restore_location.x", l.getX());
                    b.put(path + ".restore_location.y", l.getY());
                    b.put(path + ".restore_location.z", l.getZ());
                    b.put(path + ".restore_location.yaw", l.getYaw());
                    b.put(path + ".restore_location.pitch", l.getPitch());
                }
            }
        }

        pr.region().ifPresent(r -> {
            b.put(path + ".region.world", r.worldName());
            b.put(path + ".region.x1", r.minX());
            b.put(path + ".region.y1", r.minY());
            b.put(path + ".region.z1", r.minZ());
            b.put(path + ".region.x2", r.maxX());
            b.put(path + ".region.y2", r.maxY());
            b.put(path + ".region.z2", r.maxZ());
        });
        if (!pr.commands().isEmpty()) {
            b.put(path + ".commands", pr.commands());
        }
    }

    public boolean deleteParcour(String id) {
        if (id == null || id.isBlank()) return false;
        String keyLower = id.toLowerCase(Locale.ROOT);
        byId.remove(keyLower);
        String base = "parcours." + id;

        feature.getConfigHandler().batch(b -> {
            b.remove(base + ".exit_spawn");
            b.remove(base + ".regions");
            b.remove(base + ".notify_progress");
            b.remove(base + ".use_actionbar");
            b.remove(base + ".finish_teleport_delay_seconds");
            b.remove(base + ".sounds");
            b.remove(base + ".region_highlight_particle");
            b.remove(base + ".hunger_enabled");
            b.remove(base + ".damage_enabled");
            b.remove(base);
        });

        return true;
    }

    public Optional<ParcourDefinition> get(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<ParcourDefinition> all() {
        return List.copyOf(byId.values());
    }

    public int size() {
        return byId.size();
    }
}
