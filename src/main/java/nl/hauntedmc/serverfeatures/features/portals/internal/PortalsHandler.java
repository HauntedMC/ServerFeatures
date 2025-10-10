package nl.hauntedmc.serverfeatures.features.portals.internal;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.portals.Portals;
import nl.hauntedmc.serverfeatures.features.portals.model.CommandExecutor;
import nl.hauntedmc.serverfeatures.features.portals.model.PortalDefinition;
import nl.hauntedmc.serverfeatures.features.portals.model.PortalMode;
import nl.hauntedmc.serverfeatures.features.portals.model.Region;
import nl.hauntedmc.serverfeatures.features.portals.registry.PortalRegistry;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import org.bukkit.*;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PortalsHandler {

    private static final long TRIGGER_COOLDOWN_MS = 1000L; // prevent spam while standing in region
    private static final String WAND_NAME = "§6Portals Wand";

    private final Portals feature;
    private final PortalRegistry registry;
    private final FeatureLogger log;
    private final NamespacedKey wandKey;

    // Selection state per player (for editing)
    public static final class Selection {
        public String selectedPortalId; // nullable
        public String world1;
        public Integer x1, y1, z1;
        public String world2;
        public Integer x2, y2, z2;

        public boolean hasBoth() {
            return world1 != null && x1 != null && y1 != null && z1 != null
                    && world2 != null && x2 != null && y2 != null && z2 != null
                    && Objects.equals(world1, world2);
        }

        public Region toRegionOrNull() {
            if (!hasBoth()) return null;
            return new Region(world1, x1, y1, z1, x2, y2, z2);
        }

        public void clearRegion() {
            world1 = world2 = null;
            x1 = y1 = z1 = x2 = y2 = z2 = null;
        }
    }

    private final Map<UUID, Selection> selections = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTrigger = new ConcurrentHashMap<>();

    public PortalsHandler(Portals feature, PortalRegistry registry) {
        this.feature = feature;
        this.registry = registry;
        this.log = feature.getLogger();
        this.wandKey = new NamespacedKey(feature.getPlugin(), "portals_wand");
    }

    public NamespacedKey getWandKey() {
        return wandKey;
    }

    public Selection selection(Player p) {
        return selections.computeIfAbsent(p.getUniqueId(), k -> new Selection());
    }

    public void setPos1(Player p, Location loc) {
        var s = selection(p);
        s.world1 = loc.getWorld().getName();
        s.x1 = loc.getBlockX();
        s.y1 = loc.getBlockY();
        s.z1 = loc.getBlockZ();
    }

    public void setPos2(Player p, Location loc) {
        var s = selection(p);
        s.world2 = loc.getWorld().getName();
        s.x2 = loc.getBlockX();
        s.y2 = loc.getBlockY();
        s.z2 = loc.getBlockZ();
    }

    public boolean saveRegionToSelected(Player p) {
        var s = selection(p);
        if (s.selectedPortalId == null) return false;
        if (!s.hasBoth()) return false;
        Region r = s.toRegionOrNull();
        if (r == null) return false;

        Optional<PortalDefinition> opt = registry.get(s.selectedPortalId);
        if (opt.isEmpty()) return false;
        PortalDefinition def = opt.get();
        def.setRegion(r);
        registry.savePortal(def);
        log.info("Region saved for portal '" + def.id() + "' by " + p.getName());
        return true;
    }

    public boolean selectPortal(Player p, String id) {
        if (registry.get(id).isEmpty()) return false;
        selection(p).selectedPortalId = id;
        return true;
    }

    // ===== Wand helpers =====

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

    // ===== Trigger logic - called by listener =====
    public void tryTrigger(Player p, Location to) {
        long now = System.currentTimeMillis();
        Long prev = lastTrigger.get(p.getUniqueId());
        if (prev != null && (now - prev) < TRIGGER_COOLDOWN_MS) return;

        // Find first portal whose region contains 'to'
        for (PortalDefinition def : registry.all()) {
            if (def.region().isEmpty()) continue;
            Region r = def.region().get();
            if (!r.worldName().equals(to.getWorld().getName())) continue;
            if (!r.contains(to)) continue;

            if (def.exclusiveBlock().isPresent()) {
                Material required = def.exclusiveBlock().get();
                Material inBlock = to.getBlock().getType();
                if (inBlock != required) {
                    // Not inside the specified block -> do not trigger
                    continue;
                }
            }

            // Trigger
            handlePortalEnter(p, def);
            lastTrigger.put(p.getUniqueId(), now);
            break; // one portal at a time
        }
    }

    private void handlePortalEnter(Player p, PortalDefinition def) {
        // Perform primary action first
        if (def.mode() == PortalMode.TELEPORT) {
            var wOpt = def.resolveTargetWorld();
            if (wOpt.isEmpty()) {
                log.warning("Portal '" + def.id() + "' teleport world not found: " + def.targetWorld().orElse("null"));
                return;
            }
            Location dst = new Location(wOpt.get(), def.tx(), def.ty(), def.tz(), def.tyaw(), def.tpitch());
            p.teleport(dst);
            log.info("Teleported " + p.getName() + " via portal '" + def.id() + "' to " +
                    dst.getWorld().getName() + " " + dst.getX() + " " + dst.getY() + " " + dst.getZ());
        } else if (def.mode() == PortalMode.COMMAND) {
            String cmd = def.command().orElse("");
            if (!cmd.isBlank()) {
                switch (def.executor()) {
                    case PLAYER -> {
                        boolean ok = p.performCommand(cmd);
                        log.info("Player '" + p.getName() + "' executed command via portal '" + def.id() + "': /" + cmd + " (ok=" + ok + ")");
                    }
                    case CONSOLE -> {
                        ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
                        boolean ok = Bukkit.dispatchCommand(console, cmd.replace("{player}", p.getName()));
                        log.info("Console executed command for '" + p.getName() + "' via portal '" + def.id() + "': /" + cmd + " (ok=" + ok + ")");
                    }
                }
            }
        } else if (def.mode() == PortalMode.SERVER) {
            var targetOpt = def.serverTarget();
            if (targetOpt.isEmpty() || targetOpt.get().isBlank()) {
                p.sendMessage(feature.getLocalizationHandler().getMessage("portals.server.missing").forAudience(p).build());
                log.warning("Portal '" + def.id() + "' has SERVER mode but no target server set.");
                return;
            }
            String server = targetOpt.get();
            connectPlayerToServer(p, server);
            log.info("Sent " + p.getName() + " to server '" + server + "' via portal '" + def.id() + "'");
        }

        // Schedule feedback effects after action
        scheduleEffects(p, def);
    }

    private void scheduleEffects(Player p, PortalDefinition def) {
        // Sound
        def.sound().ifPresent((Sound s) -> {
            int delay = Math.max(0, def.soundDelay());
            feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
                if (!p.isOnline()) return;
                p.playSound(p.getLocation(), s, 1.0f, 1.0f);
            }, BukkitTime.ticks(delay));
        });

        // Particle
        def.particle().ifPresent((org.bukkit.Particle part) -> {
            int delay = Math.max(0, def.particleDelay());
            feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
                if (!p.isOnline()) return;
                Location loc = p.getLocation();
                // A small burst around player
                p.spawnParticle(part, loc, 30, 0.3, 0.6, 0.3, 0.0);
            }, BukkitTime.ticks(delay));
        });
    }


    /** Send a legacy BungeeCord/Velocity plugin message to connect the player to another server. */
    private void connectPlayerToServer(Player player, String serverName) {
        try (ByteArrayOutputStream b = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(b)) {
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            player.sendPluginMessage(feature.getPlugin(), "BungeeCord", b.toByteArray());
        } catch (Exception e) {
            log.severe("Failed to send player to server '" + serverName + "': " + e.getMessage());
        }
    }

    // ===== Editing helpers =====

    public boolean createPortal(String id) {
        if (registry.get(id).isPresent()) return false;
        registry.savePortal(new PortalDefinition(id));
        log.info("Created portal '" + id + "'");
        return true;
    }

    public boolean setMode(String id, String modeStr) {
        Optional<PortalDefinition> opt = registry.get(id);
        if (opt.isEmpty()) return false;
        PortalDefinition def = opt.get();
        try {
            def.setMode(PortalMode.valueOf(modeStr.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return false;
        }
        registry.savePortal(def);
        return true;
    }

    public boolean setTeleport(String id, String world, double x, double y, double z, float yaw, float pitch) {
        Optional<PortalDefinition> opt = registry.get(id);
        if (opt.isEmpty()) return false;
        PortalDefinition def = opt.get();
        def.setTeleport(world, x, y, z, yaw, pitch);
        registry.savePortal(def);
        return true;
    }

    public boolean setTeleportFromPlayer(String id, Player p) {
        var loc = p.getLocation();
        return setTeleport(id, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
    }

    public boolean setCommand(String id, String cmd, CommandExecutor ex) {
        Optional<PortalDefinition> opt = registry.get(id);
        if (opt.isEmpty()) return false;
        PortalDefinition def = opt.get();
        def.setCommand(cmd, ex);
        registry.savePortal(def);
        return true;
    }

    public boolean setServer(String id, String serverName) {
        Optional<PortalDefinition> opt = registry.get(id);
        if (opt.isEmpty()) return false;
        PortalDefinition def = opt.get();
        def.setServerTarget(serverName);
        registry.savePortal(def);
        return true;
    }

    public enum ExclusiveBlockResult { SET, CLEARED, INVALID, NOT_FOUND }

    public ExclusiveBlockResult setExclusiveBlock(String id, String blockName) {
        Optional<PortalDefinition> opt = registry.get(id);
        if (opt.isEmpty()) return ExclusiveBlockResult.NOT_FOUND;

        PortalDefinition def = opt.get();

        if (blockName.equalsIgnoreCase("none") || blockName.equalsIgnoreCase("clear")) {
            def.clearExclusiveBlock();
            registry.savePortal(def);
            return ExclusiveBlockResult.CLEARED;
        }

        Material m = Material.matchMaterial(blockName);
        if (m == null) m = Material.matchMaterial(blockName.toUpperCase(Locale.ROOT));
        if (m == null || !m.isBlock()) {
            return ExclusiveBlockResult.INVALID;
        }

        def.setExclusiveBlock(m);
        registry.savePortal(def);
        return ExclusiveBlockResult.SET;
    }

    public boolean delete(String id) {
        return registry.deletePortal(id);
    }
}
