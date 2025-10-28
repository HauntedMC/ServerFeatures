// File: nl/hauntedmc/serverfeatures/features/parcour/internal/ParcourHandler.java
package nl.hauntedmc.serverfeatures.features.parcour.internal;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.parcour.Parcour;
import nl.hauntedmc.serverfeatures.features.parcour.model.ParcourDefinition;
import nl.hauntedmc.serverfeatures.features.parcour.model.ParcourRegion;
import nl.hauntedmc.serverfeatures.features.parcour.model.ParcourRegionType;
import nl.hauntedmc.serverfeatures.features.parcour.model.Region;
import nl.hauntedmc.serverfeatures.features.parcour.registry.ParcourRegistry;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ParcourHandler {

    private static final long TRIGGER_COOLDOWN_MS = 1000L; // prevent spam
    private static final long TELEPORT_IGNORE_MS = 1000L;  // ignore triggers after controlled teleports
    private static final String WAND_NAME = "§6Parcour Wand";
    private final Parcour feature;
    private final ParcourRegistry registry;
    private final FeatureLogger log;
    private final NamespacedKey wandKey;

    // Per-player selection for editor
    public static final class Selection {
        public String selectedParcourId;
        public Integer pos1x, pos1y, pos1z;
        public Integer pos2x, pos2y, pos2z;
        public String world1, world2;

        public boolean hasBoth() {
            return world1 != null && Objects.equals(world1, world2)
                    && pos1x != null && pos1y != null && pos1z != null
                    && pos2x != null && pos2y != null && pos2z != null;
        }

        public Region toRegionOrNull() {
            if (!hasBoth()) return null;
            return new Region(world1, pos1x, pos1y, pos1z, pos2x, pos2y, pos2z);
        }

        public void clearRegion() {
            world1 = world2 = null;
            pos1x = pos1y = pos1z = pos2x = pos2y = pos2z = null;
        }
    }

    private final Map<UUID, Selection> selections = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTrigger = new ConcurrentHashMap<>();
    private final Map<UUID, Long> ignoreUntil = new ConcurrentHashMap<>();
    private final Map<UUID, ParcourSession> sessions = new ConcurrentHashMap<>();

    public ParcourHandler(Parcour feature, ParcourRegistry registry) {
        this.feature = feature;
        this.registry = registry;
        this.log = feature.getLogger();
        this.wandKey = new NamespacedKey(feature.getPlugin(), "parcour_wand");
    }

    public NamespacedKey wandKey() { return wandKey; }

    // ===== Selections (editor) =====
    public Selection selection(Player p) {
        return selections.computeIfAbsent(p.getUniqueId(), k -> new Selection());
    }

    public void setPos1(Player p, Location loc) {
        var s = selection(p);
        s.world1 = loc.getWorld().getName();
        s.pos1x = loc.getBlockX();
        s.pos1y = loc.getBlockY();
        s.pos1z = loc.getBlockZ();
    }

    public void setPos2(Player p, Location loc) {
        var s = selection(p);
        s.world2 = loc.getWorld().getName();
        s.pos2x = loc.getBlockX();
        s.pos2y = loc.getBlockY();
        s.pos2z = loc.getBlockZ();
    }

    public boolean saveRegion(String parcourId, int order) {
        Optional<ParcourDefinition> defOpt = registry.get(parcourId);
        if (defOpt.isEmpty()) return false;
        ParcourDefinition def = defOpt.get();
        ParcourRegion region = def.regionByOrder(order).orElse(null);
        if (region == null) return false;

        // Selection must have both
        Region r = selections.values().stream()
                .filter(sel -> Objects.equals(sel.selectedParcourId, parcourId) && sel.hasBoth())
                .findFirst()
                .map(Selection::toRegionOrNull)
                .orElse(null);
        if (r == null) return false;

        region.setRegion(r);
        registry.saveParcour(def);
        return true;
    }

    public boolean createParcour(String id) {
        if (registry.get(id).isPresent()) return false;
        registry.saveParcour(new ParcourDefinition(id));
        log.info("Created parcour '" + id + "'");
        return true;
    }

    public boolean deleteParcour(String id) {
        boolean ok = registry.deleteParcour(id);
        if (ok) {
            // drop sessions on this parcour
            sessions.values().removeIf(s -> s.parcourId.equalsIgnoreCase(id));
        }
        return ok;
    }

    public boolean addRegion(String id, ParcourRegionType type, int order, boolean restoreCheckpoint) {
        Optional<ParcourDefinition> defOpt = registry.get(id);
        if (defOpt.isEmpty()) return false;
        ParcourDefinition def = defOpt.get();
        ParcourRegion r = new ParcourRegion(order, type);
        r.setRestoreCheckpoint(restoreCheckpoint);
        def.putRegion(r);
        registry.saveParcour(def);
        return true;
    }

    public boolean removeRegion(String id, int order) {
        Optional<ParcourDefinition> defOpt = registry.get(id);
        if (defOpt.isEmpty()) return false;
        ParcourDefinition def = defOpt.get();
        boolean ok = def.removeRegion(order);
        if (ok) registry.saveParcour(def);
        return ok;
    }

    public boolean setRegionRestore(String id, int order, boolean restore) {
        Optional<ParcourDefinition> defOpt = registry.get(id);
        if (defOpt.isEmpty()) return false;
        ParcourDefinition def = defOpt.get();
        ParcourRegion r = def.regionByOrder(order).orElse(null);
        if (r == null) return false;
        r.setRestoreCheckpoint(restore);
        registry.saveParcour(def);
        return true;
    }

    public boolean addRegionCommand(String id, int order, String cmd) {
        Optional<ParcourDefinition> defOpt = registry.get(id);
        if (defOpt.isEmpty()) return false;
        ParcourDefinition def = defOpt.get();
        ParcourRegion r = def.regionByOrder(order).orElse(null);
        if (r == null) return false;
        r.addCommand(cmd);
        registry.saveParcour(def);
        return true;
    }

    public boolean clearRegionCommands(String id, int order) {
        Optional<ParcourDefinition> defOpt = registry.get(id);
        if (defOpt.isEmpty()) return false;
        ParcourDefinition def = defOpt.get();
        ParcourRegion r = def.regionByOrder(order).orElse(null);
        if (r == null) return false;
        r.clearCommands();
        registry.saveParcour(def);
        return true;
    }

    public boolean setExitSpawn(String id, Location loc) {
        Optional<ParcourDefinition> defOpt = registry.get(id);
        if (defOpt.isEmpty()) return false;
        ParcourDefinition def = defOpt.get();
        def.setExitSpawn(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        registry.saveParcour(def);
        return true;
    }

    public void giveWand(Player p) {
        ItemStack wand = new ItemStack(org.bukkit.Material.BLAZE_ROD, 1);
        ItemMeta meta = wand.getItemMeta();
        meta.displayName(Component.text(WAND_NAME));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        Map<Integer, ItemStack> notStored = p.getInventory().addItem(wand);
        if (!notStored.isEmpty()) {
            p.getWorld().dropItemNaturally(p.getLocation(), wand);
        }
    }

    // ===== Gameplay (sessions & triggers) =====

    public boolean isPlaying(Player p) {
        return sessions.containsKey(p.getUniqueId());
    }

    public Optional<ParcourSession> session(Player p) {
        return Optional.ofNullable(sessions.get(p.getUniqueId()));
    }

    public void clearSession(Player p) {
        sessions.remove(p.getUniqueId());
    }

    public boolean startParcourByCommand(Player p, String id) {
        if (isPlaying(p)) {
            p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.already_playing")
                    .with("name", sessions.get(p.getUniqueId()).parcourId).forAudience(p).build());
            return false;
        }
        Optional<ParcourDefinition> defOpt = registry.get(id);
        if (defOpt.isEmpty()) {
            p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                    .with("name", id).forAudience(p).build());
            return false;
        }
        ParcourDefinition def = defOpt.get();
        var startRegion = def.startRegion().flatMap(ParcourRegion::region);
        if (startRegion.isEmpty()) {
            p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.cannot_start_missing")
                    .with("name", id).forAudience(p).build());
            return false;
        }
        Location startLoc = startRegion.get().center(Bukkit.getServer());
        if (startLoc == null) {
            p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.cannot_start_missing")
                    .with("name", id).forAudience(p).build());
            return false;
        }

        // Teleport to start and start session
        teleportWithIgnore(p, startLoc);
        startSession(p, def, startLoc, 1);
        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.starting")
                .with("name", def.id()).forAudience(p).build());
        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.started_teleport")
                .forAudience(p).build());
        // execute start commands once
        def.startRegion().ifPresent(sr -> executeRegionCommands(p, sr));
        return true;
    }

    public boolean leaveParcour(Player p) {
        ParcourSession s = sessions.remove(p.getUniqueId());
        if (s == null) {
            p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_playing").forAudience(p).build());
            return false;
        }
        // Teleport to exit spawn
        registry.get(s.parcourId).ifPresent(def -> {
            Location dst = def.exitSpawn().orElse(def.fallbackWorldSpawn());
            teleportWithIgnore(p, dst);
        });
        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.left")
                .with("name", s.parcourId).forAudience(p).build());
        return true;
    }

    public boolean teleportToCheckpoint(Player p) {
        ParcourSession s = sessions.get(p.getUniqueId());
        if (s == null) {
            p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_playing").forAudience(p).build());
            return false;
        }
        Location dst = s.restoreLocation();
        if (dst == null) {
            // fallback to start region center
            registry.get(s.parcourId).flatMap(ParcourDefinition::startRegion)
                    .flatMap(ParcourRegion::region)
                    .ifPresentOrElse(r -> {
                        Location startLoc = r.center(Bukkit.getServer());
                        teleportWithIgnore(p, startLoc);
                        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.no_checkpoint").forAudience(p).build());
                    }, () -> {
                        // nothing better: world spawn
                        teleportWithIgnore(p, p.getWorld().getSpawnLocation());
                        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.no_checkpoint").forAudience(p).build());
                    });
            return true;
        }
        teleportWithIgnore(p, dst);
        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.checkpoint.teleport").forAudience(p).build());
        return true;
    }

    private void startSession(Player p, ParcourDefinition def, Location startRestore, int firstExpectedOrder) {
        ParcourSession session = new ParcourSession(p.getUniqueId(), def, startRestore, firstExpectedOrder);
        sessions.put(p.getUniqueId(), session);
        lastTrigger.put(p.getUniqueId(), 0L);
    }

    public void tryTrigger(Player p, Location to) {
        long now = System.currentTimeMillis();
        Long ignore = ignoreUntil.get(p.getUniqueId());
        if (ignore != null && now < ignore) return;
        Long prev = lastTrigger.get(p.getUniqueId());
        if (prev != null && (now - prev) < TRIGGER_COOLDOWN_MS) return;

        // Find first matching region among ALL parcours
        for (ParcourDefinition def : registry.all()) {
            for (ParcourRegion pr : def.regions()) {
                if (pr.region().isEmpty()) continue;
                Region r = pr.region().get();
                if (!Objects.equals(r.worldName(), to.getWorld().getName())) continue;
                if (!r.contains(to)) continue;

                // Determine action based on current session state
                ParcourSession s = sessions.get(p.getUniqueId());

                if (s == null) {
                    // Only START regions can auto-start
                    if (pr.type() == ParcourRegionType.START && pr.order() == 0) {
                        Location startRestore = r.center(Bukkit.getServer());
                        if (startRestore == null) continue;
                        startSession(p, def, startRestore, 1);
                        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.starting")
                                .with("name", def.id()).forAudience(p).build());
                        executeRegionCommands(p, pr);
                        lastTrigger.put(p.getUniqueId(), now);
                        return;
                    } else {
                        continue;
                    }
                } else {
                    // Only interact with regions of the same parcour as the active session
                    if (!s.parcourId.equalsIgnoreCase(def.id())) continue;

                    // Enforce sequential order
                    int expected = s.expectedNextOrder();
                    if (pr.order() != expected) {
                        // Allow END region if it equals expected (should match)
                        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.seq.invalid").forAudience(p).build());
                        lastTrigger.put(p.getUniqueId(), now);
                        return;
                    }

                    // Valid progression: execute commands, set restore if flagged, advance order
                    if (!s.alreadyTriggered(pr)) {
                        executeRegionCommands(p, pr);
                        s.markTriggered(pr);
                    }

                    if (pr.restoreCheckpoint()) {
                        Location center = r.center(Bukkit.getServer());
                        if (center != null) {
                            s.setRestoreLocation(center);
                            p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.checkpoint.set")
                                    .with("order", String.valueOf(pr.order())).forAudience(p).build());
                        }
                    }

                    // If END region -> finish
                    if (pr.type() == ParcourRegionType.END) {
                        finishParcour(p, s);
                        lastTrigger.put(p.getUniqueId(), now);
                        return;
                    }

                    // Advance expected order
                    s.advanceExpectedOrder();
                    lastTrigger.put(p.getUniqueId(), now);
                    return;
                }
            }
        }
    }

    private void executeRegionCommands(Player p, ParcourRegion pr) {
        if (pr.commands().isEmpty()) return;
        ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
        for (String cmd : pr.commands()) {
            String real = cmd.replace("{player}", p.getName());
            Bukkit.dispatchCommand(console, real);
        }
    }

    private void finishParcour(Player p, ParcourSession s) {
        long elapsedMs = System.currentTimeMillis() - s.startMillis;
        double seconds = elapsedMs / 1000.0;
        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.finished")
                .with("name", s.parcourId)
                .with("seconds", String.format(java.util.Locale.ROOT, "%.3f", seconds))
                .forAudience(p).build());
        sessions.remove(p.getUniqueId());
    }

    public void onPlayerDeathOrVoid(Player p) {
        if (!isPlaying(p)) return;
        teleportToCheckpoint(p);
    }

    public void teleportWithIgnore(Player p, Location dst) {
        ignoreUntil.put(p.getUniqueId(), System.currentTimeMillis() + TELEPORT_IGNORE_MS);
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
            if (!p.isOnline()) return;
            p.teleport(dst);
        }, BukkitTime.ticks(1));
    }

    public void selectParcour(Player p, String id) {
        var sel = selection(p);
        sel.selectedParcourId = id;
    }
}
