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
import org.bukkit.Sound;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

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

    // ====== Selection (editor) ======
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

    // ===== Admin editing / map settings =====

    public boolean createParcour(String id) {
        if (registry.get(id).isPresent()) return false;
        registry.saveParcour(new ParcourDefinition(id));
        log.info("Created parcour '" + id + "'");
        return true;
    }

    public boolean deleteParcour(String id) {
        boolean ok = registry.deleteParcour(id);
        if (ok) {
            sessions.values().removeIf(s -> s.parcourId.equalsIgnoreCase(id));
        }
        return ok;
    }

    public boolean setCheckpointSound(String id, String soundOrNull) {
        return registry.get(id).map(def -> {
            def.setCheckpointSoundName(soundOrNull);
            registry.saveParcour(def);
            return true;
        }).orElse(false);
    }

    public boolean setEndSound(String id, String soundOrNull) {
        return registry.get(id).map(def -> {
            def.setEndSoundName(soundOrNull);
            registry.saveParcour(def);
            return true;
        }).orElse(false);
    }

    public boolean setUseActionBar(String id, boolean value) {
        return registry.get(id).map(def -> {
            def.setUseActionBar(value);
            registry.saveParcour(def);
            return true;
        }).orElse(false);
    }

    public boolean setFinishTeleportDelay(String id, int seconds) {
        return registry.get(id).map(def -> {
            def.setFinishTeleportDelaySeconds(seconds);
            registry.saveParcour(def);
            return true;
        }).orElse(false);
    }

    public boolean addRegionStart(String id, Region region, boolean restoreCheckpoint) {
        return registry.get(id).map(def -> {
            ParcourRegion r = new ParcourRegion(-1, ParcourRegionType.START);
            r.setRegion(region);
            r.setRestoreCheckpoint(restoreCheckpoint);
            def.setStartRegion(r);
            registry.saveParcour(def);
            return true;
        }).orElse(false);
    }

    // END has no restore-flag
    public boolean addRegionEnd(String id, Region region) {
        return registry.get(id).map(def -> {
            ParcourRegion r = new ParcourRegion(Integer.MAX_VALUE, ParcourRegionType.END);
            r.setRegion(region);
            def.setEndRegion(r);
            registry.saveParcour(def);
            return true;
        }).orElse(false);
    }

    public boolean addRegionCheckpoint(String id, int order, Region region, boolean restoreCheckpoint) {
        return registry.get(id).map(def -> {
            ParcourRegion r = new ParcourRegion(order, ParcourRegionType.CHECKPOINT);
            r.setRegion(region);
            r.setRestoreCheckpoint(restoreCheckpoint);
            def.putCheckpoint(r);
            registry.saveParcour(def);
            return true;
        }).orElse(false);
    }

    public boolean removeRegion(String id, String key) {
        return registry.get(id).map(def -> {
            if (isStartKey(key)) {
                boolean ok = def.clearStartRegion();
                if (ok) registry.saveParcour(def);
                return ok;
            } else if (isEndKey(key)) {
                boolean ok = def.clearEndRegion();
                if (ok) registry.saveParcour(def);
                return ok;
            } else {
                Integer ord = parseOrder(key);
                if (ord == null) return false;
                boolean ok = def.removeCheckpoint(ord);
                if (ok) registry.saveParcour(def);
                return ok;
            }
        }).orElse(false);
    }

    public boolean setRegionRestore(String id, String key, boolean restore) {
        return registry.get(id).map(def -> {
            ParcourRegion pr = getRegionByKey(def, key);
            if (pr == null || pr.type() == ParcourRegionType.END) return false; // not applicable for END
            pr.setRestoreCheckpoint(restore);
            registry.saveParcour(def);
            return true;
        }).orElse(false);
    }

    public boolean addRegionCommand(String id, String key, String cmd) {
        return registry.get(id).map(def -> {
            ParcourRegion pr = getRegionByKey(def, key);
            if (pr == null) return false;
            pr.addCommand(cmd);
            registry.saveParcour(def);
            return true;
        }).orElse(false);
    }

    public boolean clearRegionCommands(String id, String key) {
        return registry.get(id).map(def -> {
            ParcourRegion pr = getRegionByKey(def, key);
            if (pr == null) return false;
            pr.clearCommands();
            registry.saveParcour(def);
            return true;
        }).orElse(false);
    }

    // Set explicit restore-location (START/CHECKPOINT only)
    public boolean setRegionRestoreLocation(String id, String key, Location loc) {
        return registry.get(id).map(def -> {
            ParcourRegion pr = getRegionByKey(def, key);
            if (pr == null || pr.type() == ParcourRegionType.END) return false;
            pr.setExplicitRestore(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
            registry.saveParcour(def);
            return true;
        }).orElse(false);
    }

    // Toggle progress notifications per parcour (chat)
    public boolean setProgressNotify(String id, boolean value) {
        return registry.get(id).map(def -> {
            def.setNotifyProgress(value);
            registry.saveParcour(def);
            return true;
        }).orElse(false);
    }

    private boolean isStartKey(String key) { return "START".equalsIgnoreCase(key); }
    private boolean isEndKey(String key) { return "END".equalsIgnoreCase(key); }
    private Integer parseOrder(String key) { try { return Integer.parseInt(key); } catch (NumberFormatException e) { return null; } }

    private ParcourRegion getRegionByKey(ParcourDefinition def, String key) {
        if (isStartKey(key)) return def.startRegion().orElse(null);
        if (isEndKey(key)) return def.endRegion().orElse(null);
        Integer ord = parseOrder(key);
        if (ord == null) return null;
        return def.checkpoint(ord).orElse(null);
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

    public boolean isPlaying(Player p) { return sessions.containsKey(p.getUniqueId()); }
    public Optional<ParcourSession> session(Player p) { return Optional.ofNullable(sessions.get(p.getUniqueId())); }

    public void clearSession(Player p) {
        ParcourSession s = sessions.remove(p.getUniqueId());
        if (s != null) s.cancelActionBarTask();
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
        var startRegionOpt = def.startRegion().flatMap(ParcourRegion::region);
        var endRegionOpt = def.endRegion().flatMap(ParcourRegion::region);
        if (startRegionOpt.isEmpty() || endRegionOpt.isEmpty()) {
            p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.cannot_start_missing")
                    .with("name", id).forAudience(p).build());
            return false;
        }

        // Prefer explicit restore_location for START if present, else region center
        Location startRestore = def.startRegion().map(sr -> sr.resolveRestoreLocation(Bukkit.getServer())).orElse(null);
        if (startRestore == null) {
            p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.cannot_start_missing")
                    .with("name", id).forAudience(p).build());
            return false;
        }

        // Teleport to start and start session
        teleportWithIgnore(p, startRestore);
        startSession(p, def, startRestore, 0);

        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.starting")
                .with("name", def.id()).forAudience(p).build());
        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.started_teleport")
                .forAudience(p).build());

        // Execute START commands once
        def.startRegion().ifPresent(thisRegion -> executeRegionCommands(p, thisRegion));
        return true;
    }

    public boolean leaveParcour(Player p) {
        ParcourSession s = sessions.remove(p.getUniqueId());
        if (s == null) {
            p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_playing").forAudience(p).build());
            return false;
        }
        s.cancelActionBarTask();
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
            registry.get(s.parcourId).flatMap(ParcourDefinition::startRegion)
                    .ifPresentOrElse(pr -> {
                        Location pref = pr.resolveRestoreLocation(Bukkit.getServer());
                        if (pref != null) {
                            teleportWithIgnore(p, pref);
                        } else {
                            teleportWithIgnore(p, p.getWorld().getSpawnLocation());
                        }
                        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.no_checkpoint").forAudience(p).build());
                    }, () -> {
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

        // NEW: schedule actionbar if enabled
        if (def.useActionBar()) {
            BukkitTask task = feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(() -> {
                if (!p.isOnline() || !isPlaying(p)) {
                    session.cancelActionBarTask();
                    return;
                }
                sendActionBar(p, def, session);
            }, BukkitTime.seconds(0L), BukkitTime.ticks(10L)); // every 0.5s
            session.setActionBarTask(task);
        }
    }

    private void sendActionBar(Player p, ParcourDefinition def, ParcourSession s) {
        long elapsedMs = System.currentTimeMillis() - s.startMillis;
        double seconds = elapsedMs / 1000.0;
        int total = def.totalCheckpoints() + 1; // endpoints counted with +1
        int current = Math.min(s.expectedNextOrder(), total);
        Component c = feature.getLocalizationHandler()
                .getMessage("parcour.actionbar")
                .with("seconds", String.format(java.util.Locale.ROOT, "%.3f", seconds))
                .with("current", String.valueOf(current))
                .with("total", String.valueOf(total))
                .forAudience(p).build();
        p.sendActionBar(c);
    }

    public void tryTrigger(Player p, Location to) {
        long now = System.currentTimeMillis();
        Long ignore = ignoreUntil.get(p.getUniqueId());
        if (ignore != null && now < ignore) return;
        Long prev = lastTrigger.get(p.getUniqueId());
        if (prev != null && (now - prev) < TRIGGER_COOLDOWN_MS) return;

        for (ParcourDefinition def : registry.all()) {
            // START auto-start if not playing
            if (!isPlaying(p)) {
                if (def.startRegion().isPresent() && def.startRegion().get().region().isPresent()) {
                    Region r = def.startRegion().get().region().get();
                    if (Objects.equals(r.worldName(), to.getWorld().getName()) && r.contains(to)) {
                        Location startRestore = def.startRegion().get().resolveRestoreLocation(Bukkit.getServer());
                        if (startRestore == null) continue;
                        startSession(p, def, startRestore, 0);
                        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.starting")
                                .with("name", def.id()).forAudience(p).build());
                        executeRegionCommands(p, def.startRegion().get());
                        lastTrigger.put(p.getUniqueId(), now);
                        return;
                    }
                }
                continue;
            }

            // If playing, only the active parcours can react
            ParcourSession s = sessions.get(p.getUniqueId());
            if (!s.parcourId.equalsIgnoreCase(def.id())) continue;

            int expected = s.expectedNextOrder();

            // expected checkpoint
            Optional<ParcourRegion> expectedCkpt = def.checkpoint(expected);
            if (expectedCkpt.isPresent()) {
                ParcourRegion pr = expectedCkpt.get();
                if (pr.region().isPresent()) {
                    Region r = pr.region().get();
                    if (Objects.equals(r.worldName(), to.getWorld().getName()) && r.contains(to)) {
                        boolean firstTimeHere = !s.alreadyTriggered(pr);
                        if (firstTimeHere) {
                            executeRegionCommands(p, pr);
                            playSoundIfDefined(p, def.checkpointSoundName());
                            s.markTriggered(pr);
                        }
                        if (pr.restoreCheckpoint()) {
                            Location pref = pr.resolveRestoreLocation(Bukkit.getServer());
                            if (pref != null) {
                                s.setRestoreLocation(pref);
                                p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.checkpoint.set")
                                        .with("order", String.valueOf(pr.order())).forAudience(p).build());
                            }
                        }
                        // Progress one
                        s.advanceExpectedOrder();

                        // Progress notification (optional chat)
                        if (def.notifyProgress()) {
                            int current = s.expectedNextOrder(); // after increment
                            int total = def.totalCheckpoints() + 1; // also add endpoint
                            p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.progress")
                                    .with("current", String.valueOf(current))
                                    .with("total", String.valueOf(total))
                                    .forAudience(p).build());
                        }

                        lastTrigger.put(p.getUniqueId(), now);
                        return;
                    }
                }
                continue;
            }

            // No more checkpoints expected -> END must complete
            if (def.endRegion().isPresent() && def.endRegion().get().region().isPresent()) {
                Region r = def.endRegion().get().region().get();
                if (Objects.equals(r.worldName(), to.getWorld().getName()) && r.contains(to)) {
                    // run END commands once
                    ParcourRegion endPr = def.endRegion().get();
                    boolean firstEnd = !s.alreadyTriggered(endPr);
                    if (firstEnd) {
                        executeRegionCommands(p, endPr);
                        playSoundIfDefined(p, def.endSoundName());
                        s.markTriggered(endPr);
                    }

                    // send final progress if enabled (report total/total)
                    if (def.notifyProgress()) {
                        int total = def.totalCheckpoints() + 1; // checkpoints + END
                        int current = total;
                        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.progress")
                                .with("current", String.valueOf(current))
                                .with("total", String.valueOf(total))
                                .forAudience(p).build());
                    }

                    // Finish
                    finishParcour(p, s, def);
                    lastTrigger.put(p.getUniqueId(), now);
                    return;
                }
            }
        }
    }

    private void playSoundIfDefined(Player p, Optional<String> nameOpt) {
        if (nameOpt.isEmpty()) return;
        String raw = nameOpt.get();
        try {
            Sound s = Sound.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            p.playSound(p.getLocation(), s, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {
            // invalid name slipped in; ignore silently
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

    private void finishParcour(Player p, ParcourSession s, ParcourDefinition def) {
        long elapsedMs = System.currentTimeMillis() - s.startMillis;
        double seconds = elapsedMs / 1000.0;
        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.finished")
                .with("name", s.parcourId)
                .with("seconds", String.format(java.util.Locale.ROOT, "%.3f", seconds))
                .forAudience(p).build());

        // stop actionbar if running
        s.cancelActionBarTask();
        sessions.remove(p.getUniqueId());

        // NEW: delayed teleport to exit spawn if configured (>0)
        int delaySec = def.finishTeleportDelaySeconds();
        if (delaySec > 0) {
            Location dst = def.exitSpawn().orElse(def.fallbackWorldSpawn());
            feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
                if (p.isOnline()) {
                    teleportWithIgnore(p, dst);
                }
            }, BukkitTime.seconds(delaySec));
        }
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
