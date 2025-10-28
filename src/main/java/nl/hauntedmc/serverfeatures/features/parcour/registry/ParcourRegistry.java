package nl.hauntedmc.serverfeatures.features.parcour.registry;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.features.parcour.Parcour;
import nl.hauntedmc.serverfeatures.features.parcour.model.ParcourDefinition;
import nl.hauntedmc.serverfeatures.features.parcour.model.ParcourRegion;
import nl.hauntedmc.serverfeatures.features.parcour.model.ParcourRegionType;
import nl.hauntedmc.serverfeatures.features.parcour.model.Region;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;

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

                // Exit spawn
                ConfigNode ex = n.get("exit_spawn");
                String ew = ex.get("world").as(String.class, null);
                Double exx = ex.get("x").as(Double.class, null);
                Double exy = ex.get("y").as(Double.class, null);
                Double exz = ex.get("z").as(Double.class, null);
                Float eyaw = ex.get("yaw").as(Float.class, 0f);
                Float epitch = ex.get("pitch").as(Float.class, 0f);
                if (ew != null && exx != null && exy != null && exz != null) {
                    def.setExitSpawn(ew, exx, exy, exz, eyaw, epitch);
                }

                // Regions
                ConfigNode regions = n.get("regions");
                Map<String, ConfigNode> regChildren = regions.children();
                for (Map.Entry<String, ConfigNode> rc : regChildren.entrySet()) {
                    String orderStr = rc.getKey();
                    int order;
                    try {
                        order = Integer.parseInt(orderStr);
                    } catch (NumberFormatException nf) {
                        continue;
                    }
                    ConfigNode rn = rc.getValue();
                    String typeStr = rn.get("type").as(String.class, "CHECKPOINT").toUpperCase(Locale.ROOT);
                    ParcourRegionType type = ParcourRegionType.valueOf(typeStr);
                    ParcourRegion pr = new ParcourRegion(order, type);

                    boolean restore = rn.get("restore").as(Boolean.class, false);
                    pr.setRestoreCheckpoint(restore);

                    // region box
                    ConfigNode r = rn.get("region");
                    String w = r.get("world").as(String.class, null);
                    Integer x1 = r.get("x1").as(Integer.class, null);
                    Integer y1 = r.get("y1").as(Integer.class, null);
                    Integer z1 = r.get("z1").as(Integer.class, null);
                    Integer x2 = r.get("x2").as(Integer.class, null);
                    Integer y2 = r.get("y2").as(Integer.class, null);
                    Integer z2 = r.get("z2").as(Integer.class, null);
                    if (w != null && x1 != null && y1 != null && z1 != null && x2 != null && y2 != null && z2 != null) {
                        pr.setRegion(new Region(w, x1, y1, z1, x2, y2, z2));
                    }

                    // commands list
                    List<String> cmds = rn.get("commands").listOf(String.class);
                    if (cmds != null) {
                        for (String c : cmds) pr.addCommand(c);
                    }

                    def.putRegion(pr);
                }

                byId.put(id.toLowerCase(Locale.ROOT), def);
                loaded++;
            } catch (Exception ex) {
                log.warning("Failed to load parcour '" + rawId + "': " + ex.getMessage());
            }
        }
        feature.getLogger().info("Loaded " + loaded + " parcour(s).");
    }

    public void saveParcour(ParcourDefinition def) {
        var cfg = feature.getConfigHandler();
        String keyId = def.id().toLowerCase(Locale.ROOT);
        String base = "parcours." + keyId;

        cfg.batch(b -> {
            // exit spawn
            def.exitSpawn().ifPresent(loc -> {
                b.put(base + ".exit_spawn.world", loc.getWorld().getName());
                b.put(base + ".exit_spawn.x", loc.getX());
                b.put(base + ".exit_spawn.y", loc.getY());
                b.put(base + ".exit_spawn.z", loc.getZ());
                b.put(base + ".exit_spawn.yaw", loc.getYaw());
                b.put(base + ".exit_spawn.pitch", loc.getPitch());
            });

            // regions
            b.remove(base + ".regions"); // reset block
            for (ParcourRegion pr : def.regions()) {
                String rb = base + ".regions." + pr.order();
                b.put(rb + ".type", pr.type().name());
                b.put(rb + ".restore", pr.restoreCheckpoint());
                pr.region().ifPresent(r -> {
                    b.put(rb + ".region.world", r.worldName());
                    b.put(rb + ".region.x1", r.minX());
                    b.put(rb + ".region.y1", r.minY());
                    b.put(rb + ".region.z1", r.minZ());
                    b.put(rb + ".region.x2", r.maxX());
                    b.put(rb + ".region.y2", r.maxY());
                    b.put(rb + ".region.z2", r.maxZ());
                });
                if (!pr.commands().isEmpty()) {
                    b.put(rb + ".commands", new ArrayList<>(pr.commands()));
                }
            }
        });

        byId.put(keyId, def);
    }

    public boolean deleteParcour(String id) {
        if (id == null || id.isBlank()) return false;
        String keyLower = id.toLowerCase(Locale.ROOT);
        byId.remove(keyLower);
        String base = "parcours." + keyLower;

        feature.getConfigHandler().batch(b -> {
            b.remove(base + ".exit_spawn");
            b.remove(base + ".regions");
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
