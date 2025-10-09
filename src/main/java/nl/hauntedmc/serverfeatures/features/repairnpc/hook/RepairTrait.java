package nl.hauntedmc.serverfeatures.features.repairnpc.hook;

import java.util.*;

import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.util.DataKey;

import net.milkbowl.vault.economy.Economy;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import org.bukkit.Material;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;

import nl.hauntedmc.serverfeatures.features.repairnpc.RepairNPC;
import nl.hauntedmc.serverfeatures.features.repairnpc.util.EcoUtil;
import nl.hauntedmc.serverfeatures.features.repairnpc.util.ItemUtil;
import org.bukkit.inventory.meta.ItemMeta;

public class RepairTrait extends Trait {
    private final RepairNPC feature = RepairNPC.getInstance();
    private Economy economy;

    private final Map<String, Calendar> cooldowns = new HashMap<>();

    private final int minDelay = ((Number) feature.getConfigHandler().getSetting("delays-in-seconds.minimum")).intValue();
    private final int maxDelay = ((Number) feature.getConfigHandler().getSetting("delays-in-seconds.maximum")).intValue();
    private final int cooldown = ((Number) feature.getConfigHandler().getSetting("delays-in-seconds.reforge-cooldown")).intValue();
    private final boolean dropItem = (Boolean) feature.getConfigHandler().getSetting("dropitem");
    private final boolean noCooldown = (Boolean) feature.getConfigHandler().getSetting("disablecooldown");
    private final boolean noDelay = (Boolean) feature.getConfigHandler().getSetting("disabledelay");

    private RepairSession session;
    private long sessionStart;

    public RepairTrait() {
        super("repair");
        setupVault();
    }

    private void setupVault() {
        var registration = feature.getPlugin()
                .getServer()
                .getServicesManager()
                .getRegistration(Economy.class);

        if (registration != null) {
            economy = registration.getProvider();
        } else {
            feature.getLogger()
                    .severe("Failed to load Vault economy; RepairNPC will not function.");
        }
    }

    @Override
    public void load(DataKey key) {
    }

    @EventHandler
    public void onLeftClick(NPCLeftClickEvent evt) {
        if (evt.getNPC() != npc) return;
        Player p = evt.getClicker();

        if (noCooldown) cooldowns.remove(p.getName());
        if (!p.hasPermission("serverfeatures.feature.repairnpc.use")) return;
        if (checkCooldown(p)) return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        Material type = hand.getType();
        if (type.toString().endsWith("_SWORD")
                || type.toString().endsWith("_AXE")
                || type == Material.TRIDENT) {
            ((Damageable) npc.getEntity()).damage(1.0);
            p.sendMessage(feature.getLocalizationHandler()
                    .getMessage("repairnpc.auw").forAudience(p).build());
            return;
        }
        handleInteraction(p, hand);
    }

    @EventHandler
    public void onRightClick(NPCRightClickEvent evt) {
        if (evt.getNPC() != npc) return;
        Player p = evt.getClicker();

        if (noCooldown) cooldowns.remove(p.getName());
        if (!p.hasPermission("serverfeatures.feature.repairnpc.use")) return;
        if (checkCooldown(p)) return;

        handleInteraction(p, p.getInventory().getItemInMainHand());
    }

    private boolean checkCooldown(Player p) {
        Calendar end = cooldowns.get(p.getName());
        if (end != null && !Calendar.getInstance().after(end)) {
            p.sendMessage(feature.getLocalizationHandler()
                    .getMessage("repairnpc.cooldown-not-expired")
                    .forAudience(p).build());
            return true;
        }
        cooldowns.remove(p.getName());
        return false;
    }

    private void handleInteraction(Player p, ItemStack hand) {
        // clear stale session
        if (session != null && (System.currentTimeMillis() > sessionStart + 10_000
                || npc.getEntity().getLocation().distance(p.getLocation()) > 20)) {
            session = null;
        }

        if (session != null) {
            if (!session.isInSession(p)) {
                p.sendMessage(feature.getLocalizationHandler()
                        .getMessage("repairnpc.busy-with-player")
                        .forAudience(p).build());
                return;
            }
            if (session.isRunning()) {
                p.sendMessage(feature.getLocalizationHandler()
                        .getMessage("repairnpc.busy-with-reforge")
                        .forAudience(p).build());
                return;
            }
            if (session.handleClick()) {
                session = null;
            } else {
                startReforge(p);
            }
            return;
        }

        // first click: validate & show cost
        if ((!ItemUtil.isTool(hand) && !ItemUtil.isArmor(hand))) {
            p.sendMessage(feature.getLocalizationHandler()
                    .getMessage("repairnpc.invalid-item")
                    .forAudience(p).build());
            return;
        }
        if (!EcoUtil.doesPlayerHaveEnough(p, economy)) {
            p.sendMessage(feature.getLocalizationHandler()
                    .getMessage("repairnpc.insufficient-funds")
                    .forAudience(p).build());
            return;
        }

        sessionStart = System.currentTimeMillis();
        session = new RepairSession(p, npc);
        String cost = EcoUtil.formatCost(p, economy);
        p.sendMessage(feature.getLocalizationHandler()
                .getMessage("repairnpc.cost")
                .forAudience(p)
                .withPlaceholders(Map.of("price", cost,
                        "item", hand.getType().name().toLowerCase().replace('_', ' ')))
                .build());
    }

    private void startReforge(Player p) {
        p.sendMessage(feature.getLocalizationHandler()
                .getMessage("repairnpc.start-reforge")
                .forAudience(p).build());

        EcoUtil.withdraw(p, economy);
        session.beginRepair();

        var ent = npc.getEntity();
        if (ent instanceof Player npcPlayer) {
            npcPlayer.getInventory().setItemInMainHand(p.getInventory().getItemInMainHand());
        } else if (ent instanceof LivingEntity le) {
            Objects.requireNonNull(le.getEquipment()).setItemInMainHand(p.getInventory().getItemInMainHand());
        }
        p.getInventory().setItemInMainHand(null);
    }

    @Override
    public void save(DataKey key) { /* no per-NPC data */ }

    private class RepairSession implements Runnable {
        private final Player player;
        private final NPC npc;
        private final ItemStack repairingItem;
        private int taskId;

        RepairSession(Player p, NPC npc) {
            this.player = p;
            this.npc = npc;
            this.repairingItem = p.getInventory().getItemInMainHand();
        }

        @Override
        public void run() {
            repairItem();
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("repairnpc.successful-reforge")
                    .forAudience(player).build());

            // clear NPC’s hand
            var ent = npc.getEntity();
            if (ent instanceof Player np) {
                np.getInventory().setItemInMainHand(null);
            } else if (ent instanceof LivingEntity le) {
                Objects.requireNonNull(le.getEquipment()).setItemInMainHand(null);
            }

            // return or drop item
            if (!noDelay) {
                if (dropItem) {
                    player.getWorld().dropItemNaturally(
                            npc.getEntity().getLocation(), repairingItem);
                } else {
                    player.getInventory().addItem(repairingItem);
                }
            } else {
                player.getInventory().setItemInMainHand(repairingItem);
            }

            // schedule cooldown
            if (!noCooldown) {
                Calendar next = Calendar.getInstance();
                next.add(Calendar.SECOND, cooldown);
                cooldowns.put(player.getName(), next);
            }
            session = null;
        }

        private void repairItem() {
            ItemMeta itemMeta = repairingItem.getItemMeta();
            ((org.bukkit.inventory.meta.Damageable) itemMeta).setDamage(0);
            repairingItem.setItemMeta(itemMeta);
        }

        boolean handleClick() {
            if (!repairingItem.equals(player.getInventory().getItemInMainHand())) {
                player.sendMessage(feature.getLocalizationHandler()
                        .getMessage("repairnpc.item-changed-during-reforge")
                        .forAudience(player).build());
                return true;
            }
            if (!EcoUtil.doesPlayerHaveEnough(player, economy)) {
                player.sendMessage(feature.getLocalizationHandler()
                        .getMessage("repairnpc.insufficient-funds")
                        .forAudience(player).build());
                return true;
            }
            return false;
        }

        boolean isRunning() {
            return feature.getLifecycleManager().getTaskManager().isTaskQueued(taskId);
        }

        boolean isInSession(Player other) {
            return player.getUniqueId().equals(other.getUniqueId());
        }

        void beginRepair() {
            int ticks = noDelay ? 0
                    : ((new Random().nextInt(maxDelay - minDelay + 1) + minDelay) * 20);

            taskId = feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(this, BukkitTime.ticks(ticks)).getTaskId();
        }
    }
}
