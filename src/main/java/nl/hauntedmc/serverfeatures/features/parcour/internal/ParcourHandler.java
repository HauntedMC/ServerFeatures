package nl.hauntedmc.serverfeatures.features.parcour.internal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.api.util.BukkitRegistry;
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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ParcourHandler {

    // Cooldowns throttled elsewhere; do not block sweep checks with a coarse move-cooldown.
    private static final long FINISH_ACTIONBAR_HOLD_MS = 3000L;

    private static final long PARTICLE_INTERVAL_TICKS = 12L;
    private static final int PARTICLE_OUTLINE_TARGET_POINTS = 280;

    private static final org.bukkit.Material ITEM_LEAVE_MAT = org.bukkit.Material.BARRIER;
    private static final org.bukkit.Material ITEM_CKPT_MAT = org.bukkit.Material.NETHER_STAR;
    private static final int SLOT_CKPT = 3;
    private static final int SLOT_LEAVE = 5;

    private final Parcour feature;
    private final ParcourRegistry registry;
    private final FeatureLogger log;
    private final NamespacedKey leaveKey;
    private final NamespacedKey checkpointKey;
    private final NamespacedKey kitKey;

    private final Map<UUID, ParcourSession> sessions = new ConcurrentHashMap<>();

    public ParcourHandler(Parcour feature, ParcourRegistry registry) {
        this.feature = feature;
        this.registry = registry;
        this.log = feature.getLogger();
        this.leaveKey = new NamespacedKey(feature.getPlugin(), "parcour_leave");
        this.checkpointKey = new NamespacedKey(feature.getPlugin(), "parcour_checkpoint");
        this.kitKey = new NamespacedKey(feature.getPlugin(), "parcour_kit");
    }

    public NamespacedKey leaveKey() { return leaveKey; }
    public NamespacedKey checkpointKey() { return checkpointKey; }
    public NamespacedKey kitKey() { return kitKey; }

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

    public boolean setRegionParticle(String id, String particleOrNull) {
        return registry.get(id).map(def -> {
            def.setRegionHighlightParticleName(particleOrNull);
            registry.saveParcour(def);

            for (ParcourSession s : sessions.values()) {
                if (!s.parcourId.equalsIgnoreCase(def.id())) continue;
                Player pl = Bukkit.getPlayer(s.playerId);
                if (pl != null && pl.isOnline()) {
                    updateRegionHighlight(pl, def, s);
                }
            }
            return true;
        }).orElse(false);
    }

    public boolean setHungerEnabled(String id, boolean value) {
        return registry.get(id).map(def -> {
            def.setHungerEnabled(value);
            registry.saveParcour(def);
            return true;
        }).orElse(false);
    }

    public boolean setDamageEnabled(String id, boolean value) {
        return registry.get(id).map(def -> {
            def.setDamageEnabled(value);
            registry.saveParcour(def);
            return true;
        }).orElse(false);
    }

    public boolean setCheckpointCooldownSeconds(String id, int seconds) {
        return registry.get(id).map(def -> {
            def.setCheckpointCooldownSeconds(seconds);
            registry.saveParcour(def);
            return true;
        }).orElse(false);
    }

    public boolean setStartCountdownSeconds(String id, int seconds) {
        return registry.get(id).map(def -> {
            def.setStartCountdownSeconds(seconds);
            registry.saveParcour(def);
            return true;
        }).orElse(false);
    }

    public boolean setStartPosition(String id, Location loc) {
        return registry.get(id).map(def -> {
            def.setStartPosition(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
            registry.saveParcour(def);
            return true;
        }).orElse(false);
    }

    public boolean clearStartPosition(String id) {
        return registry.get(id).map(def -> {
            def.clearStartPosition();
            registry.saveParcour(def);
            return true;
        }).orElse(false);
    }

    public boolean setEffect(String id, String effectNameOrNull, Integer amplifier) {
        return registry.get(id).map(def -> {
            if (effectNameOrNull == null) {
                def.setEffect(null, null);
            } else {
                String up = effectNameOrNull.trim().toUpperCase(java.util.Locale.ROOT);
                if (PotionEffectType.getByName(up) == null) return false; // kept for config validation; application uses registry-safe methods
                def.setEffect(up, amplifier);
            }
            registry.saveParcour(def);

            // live-update current sessions on this parcour
            for (ParcourSession s : sessions.values()) {
                if (!s.parcourId.equalsIgnoreCase(def.id())) continue;
                Player pl = Bukkit.getPlayer(s.playerId);
                if (pl != null && pl.isOnline()) {
                    cleanupEffectForSession(pl, s);
                    applyEffectIfConfigured(pl, def, s);
                }
            }
            return true;
        }).orElse(false);
    }

    private void applyEffectIfConfigured(Player p, ParcourDefinition def, ParcourSession s) {
        if (def.effectTypeName().isEmpty()) return;
        // Resolve via registry where possible, but accept legacy names from config
        PotionEffectType type = PotionEffectType.getByName(def.effectTypeName().get());
        if (type == null) return;

        s.setAppliedEffectType(type);
        // Remember previous effect to restore later (can be null)
        PotionEffect prev = p.getPotionEffect(type);
        s.setPreviousEffect(prev);

        int amp = Math.max(0, def.effectAmplifier());
        // Very long duration so it covers the whole run; we'll remove/restore on cleanup.
        p.addPotionEffect(new PotionEffect(type, PotionEffect.INFINITE_DURATION, amp, true, false, true));
    }

    private void cleanupEffectForSession(Player p, ParcourSession s) {
        PotionEffectType type = s.appliedEffectType();
        if (type == null) return;

        // Remove the effect we applied
        p.removePotionEffect(type);

        // Restore previous if there was one
        PotionEffect prev = s.previousEffect();
        if (prev != null) {
            p.addPotionEffect(prev);
        }

        s.setAppliedEffectType(null);
        s.setPreviousEffect(null);
    }

    public boolean clearLeaveLocation(String id) {
        return registry.clearLeaveLocation(id);
    }

    public boolean clearFinishLocation(String id) {
        return registry.clearFinishLocation(id);
    }

    public boolean clearRegionRestoreLocation(String id, String key) {
        return registry.clearRegionRestoreLocation(id, key);
    }

    public boolean addStartKitFromHand(String id, Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) return false;
        return registry.get(id).map(def -> {
            registry.serializeItemToBase64(hand.clone()).ifPresent(def::addStartKitSerialized);
            registry.saveParcour(def);
            return true;
        }).orElse(false);
    }

    public boolean clearStartKit(String id) {
        return registry.get(id).map(def -> {
            def.clearStartKit();
            registry.saveParcour(def);
            return true;
        }).orElse(false);
    }

    public Optional<List<ItemStack>> listStartKit(String id) {
        return registry.get(id).map(def -> {
            List<ItemStack> res = new ArrayList<>();
            for (String b64 : def.startKitEncoded()) {
                registry.deserializeItemFromBase64(b64).ifPresent(res::add);
            }
            return res;
        });
    }

    public boolean removeStartKitIndex(String id, int oneBasedIndex) {
        return registry.get(id).map(def -> {
            boolean ok = def.removeStartKitIndex(oneBasedIndex);
            if (ok) registry.saveParcour(def);
            return ok;
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
            if ("START".equalsIgnoreCase(key)) {
                boolean ok = def.clearStartRegion();
                if (ok) registry.saveParcour(def);
                return ok;
            } else if ("END".equalsIgnoreCase(key)) {
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
            if (pr == null || pr.type() == ParcourRegionType.END) return false;
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

    public boolean removeRegionCommandIndex(String id, String key, int oneBasedIndex) {
        return registry.get(id).map(def -> {
            ParcourRegion pr = getRegionByKey(def, key);
            if (pr == null) return false;
            java.util.List<String> cmds = new java.util.ArrayList<>(pr.commands());
            if (oneBasedIndex < 1 || oneBasedIndex > cmds.size()) return false;
            cmds.remove(oneBasedIndex - 1);
            pr.clearCommands();
            for (String c : cmds) pr.addCommand(c);
            registry.saveParcour(def);
            return true;
        }).orElse(false);
    }

    public boolean setRegionRestoreLocation(String id, String key, Location loc) {
        return registry.get(id).map(def -> {
            ParcourRegion pr = getRegionByKey(def, key);
            if (pr == null || pr.type() == ParcourRegionType.END) return false;
            pr.setExplicitRestore(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
            registry.saveParcour(def);
            return true;
        }).orElse(false);
    }

    public boolean setProgressNotify(String id, boolean value) {
        return registry.get(id).map(def -> {
            def.setNotifyProgress(value);
            registry.saveParcour(def);
            return true;
        }).orElse(false);
    }

    private Integer parseOrder(String key) {
        try { return Integer.parseInt(key); } catch (NumberFormatException e) { return null; }
    }

    private ParcourRegion getRegionByKey(ParcourDefinition def, String key) {
        if ("START".equalsIgnoreCase(key)) return def.startRegion().orElse(null);
        if ("END".equalsIgnoreCase(key)) return def.endRegion().orElse(null);
        Integer ord = parseOrder(key);
        if (ord == null) return null;
        return def.checkpoint(ord).orElse(null);
    }

    public boolean setLeaveLocation(String id, Location loc) {
        Optional<ParcourDefinition> defOpt = registry.get(id);
        if (defOpt.isEmpty()) return false;
        ParcourDefinition def = defOpt.get();
        def.setLeaveSpawn(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        registry.saveParcour(def);
        return true;
    }

    public boolean setFinishLocation(String id, Location loc) {
        Optional<ParcourDefinition> defOpt = registry.get(id);
        if (defOpt.isEmpty()) return false;
        ParcourDefinition def = defOpt.get();
        def.setFinishSpawn(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        registry.saveParcour(def);
        return true;
    }

    public boolean isPlaying(Player p) { return sessions.containsKey(p.getUniqueId()); }
    public Optional<ParcourSession> session(Player p) { return Optional.ofNullable(sessions.get(p.getUniqueId())); }

    public void clearSession(Player p) {
        ParcourSession s = sessions.remove(p.getUniqueId());
        if (s != null) {
            s.cancelActionBarTask();
            s.cancelParticleTask();
            s.cancelCountdownTask();
        }
    }

    public int restoreAllAndClearSessions() {
        int count = 0;
        for (ParcourSession s : new ArrayList<>(sessions.values())) {
            Player p = Bukkit.getPlayer(s.playerId);
            if (p != null && p.isOnline()) {
                // cleanup effect and restore inventory if we can
                try { cleanupEffectForSession(p, s); } catch (Throwable ignored) {}
                if (s.snapshot() != null) {
                    try { s.snapshot().restore(p); } catch (Throwable ignored) { }
                    count++;
                }
            }
            s.cancelActionBarTask();
            s.cancelParticleTask();
            s.cancelCountdownTask();
        }
        sessions.clear();
        return count;
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

        Location startRestore = def.startRegion().map(sr -> sr.resolveRestoreLocation(Bukkit.getServer())).orElse(null);
        if (startRestore == null) {
            p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.cannot_start_missing")
                    .with("name", id).forAudience(p).build());
            return false;
        }

        teleport(p, startRestore);
        startSession(p, def, startRestore, 0);

        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.starting")
                .with("name", def.id()).forAudience(p).build());
        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.started_teleport")
                .forAudience(p).build());

        def.startRegion().ifPresent(this::executeRegionCommandsSilently);

        updateRegionHighlight(p, def, sessions.get(p.getUniqueId()));

        beginStartCountdownIfNeeded(p, def, sessions.get(p.getUniqueId()), startRestore);
        return true;
    }

    public boolean leaveParcour(Player p) {
        ParcourSession s = sessions.get(p.getUniqueId());
        if (s == null) {
            p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_playing").forAudience(p).build());
            return false;
        }

        // cleanup effect and restore inventory first
        cleanupEffectForSession(p, s);
        restoreInventoryIfPresent(p, s);

        registry.get(s.parcourId).ifPresent(def -> {
            Location dst = def.leaveSpawn().orElse(def.fallbackWorldSpawn());
            teleport(p, dst);
        });

        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.left")
                .with("name", s.parcourId).forAudience(p).build());

        clearSession(p);
        return true;
    }

    public boolean teleportToCheckpoint(Player p, boolean enforceCooldown) {
        ParcourSession s = sessions.get(p.getUniqueId());
        if (s == null) {
            p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_playing").forAudience(p).build());
            return false;
        }
        if (s.isCountdownActive()) {
            p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.countdown.blocked").forAudience(p).build());
            return false;
        }
        Optional<ParcourDefinition> defOpt = registry.get(s.parcourId);
        if (enforceCooldown && defOpt.isPresent()) {
            int cdSec = Math.max(0, defOpt.get().checkpointCooldownSeconds());
            if (cdSec > 0) {
                long now = System.currentTimeMillis();
                long nextAllowed = s.lastCheckpointTeleportMs() + cdSec * 1000L;
                if (now < nextAllowed) {
                    long remainingMs = nextAllowed - now;
                    long remainingSec = (remainingMs + 999L) / 1000L;
                    p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.checkpoint.cooldown")
                            .with("seconds", String.valueOf(remainingSec)).forAudience(p).build());
                    return false;
                }
            }
        }

        Location dst = s.restoreLocation();
        if (dst == null) {
            defOpt.flatMap(ParcourDefinition::startRegion)
                    .ifPresentOrElse(pr -> {
                        Location pref = pr.resolveRestoreLocation(Bukkit.getServer());
                        teleport(p, Objects.requireNonNullElseGet(pref, () -> p.getWorld().getSpawnLocation()));
                        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.no_checkpoint").forAudience(p).build());
                    }, () -> {
                        teleport(p, p.getWorld().getSpawnLocation());
                        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.no_checkpoint").forAudience(p).build());
                    });
            return true;
        }
        teleport(p, dst);
        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.checkpoint.teleport").forAudience(p).build());

        if (enforceCooldown && defOpt.isPresent() && defOpt.get().checkpointCooldownSeconds() > 0) {
            s.setLastCheckpointTeleportMs(System.currentTimeMillis());
        }
        return true;
    }

    private void startSession(Player p, ParcourDefinition def, Location startRestore, int firstExpectedOrder) {
        ParcourSession session = new ParcourSession(p.getUniqueId(), def, startRestore, firstExpectedOrder);
        sessions.put(p.getUniqueId(), session);

        ParcourInventorySnapshot snap = ParcourInventorySnapshot.capture(p);
        session.setSnapshot(snap);
        applyCleanParcourInventory(p);
        giveControlItems(p);
        giveStartKitItems(p, def);

        p.setHealth(Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue());
        p.setFoodLevel(20);
        p.setSaturation(20);

        // Apply parcour-wide effect if configured
        applyEffectIfConfigured(p, def, session);

        if (def.useActionBar() && def.startCountdownSeconds() <= 0) {
            startActionBarTimer(p, def, session);
        }
    }

    private void startActionBarTimer(Player p, ParcourDefinition def, ParcourSession session) {
        if (!def.useActionBar()) return;
        if (session.isCountdownActive()) return;

        session.cancelActionBarTask();
        BukkitTask task = feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(() -> {
            if (!p.isOnline()) {
                session.cancelActionBarTask();
                return;
            }
            if (!isPlaying(p)) {
                session.cancelActionBarTask();
                return;
            }
            if (session.isCountdownActive()) return;

            sendActionBar(p, def, session);
        }, BukkitTime.seconds(0L), BukkitTime.ticks(2L));
        session.setActionBarTask(task);
    }

    private void sendActionBar(Player p, ParcourDefinition def, ParcourSession s) {
        if (s.isCountdownActive()) return;

        final double shownSeconds = s.isFinished()
                ? s.finalSeconds()
                : (System.currentTimeMillis() - s.startMillis()) / 1000.0;

        final int total = def.totalCheckpoints() + 1;
        final int current = s.isFinished()
                ? total
                : Math.min(s.expectedNextOrder(), total);

        Component c = feature.getLocalizationHandler()
                .getMessage("parcour.actionbar")
                .with("seconds", String.format(java.util.Locale.ROOT, "%.1f", shownSeconds))
                .with("current", String.valueOf(current))
                .with("total", String.valueOf(total))
                .forAudience(p).build();
        p.sendActionBar(c);
    }

    /**
     * High-speed safe trigger: checks both the destination and the swept segment from->to against regions.
     */
    public void tryTrigger(Player p, Location from, Location to) {
        if (from == null || to == null) return;

        // Process all parcours; use swept AABB so we cannot skip at high velocity
        for (ParcourDefinition def : registry.all()) {
            if (!isPlaying(p)) {
                if (def.startRegion().isPresent() && def.startRegion().get().region().isPresent()) {
                    Region r = def.startRegion().get().region().get();
                    if (Objects.equals(r.worldName(), to.getWorld().getName())
                            && (contains(r, to) || segmentIntersectsRegion(from, to, r))) {

                        Location startRestore = def.startRegion().get().resolveRestoreLocation(Bukkit.getServer());
                        if (startRestore == null) continue;

                        startSession(p, def, startRestore, 0);

                        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.starting")
                                .with("name", def.id()).forAudience(p).build());

                        executeRegionCommands(p, def.startRegion().get());

                        updateRegionHighlight(p, def, sessions.get(p.getUniqueId()));

                        // Use 'from' (entry trajectory) as freeze hint so titles are shown where the player was when crossing.
                        beginStartCountdownIfNeeded(p, def, sessions.get(p.getUniqueId()), from.clone());
                        return;
                    }
                }
                continue;
            }

            ParcourSession s = sessions.get(p.getUniqueId());
            if (!s.parcourId.equalsIgnoreCase(def.id())) continue;

            if (s.isCountdownActive() || s.isFinished()) {
                return;
            }

            // Catch up through multiple checkpoints crossed in the same tick at high speed (elytra + fireworks)
            int safety = 0;
            boolean progressed = false;
            while (safety++ < 10) { // hard cap to avoid infinite loops on misconfig
                int expected = s.expectedNextOrder();
                Optional<ParcourRegion> expectedCkpt = def.checkpoint(expected);

                boolean hitSomething = false;

                if (expectedCkpt.isPresent()) {
                    ParcourRegion pr = expectedCkpt.get();
                    if (pr.region().isPresent()) {
                        Region r = pr.region().get();
                        if (Objects.equals(r.worldName(), to.getWorld().getName())
                                && (contains(r, to) || segmentIntersectsRegion(from, to, r))) {
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
                                    p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.checkpoint.set").forAudience(p).build());
                                }
                            }
                            s.advanceExpectedOrder();

                            if (def.notifyProgress()) {
                                int current = s.expectedNextOrder();
                                int total = def.totalCheckpoints() + 1;
                                p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.progress")
                                        .with("current", String.valueOf(current))
                                        .with("total", String.valueOf(total))
                                        .forAudience(p).build());
                            }

                            updateRegionHighlight(p, def, s);
                            progressed = true;
                            // loop again to see if we also crossed the next checkpoint in the same move
                            continue;
                        }
                    }
                }

                // If we didn't hit the expected checkpoint this iteration, break the loop.
                if (!hitSomething) break;
            }

            // After catching up checkpoints, also check END with swept segment
            if (def.endRegion().isPresent() && def.endRegion().get().region().isPresent()) {
                Region r = def.endRegion().get().region().get();
                if (Objects.equals(r.worldName(), to.getWorld().getName())
                        && (contains(r, to) || segmentIntersectsRegion(from, to, r))) {
                    ParcourRegion endPr = def.endRegion().get();
                    boolean firstEnd = !s.alreadyTriggered(endPr);
                    if (firstEnd) {
                        executeRegionCommands(p, endPr);
                        playSoundIfDefined(p, def.endSoundName());
                        s.markTriggered(endPr);
                    }

                    if (def.notifyProgress()) {
                        int total = def.totalCheckpoints() + 1;
                        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.progress")
                                .with("current", String.valueOf(total))
                                .with("total", String.valueOf(total))
                                .forAudience(p).build());
                    }

                    if (!s.isFinished()) {
                        finishParcour(p, s, def);
                    }
                }
            }

            // If we touched anything for this parcours, we're done this tick
            if (progressed) return;
        }
    }

    private void playSoundIfDefined(Player p, Optional<String> nameOpt) {
        if (nameOpt.isEmpty()) return;
        Sound s = resolveSound(nameOpt.get());
        if (s != null) {
            p.playSound(p.getLocation(), s, 1.0f, 1.0f);
        }
    }

    private void executeRegionCommandsSilently(ParcourRegion pr) {
        if (pr.commands().isEmpty()) return;
        ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
        for (String cmd : pr.commands()) {
            String real = cmd.replace("{player}", console.getName());
            Bukkit.dispatchCommand(console, real);
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
        long elapsedMs = System.currentTimeMillis() - s.startMillis();
        double seconds = elapsedMs / 1000.0;
        p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.finished")
                .with("name", s.parcourId)
                .with("seconds", String.format(java.util.Locale.ROOT, "%.3f", seconds))
                .forAudience(p).build());

        s.markFinished(elapsedMs);
        s.cancelParticleTask();

        // cleanup effect and restore inventory
        cleanupEffectForSession(p, s);
        restoreInventoryIfPresent(p, s);

        long holdTicks = Math.max(1L, (FINISH_ACTIONBAR_HOLD_MS + 49L) / 50L);
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
            ParcourSession current = sessions.get(p.getUniqueId());
            if (current != null && current == s) {
                s.cancelActionBarTask();
                sessions.remove(p.getUniqueId());
            }
        }, BukkitTime.ticks(holdTicks));

        int delaySec = def.finishTeleportDelaySeconds();
        if (delaySec > 0) {
            Location dst = def.finishSpawn().orElse(def.fallbackWorldSpawn());
            feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
                if (p.isOnline()) {
                    teleport(p, dst);
                }
            }, BukkitTime.seconds(delaySec));
        }
    }

    public void onPlayerDeathOrVoid(Player p) {
        if (!isPlaying(p)) return;
        teleportToCheckpoint(p, false);
    }

    public void teleport(Player p, Location dst) {
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
            if (!p.isOnline()) return;
            p.teleport(dst);
        }, BukkitTime.ticks(1));
    }

    public boolean isMovementFrozen(Player p) {
        ParcourSession s = sessions.get(p.getUniqueId());
        return s != null && s.isCountdownActive();
    }

    private void beginStartCountdownIfNeeded(Player p, ParcourDefinition def, ParcourSession s, Location entryPosition) {
        if (s == null) return;
        int seconds = Math.max(0, def.startCountdownSeconds());
        if (seconds == 0) {
            s.setStartToNow();
            return;
        }

        Location freezeAt = def.startPosition().orElse(entryPosition);
        teleport(p, freezeAt);

        s.setCountdownActive(true);
        s.setFrozenAt(freezeAt);

        final int[] remaining = {seconds};
        showCountdownTitle(p, remaining[0]);

        BukkitTask task = feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(() -> {
            if (!p.isOnline() || !isPlaying(p)) {
                s.cancelCountdownTask();
                s.setCountdownActive(false);
                return;
            }
            ParcourSession current = sessions.get(p.getUniqueId());
            if (current == null || current == s && !current.isCountdownActive()) {
                s.cancelCountdownTask();
                return;
            }

            remaining[0]--;
            if (remaining[0] > 0) {
                showCountdownTitle(p, remaining[0]);
                return;
            }

            Component go = feature.getLocalizationHandler().getMessage("parcour.countdown.go").forAudience(p).build();
            p.showTitle(Title.title(go, Component.empty(),
                    Title.Times.of(Duration.ofMillis(150), Duration.ofMillis(850), Duration.ofMillis(50))));

            s.setCountdownActive(false);
            s.cancelCountdownTask();
            s.setStartToNow();

            startActionBarTimer(p, def, s);
        }, BukkitTime.seconds(1L), BukkitTime.seconds(1L));
        s.setCountdownTask(task);
    }

    private void showCountdownTitle(Player p, int sec) {
        Component countdown = feature.getLocalizationHandler()
                .getMessage("parcour.countdown.title")
                .with("seconds", String.valueOf(sec))
                .forAudience(p).build();
        p.showTitle(Title.title(countdown, Component.empty(),
                Title.Times.of(Duration.ofMillis(150), Duration.ofMillis(850), Duration.ofMillis(0))));
    }

    private void applyCleanParcourInventory(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.getInventory().setItemInOffHand(null);
        p.updateInventory();
    }

    private void giveControlItems(Player p) {
        ItemStack leave = new ItemStack(ITEM_LEAVE_MAT, 1);
        var lm = leave.getItemMeta();
        lm.displayName(feature.getLocalizationHandler().getMessage("parcour.item.leave.name").forAudience(p).build());
        lm.lore(java.util.List.of(feature.getLocalizationHandler().getMessage("parcour.item.leave.lore").forAudience(p).build()));
        lm.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        lm.getPersistentDataContainer().set(leaveKey, PersistentDataType.BYTE, (byte) 1);
        leave.setItemMeta(lm);

        ItemStack ck = new ItemStack(ITEM_CKPT_MAT, 1);
        var cm = ck.getItemMeta();
        cm.displayName(feature.getLocalizationHandler().getMessage("parcour.item.checkpoint.name").forAudience(p).build());
        cm.lore(java.util.List.of(feature.getLocalizationHandler().getMessage("parcour.item.checkpoint.lore").forAudience(p).build()));
        cm.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        cm.getPersistentDataContainer().set(checkpointKey, PersistentDataType.BYTE, (byte) 1);
        ck.setItemMeta(cm);

        p.getInventory().setItem(SLOT_LEAVE, leave);
        p.getInventory().setItem(SLOT_CKPT, ck);
        p.updateInventory();
    }

    private void giveStartKitItems(Player p, ParcourDefinition def) {
        PlayerInventory inv = p.getInventory();
        for (String b64 : def.startKitEncoded()) {
            var isOpt = registry.deserializeItemFromBase64(b64);
            if (isOpt.isEmpty()) continue;

            ItemStack item = isOpt.get().clone();
            var meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(kitKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);

            org.bukkit.Material mat = item.getType();
            boolean placed = false;

            if (isHelmet(mat) && isEmpty(inv.getHelmet())) { inv.setHelmet(item); placed = true; }
            else if (isChestArmor(mat) && isEmpty(inv.getChestplate())) { inv.setChestplate(item); placed = true; }
            else if (mat == org.bukkit.Material.ELYTRA && isEmpty(inv.getChestplate())) { inv.setChestplate(item); placed = true; }
            else if (isLeggings(mat) && isEmpty(inv.getLeggings())) { inv.setLeggings(item); placed = true; }
            else if (isBoots(mat) && isEmpty(inv.getBoots())) { inv.setBoots(item); placed = true; }
            else if (isOffhandItem(mat) && isEmpty(inv.getItemInOffHand())) { inv.setItemInOffHand(item); placed = true; }

            if (!placed) {
                int slot = firstFreePlayableSlot(inv);
                if (slot >= 0) inv.setItem(slot, item);
                else p.getWorld().dropItemNaturally(p.getLocation(), item);
            }
        }
        p.updateInventory();
    }

    private boolean isEmpty(ItemStack it) { return it == null || it.getType().isAir(); }
    private boolean isHelmet(org.bukkit.Material m) { return m.name().endsWith("_HELMET") || m == org.bukkit.Material.TURTLE_HELMET || m == org.bukkit.Material.CARVED_PUMPKIN; }
    private boolean isChestArmor(org.bukkit.Material m) { return m.name().endsWith("_CHESTPLATE"); }
    private boolean isLeggings(org.bukkit.Material m) { return m.name().endsWith("_LEGGINGS"); }
    private boolean isBoots(org.bukkit.Material m) { return m.name().endsWith("_BOOTS"); }
    private boolean isOffhandItem(org.bukkit.Material m) { return m == org.bukkit.Material.SHIELD || m == org.bukkit.Material.TOTEM_OF_UNDYING; }

    private int firstFreePlayableSlot(PlayerInventory inv) {
        for (int i = 0; i <= 8; i++) {
            if (i == SLOT_CKPT || i == SLOT_LEAVE) continue;
            ItemStack it = inv.getItem(i);
            if (isEmpty(it)) return i;
        }
        for (int i = 9; i <= 35; i++) {
            ItemStack it = inv.getItem(i);
            if (isEmpty(it)) return i;
        }
        return -1;
    }

    private void restoreInventoryIfPresent(Player p, ParcourSession s) {
        ParcourInventorySnapshot snap = s.snapshot();
        if (snap != null) {
            try { snap.restore(p); } catch (Throwable t) { log.warning("Failed to restore inventory for " + p.getName() + ": " + t.getMessage()); }
            s.setSnapshot(null);
        }
    }

    private void updateRegionHighlight(Player p, ParcourDefinition def, ParcourSession s) {
        if (s == null) return;

        s.cancelParticleTask();

        Optional<String> pname = def.regionHighlightParticleName();
        if (pname.isEmpty()) return;

        final Particle particle = resolveParticle(pname.get());
        if (particle == null) return;

        ParcourRegion target = def.checkpoint(s.expectedNextOrder()).orElseGet(() -> def.endRegion().orElse(null));
        if (target == null || target.region().isEmpty()) return;
        Region r = target.region().get();

        if (!Objects.equals(p.getWorld().getName(), r.worldName())) return;

        BukkitTask task = feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(() -> {
            if (!p.isOnline()) return;
            spawnRegionOutline(p, r, particle);
        }, BukkitTime.ticks(2L), BukkitTime.ticks(PARTICLE_INTERVAL_TICKS));

        s.setParticleTask(task);
    }

    private void spawnRegionOutline(Player p, Region r, Particle particle) {
        final int minX = r.minX();
        final int minY = r.minY();
        final int minZ = r.minZ();
        final int maxX = r.maxX();
        final int maxY = r.maxY();
        final int maxZ = r.maxZ();

        int dx = Math.max(1, maxX - minX);
        int dy = Math.max(1, maxY - minY);
        int dz = Math.max(1, maxZ - minZ);

        int approxPerimeter = 2 * (dx + dz) * 2 + 4 * dy;
        int step = Math.max(1, (int) Math.ceil((double) approxPerimeter / PARTICLE_OUTLINE_TARGET_POINTS));

        for (int x = minX; x <= maxX; x += step) {
            spawnParticle(p, x, minY, minZ);
            spawnParticle(p, x, minY, maxZ);
            spawnParticle(p, x, maxY, minZ);
            spawnParticle(p, x, maxY, maxZ);
        }
        for (int z = minZ; z <= maxZ; z += step) {
            spawnParticle(p, minX, minY, z);
            spawnParticle(p, maxX, minY, z);
            spawnParticle(p, minX, maxY, z);
            spawnParticle(p, maxX, maxY, z);
        }

        for (int y = minY; y <= maxY; y += step) {
            spawnParticle(p, minX, y, minZ);
            spawnParticle(p, minX, y, maxZ);
            spawnParticle(p, maxX, y, minZ);
            spawnParticle(p, maxX, y, maxZ);
        }

        flushParticleQueue(p, particle);
    }

    private static final ThreadLocal<List<Location>> TL_PARTICLE_BUF = ThreadLocal.withInitial(ArrayList::new);

    private void spawnParticle(Player p, int bx, int by, int bz) {
        List<Location> buf = TL_PARTICLE_BUF.get();
        buf.add(new Location(p.getWorld(), bx + 0.5, by + 0.1, bz + 0.5));
    }

    private void flushParticleQueue(Player p, Particle particle) {
        List<Location> buf = TL_PARTICLE_BUF.get();
        if (buf.isEmpty()) return;
        try {
            for (Location l : buf) {
                p.spawnParticle(particle, l.getX(), l.getY(), l.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            }
        } finally {
            buf.clear();
        }
    }

    public void onQuit(Player p) {
        ParcourSession s = sessions.get(p.getUniqueId());
        if (s == null) return;
        try { cleanupEffectForSession(p, s); } catch (Throwable ignored) {}
        restoreInventoryIfPresent(p, s);
        clearSession(p);
    }

    // ---------- Utilities ----------

    private boolean contains(Region r, Location loc) {
        if (!Objects.equals(r.worldName(), loc.getWorld().getName())) return false;
        // Assume Region.contains(Location) internally uses block coordinates.
        // We mimic that behavior: floor to block coords inclusive.
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        return bx >= r.minX() && bx <= r.maxX()
                && by >= r.minY() && by <= r.maxY()
                && bz >= r.minZ() && bz <= r.maxZ();
    }

    /**
     * Robust line-segment vs. axis-aligned box intersection (slab method),
     * using continuous coordinates. Box spans full blocks: [min, max+1] on each axis.
     */
    private boolean segmentIntersectsRegion(Location a, Location b, Region r) {
        if (a == null || b == null) return false;
        if (!Objects.equals(a.getWorld().getName(), r.worldName())) return false;
        if (!Objects.equals(b.getWorld().getName(), r.worldName())) return false;

        // Expand to full block volumes
        double minX = Math.min(r.minX(), r.maxX());
        double minY = Math.min(r.minY(), r.maxY());
        double minZ = Math.min(r.minZ(), r.maxZ());
        double maxX = Math.max(r.minX(), r.maxX()) + 1.0;
        double maxY = Math.max(r.minY(), r.maxY()) + 1.0;
        double maxZ = Math.max(r.minZ(), r.maxZ()) + 1.0;

        double ax = a.getX(), ay = a.getY(), az = a.getZ();
        double bx = b.getX(), by = b.getY(), bz = b.getZ();

        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double tMin = 0.0, tMax = 1.0;

        // X
        if (Math.abs(dx) < 1e-9) {
            if (ax < minX || ax > maxX) return false;
        } else {
            double inv = 1.0 / dx;
            double t1 = (minX - ax) * inv;
            double t2 = (maxX - ax) * inv;
            if (t1 > t2) { double t = t1; t1 = t2; t2 = t; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMax < tMin) return false;
        }

        // Y
        if (Math.abs(dy) < 1e-9) {
            if (ay < minY || ay > maxY) return false;
        } else {
            double inv = 1.0 / dy;
            double t1 = (minY - ay) * inv;
            double t2 = (maxY - ay) * inv;
            if (t1 > t2) { double t = t1; t1 = t2; t2 = t; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMax < tMin) return false;
        }

        // Z
        if (Math.abs(dz) < 1e-9) {
            if (az < minZ || az > maxZ) return false;
        } else {
            double inv = 1.0 / dz;
            double t1 = (minZ - az) * inv;
            double t2 = (maxZ - az) * inv;
            if (t1 > t2) { double t = t1; t1 = t2; t2 = t; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMax < tMin) return false;
        }

        return tMax >= tMin && tMax >= 0.0 && tMin <= 1.0;
    }

    private Sound resolveSound(String raw) {
        if (raw == null || raw.isBlank()) return null;
        NamespacedKey key = NamespacedKey.fromString(raw.trim());
        if (key == null) {
            String guess = raw.trim().toLowerCase(Locale.ROOT).replace('_', '.'); // enum-style to dot style
            key = NamespacedKey.fromString("minecraft:" + guess);
        }
        if (key == null) return null;
        return BukkitRegistry.soundRegistry().get(key);
    }

    private Particle resolveParticle(String raw) {
        if (raw == null || raw.isBlank()) return null;
        NamespacedKey key = NamespacedKey.fromString(raw.trim());
        if (key == null) {
            String guess = raw.trim().toLowerCase(Locale.ROOT); // particles keep underscores
            key = NamespacedKey.fromString("minecraft:" + guess);
        }
        if (key == null) return null;
        return BukkitRegistry.particleRegistry().get(key);
    }
}
