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
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

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

                def.setNotifyProgress(n.get("notify_progress").as(Boolean.class, false));
                def.setUseActionBar(n.get("use_actionbar").as(Boolean.class, false));
                def.setFinishTeleportDelaySeconds(n.get("finish_teleport_delay_seconds").as(Integer.class, 0));
                def.setCheckpointCooldownSeconds(n.get("checkpoint_cooldown_seconds").as(Integer.class, 3));
                def.setStartCountdownSeconds(n.get("start_countdown_seconds").as(Integer.class, 0));

                ConfigNode startPos = n.get("start_position");
                String spw = startPos.get("world").as(String.class, null);
                Double spx = startPos.get("x").as(Double.class, null);
                Double spy = startPos.get("y").as(Double.class, null);
                Double spz = startPos.get("z").as(Double.class, null);
                Float spyaw = startPos.get("yaw").as(Float.class, null);
                Float sppitch = startPos.get("pitch").as(Float.class, null);
                if (spw != null && spx != null && spy != null && spz != null && spyaw != null && sppitch != null) {
                    def.setStartPosition(spw, spx, spy, spz, spyaw, sppitch);
                }

                ConfigNode sounds = n.get("sounds");
                String cpSound = sounds.get("checkpoint").as(String.class, null);
                String endSound = sounds.get("end").as(String.class, null);
                if (isValidSound(cpSound, log, id, "checkpoint")) def.setCheckpointSoundName(cpSound);
                if (isValidSound(endSound, log, id, "end")) def.setEndSoundName(endSound);

                String particleName = n.get("region_highlight_particle").as(String.class, null);
                if (isValidParticle(particleName, log, id)) {
                    def.setRegionHighlightParticleName(particleName);
                }

                def.setHungerEnabled(n.get("hunger_enabled").as(Boolean.class, true));
                def.setDamageEnabled(n.get("damage_enabled").as(Boolean.class, true));

                List<String> kit = n.get("start_kit").listOf(String.class);
                if (kit != null) {
                    for (String b64 : kit) {
                        if (b64 != null && !b64.isBlank()) def.addStartKitSerialized(b64);
                    }
                }

                // Leave / Finish locations (optional) + backward-compat
                ConfigNode leave = n.get("leave_location");
                String lw = leave.get("world").as(String.class, null);
                Double lx = leave.get("x").as(Double.class, null);
                Double ly = leave.get("y").as(Double.class, null);
                Double lz = leave.get("z").as(Double.class, null);
                Float lyaw = leave.get("yaw").as(Float.class, 0f);
                Float lpitch = leave.get("pitch").as(Float.class, 0f);
                if (lw != null && lx != null && ly != null && lz != null) {
                    def.setLeaveSpawn(lw, lx, ly, lz, lyaw, lpitch);
                }

                ConfigNode finish = n.get("finish_location");
                String fw = finish.get("world").as(String.class, null);
                Double fx = finish.get("x").as(Double.class, null);
                Double fy = finish.get("y").as(Double.class, null);
                Double fz = finish.get("z").as(Double.class, null);
                Float fyaw = finish.get("yaw").as(Float.class, 0f);
                Float fpitch = finish.get("pitch").as(Float.class, 0f);
                if (fw != null && fx != null && fy != null && fz != null) {
                    def.setFinishSpawn(fw, fx, fy, fz, fyaw, fpitch);
                }

                // Legacy: exit_spawn
                ConfigNode legacyExit = n.get("exit_spawn");
                String ew = legacyExit.get("world").as(String.class, null);
                Double ex = legacyExit.get("x").as(Double.class, null);
                Double ey = legacyExit.get("y").as(Double.class, null);
                Double ez = legacyExit.get("z").as(Double.class, null);
                Float eyaw = legacyExit.get("yaw").as(Float.class, 0f);
                Float epitch = legacyExit.get("pitch").as(Float.class, 0f);
                if (ew != null && ex != null && ey != null && ez != null) {
                    if (def.leaveSpawn().isEmpty()) {
                        def.setLeaveSpawn(ew, ex, ey, ez, eyaw, epitch);
                    }
                    if (def.finishSpawn().isEmpty()) {
                        def.setFinishSpawn(ew, ex, ey, ez, eyaw, epitch);
                    }
                }

                // Regions
                ConfigNode rs = n.get("regions");

                ParcourRegion start = readRegionKey(rs, "START", ParcourRegionType.START, -1, log, id);
                if (start != null) def.setStartRegion(start);

                ParcourRegion end = readRegionKey(rs, "END", ParcourRegionType.END, Integer.MAX_VALUE, log, id);
                if (end != null) def.setEndRegion(end);

                Map<String, ConfigNode> rChildren = rs.children();
                for (Map.Entry<String, ConfigNode> rc : rChildren.entrySet()) {
                    String key = rc.getKey();
                    if ("START".equalsIgnoreCase(key) || "END".equalsIgnoreCase(key)) continue;
                    Integer order = parseOrder(key);
                    if (order == null) continue;

                    ParcourRegion cp = readRegion(rc.getValue(), ParcourRegionType.CHECKPOINT, order, log, id, key);
                    if (cp != null) def.putCheckpoint(cp);
                }

                boolean valid = def.startRegion().isPresent()
                        && def.startRegion().get().region().isPresent()
                        && def.endRegion().isPresent()
                        && def.endRegion().get().region().isPresent();

                if (!valid) {
                    log.warning("Parcour '" + id + "' is invalid: missing START and/or END region. It will not be loaded.");
                    continue;
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
        try { Sound.valueOf(name.trim().toUpperCase(Locale.ROOT)); return true; }
        catch (IllegalArgumentException ex) {
            log.warning("Parcour '" + parcourId + "': invalid " + kind + " sound '" + name + "'. Ignoring.");
            return false;
        }
    }

    private static boolean isValidParticle(String name, FeatureLogger log, String parcourId) {
        if (name == null || name.isBlank()) return false;
        try { Particle.valueOf(name.trim().toUpperCase(Locale.ROOT)); return true; }
        catch (IllegalArgumentException ex) {
            log.warning("Parcour '" + parcourId + "': invalid region_highlight_particle '" + name + "'. Ignoring.");
            return false;
        }
    }

    private static Integer parseOrder(String k) {
        try { return Integer.parseInt(k); } catch (NumberFormatException e) { return null; }
    }

    private ParcourRegion readRegionKey(ConfigNode parent, String key, ParcourRegionType type, int orderForInternal, FeatureLogger log, String parcourId) {
        ConfigNode node = parent.get(key);
        if (node.isNull()) return null;
        return readRegion(node, type, orderForInternal, log, parcourId, key);
    }

    private ParcourRegion readRegion(ConfigNode node, ParcourRegionType type, int orderForInternal, FeatureLogger log, String parcourId, String key) {
        try {
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

            ParcourRegion pr = new ParcourRegion(orderForInternal, ParcourRegionType.CHECKPOINT == type ? ParcourRegionType.CHECKPOINT : type);
            pr.setRestoreCheckpoint(restore);
            if (region != null) pr.setRegion(region);

            List<String> cmds = node.get("commands").listOf(String.class);
            if (cmds != null) for (String c : cmds) pr.addCommand(c);

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
            b.put(base + ".notify_progress", def.notifyProgress());
            b.put(base + ".use_actionbar", def.useActionBar());
            b.put(base + ".finish_teleport_delay_seconds", def.finishTeleportDelaySeconds());
            b.put(base + ".checkpoint_cooldown_seconds", def.checkpointCooldownSeconds());
            b.put(base + ".start_countdown_seconds", def.startCountdownSeconds());

            def.startPosition().ifPresent(l -> {
                b.put(base + ".start_position.world", l.getWorld().getName());
                b.put(base + ".start_position.x", l.getX());
                b.put(base + ".start_position.y", l.getY());
                b.put(base + ".start_position.z", l.getZ());
                b.put(base + ".start_position.yaw", l.getYaw());
                b.put(base + ".start_position.pitch", l.getPitch());
            });

            def.checkpointSoundName().ifPresent(name -> b.put(base + ".sounds.checkpoint", name));
            def.endSoundName().ifPresent(name -> b.put(base + ".sounds.end", name));
            def.regionHighlightParticleName().ifPresent(name -> b.put(base + ".region_highlight_particle", name));

            b.put(base + ".hunger_enabled", def.hungerEnabled());
            b.put(base + ".damage_enabled", def.damageEnabled());
            b.put(base + ".start_kit", def.startKitEncoded());

            def.leaveSpawn().ifPresent(l -> {
                b.put(base + ".leave_location.world", l.getWorld().getName());
                b.put(base + ".leave_location.x", l.getX());
                b.put(base + ".leave_location.y", l.getY());
                b.put(base + ".leave_location.z", l.getZ());
                b.put(base + ".leave_location.yaw", l.getYaw());
                b.put(base + ".leave_location.pitch", l.getPitch());
            });

            def.finishSpawn().ifPresent(l -> {
                b.put(base + ".finish_location.world", l.getWorld().getName());
                b.put(base + ".finish_location.x", l.getX());
                b.put(base + ".finish_location.y", l.getY());
                b.put(base + ".finish_location.z", l.getZ());
                b.put(base + ".finish_location.yaw", l.getYaw());
                b.put(base + ".finish_location.pitch", l.getPitch());
            });

            b.remove(base + ".regions");

            def.startRegion().ifPresent(pr -> writeRegion(b, base + ".regions.START", pr));
            for (Integer ord : def.orders()) {
                ParcourRegion pr = def.checkpoint(ord).orElse(null);
                if (pr == null) continue;
                writeRegion(b, base + ".regions." + ord, pr);
            }
            def.endRegion().ifPresent(pr -> writeRegion(b, base + ".regions.END", pr));

            b.remove(base + ".exit_spawn");
        });

        byId.put(keyId.toLowerCase(Locale.ROOT), def);
    }

    private void writeRegion(FeatureConfigHandler.FeatureBatch b, String path, ParcourRegion pr) {
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
            b.remove(base + ".leave_location");
            b.remove(base + ".finish_location");
            b.remove(base + ".regions");
            b.remove(base + ".notify_progress");
            b.remove(base + ".use_actionbar");
            b.remove(base + ".finish_teleport_delay_seconds");
            b.remove(base + ".checkpoint_cooldown_seconds");
            b.remove(base + ".start_countdown_seconds");
            b.remove(base + ".start_position");
            b.remove(base + ".sounds");
            b.remove(base + ".region_highlight_particle");
            b.remove(base + ".hunger_enabled");
            b.remove(base + ".damage_enabled");
            b.remove(base + ".start_kit");
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

    public int size() { return byId.size(); }

    // ====== Item (de)serialization helpers for start kit ======
    public Optional<String> serializeItemToBase64(ItemStack item) {
        if (item == null) return Optional.empty();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
            oos.writeObject(item);
            oos.flush();
            return Optional.of(Base64.getEncoder().encodeToString(baos.toByteArray()));
        } catch (IOException ex) {
            feature.getLogger().warning("Failed to serialize start kit item: " + ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<ItemStack> deserializeItemFromBase64(String base64) {
        if (base64 == null || base64.isBlank()) return Optional.empty();
        byte[] data;
        try { data = Base64.getDecoder().decode(base64); }
        catch (IllegalArgumentException e) {
            feature.getLogger().warning("Invalid base64 for start kit item.");
            return Optional.empty();
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {
            Object obj = ois.readObject();
            if (obj instanceof ItemStack is) return Optional.of(is);
            feature.getLogger().warning("Deserialized object is not an ItemStack.");
            return Optional.empty();
        } catch (IOException | ClassNotFoundException ex) {
            feature.getLogger().warning("Failed to deserialize start kit item: " + ex.getMessage());
            return Optional.empty();
        }
    }

    // ====== NEW: clears for unified set<>location ... clear UX ======

    public boolean clearLeaveLocation(String id) {
        Optional<ParcourDefinition> defOpt = get(id);
        if (defOpt.isEmpty()) return false;
        String realId = defOpt.get().id();
        String base = "parcours." + realId;
        feature.getConfigHandler().batch(b -> b.remove(base + ".leave_location"));
        // Reload to ensure in-memory defs reflect cleared state
        reloadFromConfig();
        return true;
    }

    public boolean clearFinishLocation(String id) {
        Optional<ParcourDefinition> defOpt = get(id);
        if (defOpt.isEmpty()) return false;
        String realId = defOpt.get().id();
        String base = "parcours." + realId;
        feature.getConfigHandler().batch(b -> b.remove(base + ".finish_location"));
        reloadFromConfig();
        return true;
    }

    public boolean clearRegionRestoreLocation(String id, String key) {
        Optional<ParcourDefinition> defOpt = get(id);
        if (defOpt.isEmpty()) return false;
        ParcourDefinition def = defOpt.get();

        // Resolve exact casing for path (START/END/<number>)
        final String pathKey;
        if ("START".equalsIgnoreCase(key)) pathKey = "START";
        else if ("END".equalsIgnoreCase(key)) pathKey = "END";
        else {
            Integer ord = parseOrder(key);
            if (ord == null) return false;
            if (def.checkpoint(ord).isEmpty()) return false;
            pathKey = String.valueOf(ord);
        }

        String base = "parcours." + def.id() + ".regions." + pathKey + ".restore_location";
        feature.getConfigHandler().batch(b -> b.remove(base));
        reloadFromConfig();
        return true;
    }
}
