package nl.hauntedmc.serverfeatures.features.balloons;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.balloons.internal.BalloonsHandler;
import nl.hauntedmc.serverfeatures.features.balloons.listener.BalloonsListener;
import nl.hauntedmc.serverfeatures.features.balloons.meta.Meta;
import nl.hauntedmc.serverfeatures.features.balloons.registry.BalloonRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Balloons feature, implemented in the ServerFeatures framework.
 */
public class Balloons extends BukkitBaseFeature<Meta> {

    private BalloonsHandler handler;
    private BalloonRegistry registry;

    public Balloons(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);
        Map<String, Object> defs = new LinkedHashMap<>();

        defs.put("beacon", mapOf(
                "permission", "serverfeatures.feature.balloons.beacon",
                "item", "BEACON",
                "displayname", "§bBeacon Ballon"
        ));
        defs.put("dragon", mapOf(
                "permission", "serverfeatures.feature.balloons.dragon",
                "item", "DRAGON_HEAD",
                "displayname", "§bDragon Ballon"
        ));
        defs.put("spawner", mapOf(
                "permission", "serverfeatures.feature.balloons.spawner",
                "item", "SPAWNER",
                "displayname", "§bSpawner Ballon"
        ));
        defs.put("ancient_debris", mapOf(
                "permission", "serverfeatures.feature.balloons.ancient_debris",
                "item", "ANCIENT_DEBRIS",
                "displayname", "§bAncient Debris Ballon"
        ));
        defs.put("shulkerbox", mapOf(
                "permission", "serverfeatures.feature.balloons.shulkerbox",
                "item", "SHULKER_BOX",
                "displayname", "§bShulker Box Ballon"
        ));
        defs.put("dragonegg", mapOf(
                "permission", "serverfeatures.feature.balloons.dragonegg",
                "item", "DRAGON_EGG",
                "displayname", "§bDragon Egg Ballon"
        ));
        defs.put("tnt", mapOf(
                "permission", "serverfeatures.feature.balloons.tnt",
                "item", "TNT",
                "displayname", "§bTNT Ballon"
        ));
        defs.put("netherite_block", mapOf(
                "permission", "serverfeatures.feature.balloons.netherite_block",
                "item", "NETHERITE_BLOCK",
                "displayname", "§bNetherite Block Balloon"
        ));
        defs.put("end_portal_frame", mapOf(
                "permission", "serverfeatures.feature.balloons.end_portal_frame",
                "item", "END_PORTAL_FRAME",
                "displayname", "§bEnd Portal Frame Balloon"
        ));
        defs.put("respawn_anchor", mapOf(
                "permission", "serverfeatures.feature.balloons.respawn_anchor",
                "item", "RESPAWN_ANCHOR",
                "displayname", "§bRespawn Anchor Balloon"
        ));
        defs.put("enchanting_table", mapOf(
                "permission", "serverfeatures.feature.balloons.enchanting_table",
                "item", "ENCHANTING_TABLE",
                "displayname", "§bEnchanting Table Balloon"
        ));
        defs.put("command_block", mapOf(
                "permission", "serverfeatures.feature.balloons.command_block",
                "item", "COMMAND_BLOCK",
                "displayname", "§bCommand Block Balloon"
        ));
        // Colors
        defs.put("rood", mapOf("permission","serverfeatures.feature.balloons.rood","item","RED_CONCRETE","displayname","§bRode Ballon"));
        defs.put("oranje", mapOf("permission","serverfeatures.feature.balloons.oranje","item","ORANGE_CONCRETE","displayname","§bOranje Ballon"));
        defs.put("geel", mapOf("permission","serverfeatures.feature.balloons.geel","item","YELLOW_CONCRETE","displayname","§bGele Ballon"));
        defs.put("groen", mapOf("permission","serverfeatures.feature.balloons.groen","item","LIME_CONCRETE","displayname","§bGroene Ballon"));
        defs.put("donkergroen", mapOf("permission","serverfeatures.feature.balloons.donkergroen","item","GREEN_CONCRETE","displayname","§bDonker Groene Ballon"));
        defs.put("cyan", mapOf("permission","serverfeatures.feature.balloons.cyan","item","CYAN_CONCRETE","displayname","§bCyan Ballon"));
        defs.put("blauw", mapOf("permission","serverfeatures.feature.balloons.blauw","item","LIGHT_BLUE_CONCRETE","displayname","§bBlauwe Ballon"));
        defs.put("donkerblauw", mapOf("permission","serverfeatures.feature.balloons.donkerblauw","item","BLUE_CONCRETE","displayname","§bDonker Blauwe Ballon"));
        defs.put("paars", mapOf("permission","serverfeatures.feature.balloons.paars","item","PURPLE_CONCRETE","displayname","§bPaarse Ballon"));
        defs.put("magenta", mapOf("permission","serverfeatures.feature.balloons.magenta","item","MAGENTA_CONCRETE","displayname","§bMagenta Ballon"));
        defs.put("roze", mapOf("permission","serverfeatures.feature.balloons.roze","item","PINK_CONCRETE","displayname","§bRoze Ballon"));
        defs.put("wit", mapOf("permission","serverfeatures.feature.balloons.wit","item","WHITE_CONCRETE","displayname","§bWit Ballon"));
        defs.put("grijs", mapOf("permission","serverfeatures.feature.balloons.grijs","item","LIGHT_GRAY_CONCRETE","displayname","§bGrijze Ballon"));
        defs.put("donkergrijs", mapOf("permission","serverfeatures.feature.balloons.donkergrijs","item","GRAY_CONCRETE","displayname","§bDonker Grijze Ballon"));
        defs.put("zwart", mapOf("permission","serverfeatures.feature.balloons.zwart","item","BLACK_CONCRETE","displayname","§bZwarte Ballon"));
        defs.put("bruin", mapOf("permission","serverfeatures.feature.balloons.bruin","item","BROWN_CONCRETE","displayname","§bBruine Ballon"));

        // === Heads ===
        defs.put("paasevent24", mapOf(
                "permission", "serverfeatures.feature.balloons.paasevent24",
                "head", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGYzZjRkM2EyZjQ4NDY2OTE4ZmEwNWNkNjBjOTgzMGNjMThmYzc0MzkwY2ZhZWFhZDFhMTE3Nzg2YWJhOTBjZCJ9fX0=",
                "displayname", "§bPaasEvent 2024"
        ));
        defs.put("paasevent25", mapOf(
                "permission", "serverfeatures.feature.balloons.paasevent25",
                "head", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGUzYmE1NGU0NmY0ZmNkOTI4MWVjNTkyYTVjODE3OTAxY2UzZjgxMTE4NWY3N2E5MzhjZjBjNjMzNDZlMGU2MiJ9fX0=",
                "displayname", "§bPaasEvent 2025"
        ));

        cfg.put("balloons", defs);
        return cfg;
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();

        // Menu
        m.add("balloons.menu.title", "&8&lBallonnen");
        m.add("balloons.menu.balloon.name", "&eBallon: &f{name}");
        m.add("balloons.menu.balloon.lore.allowed", "&aKlik om deze ballon te activeren.");
        m.add("balloons.menu.balloon.lore.locked", "&cJe hebt deze ballon nog niet unlocked.");
        m.add("balloons.menu.remove.name", "&c&lVerwijder Ballon");
        m.add("balloons.menu.remove.lore", "&7Klik om je huidige ballon uit te zetten.");
        m.add("balloons.menu.close.name", "&c&lMenu Sluiten");
        m.add("balloons.menu.close.lore", "&7Klik om dit menu te sluiten.");
        m.add("balloons.menu.status.active", "&6&lHuidige ballon: &r&f{name}");
        m.add("balloons.menu.status.inactive", "&6&lHuidige ballon: &r&7Geen.");
        m.add("balloons.menu.status.lore", "&7Selecteer een ballon of verwijder je ballon.");
        m.add("balloons.menu.prev", "&7« &eVorige");
        m.add("balloons.menu.next", "&eVolgende &7»");
        // Commands / status
        m.add("balloons.removed", "&7Ballon is verwijderd.");
        m.add("balloons.no_active", "&7Je hebt geen ballon actief.");
        m.add("balloons.set", "&aJe ballon is geactiveerd: &f{name}");
        m.add("balloons.cannot_open_vehicle", "&bJe kunt het ballonmenu niet openen in een voertuig.");

        return m;
    }

    @Override
    public void initialize() {
        // Registry from config
        this.registry = new BalloonRegistry(this);
        this.registry.reloadFromConfig();

        // Handler (schedules its own ticks)
        this.handler = new BalloonsHandler(this);

        // Listener + Command
        getLifecycleManager().getListenerManager().registerListener(new BalloonsListener(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new nl.hauntedmc.serverfeatures.features.balloons.command.BalloonsCommand(this));
    }

    @Override
    public void disable() {
        if (handler != null) handler.shutdown();
    }

    public BalloonsHandler getHandler() { return handler; }
    public BalloonRegistry getRegistry() { return registry; }
}
