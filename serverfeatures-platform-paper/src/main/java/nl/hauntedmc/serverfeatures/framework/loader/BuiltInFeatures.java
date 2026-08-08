package nl.hauntedmc.serverfeatures.framework.loader;

import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.actionbar.Actionbar;
import nl.hauntedmc.serverfeatures.features.afk.AFK;
import nl.hauntedmc.serverfeatures.features.antiraidfarm.AntiRaidFarm;
import nl.hauntedmc.serverfeatures.features.autolapis.AutoLapis;
import nl.hauntedmc.serverfeatures.features.autopickup.AutoPickup;
import nl.hauntedmc.serverfeatures.features.backup.Backup;
import nl.hauntedmc.serverfeatures.features.balloons.Balloons;
import nl.hauntedmc.serverfeatures.features.bettercoral.BetterCoral;
import nl.hauntedmc.serverfeatures.features.betterdoors.BetterDoors;
import nl.hauntedmc.serverfeatures.features.bossbar.Bossbars;
import nl.hauntedmc.serverfeatures.features.broadcast.Broadcast;
import nl.hauntedmc.serverfeatures.features.capacity.Capacity;
import nl.hauntedmc.serverfeatures.features.chatfilter.ChatFilter;
import nl.hauntedmc.serverfeatures.features.chatlayout.ChatLayout;
import nl.hauntedmc.serverfeatures.features.chatlog.ChatLog;
import nl.hauntedmc.serverfeatures.features.chattools.ChatTools;
import nl.hauntedmc.serverfeatures.features.combattag.CombatTag;
import nl.hauntedmc.serverfeatures.features.commandlogger.CommandLogger;
import nl.hauntedmc.serverfeatures.features.commandrelay.CommandRelay;
import nl.hauntedmc.serverfeatures.features.commandscheduler.CommandScheduler;
import nl.hauntedmc.serverfeatures.features.customrecipes.CustomRecipes;
import nl.hauntedmc.serverfeatures.features.deephaste.DeepHaste;
import nl.hauntedmc.serverfeatures.features.durabilityalert.DurabilityAlert;
import nl.hauntedmc.serverfeatures.features.economy.Economy;
import nl.hauntedmc.serverfeatures.features.enderframe.EnderFrame;
import nl.hauntedmc.serverfeatures.features.fairperks.FairPerks;
import nl.hauntedmc.serverfeatures.features.glow.Glow;
import nl.hauntedmc.serverfeatures.features.graveyard.Graveyard;
import nl.hauntedmc.serverfeatures.features.holograms.Holograms;
import nl.hauntedmc.serverfeatures.features.instaskull.InstaSkull;
import nl.hauntedmc.serverfeatures.features.invtools.InvTools;
import nl.hauntedmc.serverfeatures.features.itemedit.ItemEdit;
import nl.hauntedmc.serverfeatures.features.joinitems.JoinItems;
import nl.hauntedmc.serverfeatures.features.lagmonitor.LagMonitor;
import nl.hauntedmc.serverfeatures.features.limitspawners.LimitSpawners;
import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.lottery.Lottery;
import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import nl.hauntedmc.serverfeatures.features.nickname.Nickname;
import nl.hauntedmc.serverfeatures.features.nightvision.NightVision;
import nl.hauntedmc.serverfeatures.features.notifylogin.NotifyLogin;
import nl.hauntedmc.serverfeatures.features.parcour.Parcour;
import nl.hauntedmc.serverfeatures.features.playercount.PlayerCount;
import nl.hauntedmc.serverfeatures.features.playerlanguage.PlayerLanguage;
import nl.hauntedmc.serverfeatures.features.portals.Portals;
import nl.hauntedmc.serverfeatures.features.repairnpc.RepairNPC;
import nl.hauntedmc.serverfeatures.features.restart.Restart;
import nl.hauntedmc.serverfeatures.features.sanctions.Sanctions;
import nl.hauntedmc.serverfeatures.features.sanitize.Sanitize;
import nl.hauntedmc.serverfeatures.features.scoreboard.Scoreboard;
import nl.hauntedmc.serverfeatures.features.silkspawners.SilkSpawners;
import nl.hauntedmc.serverfeatures.features.skins.Skins;
import nl.hauntedmc.serverfeatures.features.spawnertoggle.SpawnerToggle;
import nl.hauntedmc.serverfeatures.features.staffchat.StaffChat;
import nl.hauntedmc.serverfeatures.features.tablist.Tablist;
import nl.hauntedmc.serverfeatures.features.teleportation.Teleportation;
import nl.hauntedmc.serverfeatures.features.titles.Titles;
import nl.hauntedmc.serverfeatures.features.vanish.Vanish;
import nl.hauntedmc.serverfeatures.features.versionrecommender.VersionRecommender;
import nl.hauntedmc.serverfeatures.features.villageroptimizer.VillagerOptimizer;
import nl.hauntedmc.serverfeatures.features.votereward.VoteReward;
import nl.hauntedmc.serverfeatures.features.votifier.Votifier;
import nl.hauntedmc.serverfeatures.features.whitelist.Whitelist;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.WorldEditVisualizer;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Explicit inventory of every built-in ServerFeatures implementation. */
public final class BuiltInFeatures {
    private static final String FEATURE_PACKAGE = "nl.hauntedmc.serverfeatures.features.";

    /*
     * Keep discovery metadata inert. In particular, do not put implementation class literals or
     * constructor method handles in this list. Some built-ins link optional plugin APIs (for
     * example PacketEvents); eagerly resolving those classes would crash ServerFeatures before
     * the dependency manager can prune the unavailable feature.
     */
    private static final List<Definition> DEFINITIONS = List.of(
            def("AFK", nl.hauntedmc.serverfeatures.features.afk.meta.Meta::new),
            def("Skins", nl.hauntedmc.serverfeatures.features.skins.meta.Meta::new),
            def("Backup", nl.hauntedmc.serverfeatures.features.backup.meta.Meta::new),
            def("Titles", nl.hauntedmc.serverfeatures.features.titles.meta.Meta::new),
            def("Tablist", nl.hauntedmc.serverfeatures.features.tablist.meta.Meta::new),
            def("ChatLog", nl.hauntedmc.serverfeatures.features.chatlog.meta.Meta::new),
            def("Bossbars", nl.hauntedmc.serverfeatures.features.bossbar.meta.Meta::new),
            def("Glow", nl.hauntedmc.serverfeatures.features.glow.meta.Meta::new),
            def("Sanitize", nl.hauntedmc.serverfeatures.features.sanitize.meta.Meta::new),
            def("Balloons", nl.hauntedmc.serverfeatures.features.balloons.meta.Meta::new),
            def("ItemEdit", nl.hauntedmc.serverfeatures.features.itemedit.meta.Meta::new),
            def("Whitelist", nl.hauntedmc.serverfeatures.features.whitelist.meta.Meta::new),
            def("ChatTools", nl.hauntedmc.serverfeatures.features.chattools.meta.Meta::new),
            def("StaffChat", nl.hauntedmc.serverfeatures.features.staffchat.meta.Meta::new),
            def("AutoLapis", nl.hauntedmc.serverfeatures.features.autolapis.meta.Meta::new),
            def("DeepHaste", nl.hauntedmc.serverfeatures.features.deephaste.meta.Meta::new),
            def("JoinItems", nl.hauntedmc.serverfeatures.features.joinitems.meta.Meta::new),
            def("Sanctions", nl.hauntedmc.serverfeatures.features.sanctions.meta.Meta::new),
            def("Holograms", nl.hauntedmc.serverfeatures.features.holograms.meta.Meta::new),
            def("Actionbar", nl.hauntedmc.serverfeatures.features.actionbar.meta.Meta::new),
            def("Broadcast", nl.hauntedmc.serverfeatures.features.broadcast.meta.Meta::new),
            def("Scoreboard", nl.hauntedmc.serverfeatures.features.scoreboard.meta.Meta::new),
            def("InstaSkull", nl.hauntedmc.serverfeatures.features.instaskull.meta.Meta::new),
            def("EnderFrame", nl.hauntedmc.serverfeatures.features.enderframe.meta.Meta::new),
            def("LiquidTank", nl.hauntedmc.serverfeatures.features.liquidtank.meta.Meta::new),
            def("LagMonitor", nl.hauntedmc.serverfeatures.features.lagmonitor.meta.Meta::new),
            def("VoteReward", nl.hauntedmc.serverfeatures.features.votereward.meta.Meta::new),
            def("RepairNPC", nl.hauntedmc.serverfeatures.features.repairnpc.meta.Meta::new),
            def("BetterCoral", nl.hauntedmc.serverfeatures.features.bettercoral.meta.Meta::new),
            def("NightVision", nl.hauntedmc.serverfeatures.features.nightvision.meta.Meta::new),
            def("BetterDoors", nl.hauntedmc.serverfeatures.features.betterdoors.meta.Meta::new),
            def("NotifyLogin", nl.hauntedmc.serverfeatures.features.notifylogin.meta.Meta::new),
            def("AntiRaidFarm", nl.hauntedmc.serverfeatures.features.antiraidfarm.meta.Meta::new),
            def("SilkSpawners", nl.hauntedmc.serverfeatures.features.silkspawners.meta.Meta::new),
            def("Nickname", nl.hauntedmc.serverfeatures.features.nickname.meta.Meta::new),
            def("CustomRecipes", nl.hauntedmc.serverfeatures.features.customrecipes.meta.Meta::new),
            def("CommandLogger", nl.hauntedmc.serverfeatures.features.commandlogger.meta.Meta::new),
            def("Portals", nl.hauntedmc.serverfeatures.features.portals.meta.Meta::new),
            def("PlayerLanguage", nl.hauntedmc.serverfeatures.features.playerlanguage.meta.Meta::new),
            def("DurabilityAlert", nl.hauntedmc.serverfeatures.features.durabilityalert.meta.Meta::new),
            def("ChatFilter", nl.hauntedmc.serverfeatures.features.chatfilter.meta.Meta::new),
            def("VillagerOptimizer", nl.hauntedmc.serverfeatures.features.villageroptimizer.meta.Meta::new),
            def("Teleportation", nl.hauntedmc.serverfeatures.features.teleportation.meta.Meta::new),
            def("VersionRecommender", nl.hauntedmc.serverfeatures.features.versionrecommender.meta.Meta::new),
            def("Capacity", nl.hauntedmc.serverfeatures.features.capacity.meta.Meta::new),
            def("Votifier", nl.hauntedmc.serverfeatures.features.votifier.meta.Meta::new),
            def("ChatLayout", nl.hauntedmc.serverfeatures.features.chatlayout.meta.Meta::new),
            def("Nametags", nl.hauntedmc.serverfeatures.features.nametags.meta.Meta::new),
            def("SpawnerToggle", nl.hauntedmc.serverfeatures.features.spawnertoggle.meta.Meta::new),
            def("PlayerCount", nl.hauntedmc.serverfeatures.features.playercount.meta.Meta::new),
            def("Vanish", nl.hauntedmc.serverfeatures.features.vanish.meta.Meta::new),
            def("Parcour", nl.hauntedmc.serverfeatures.features.parcour.meta.Meta::new),
            def("CommandRelay", nl.hauntedmc.serverfeatures.features.commandrelay.meta.Meta::new),
            def("WorldEditVisualizer", nl.hauntedmc.serverfeatures.features.worldeditvisualizer.meta.Meta::new),
            def("Economy", nl.hauntedmc.serverfeatures.features.economy.meta.Meta::new),
            def("AutoPickup", nl.hauntedmc.serverfeatures.features.autopickup.meta.Meta::new),
            def("Restart", nl.hauntedmc.serverfeatures.features.restart.meta.Meta::new),
            def("InvTools", nl.hauntedmc.serverfeatures.features.invtools.meta.Meta::new),
            def("LimitSpawners", nl.hauntedmc.serverfeatures.features.limitspawners.meta.Meta::new),
            def("CombatTag", nl.hauntedmc.serverfeatures.features.combattag.meta.Meta::new),
            def("Lottery", nl.hauntedmc.serverfeatures.features.lottery.meta.Meta::new),
            def("Graveyard", nl.hauntedmc.serverfeatures.features.graveyard.meta.Meta::new),
            def("CommandScheduler", nl.hauntedmc.serverfeatures.features.commandscheduler.meta.Meta::new),
            def("FairPerks", nl.hauntedmc.serverfeatures.features.fairperks.meta.Meta::new)
    );
    private static final Map<String, Definition> BY_IMPLEMENTATION = indexByImplementation();

    static {
        if (DEFINITIONS.size() != 64) {
            throw new IllegalStateException("Expected 64 built-in ServerFeatures definitions");
        }
        Set<String> names = new HashSet<>();
        Set<String> implementations = new HashSet<>();
        for (Definition definition : DEFINITIONS) {
            if (!names.add(definition.registryName().toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException("Duplicate feature name: " + definition.registryName());
            }
            if (!implementations.add(definition.implementationClassName())) {
                throw new IllegalStateException("Duplicate feature implementation: "
                        + definition.implementationClassName());
            }
        }
    }

    private BuiltInFeatures() { }

    public static List<Definition> definitions() {
        return DEFINITIONS;
    }

    public static Optional<Definition> findByImplementationClassName(String implementationClassName) {
        if (implementationClassName == null || implementationClassName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_IMPLEMENTATION.get(implementationClassName));
    }

    private static Map<String, Definition> indexByImplementation() {
        Map<String, Definition> definitions = new LinkedHashMap<>();
        for (Definition definition : DEFINITIONS) {
            Definition previous = definitions.put(definition.implementationClassName(), definition);
            if (previous != null) {
                throw new IllegalStateException("Duplicate feature implementation: "
                        + definition.implementationClassName());
            }
        }
        return Map.copyOf(definitions);
    }

    private static Definition def(String registryName, Supplier<? extends BaseMeta> metaFactory) {
        String packageName = registryName.equals("Bossbars")
                ? "bossbar"
                : registryName.toLowerCase(Locale.ROOT);
        return new Definition(
                registryName,
                FEATURE_PACKAGE + packageName + "." + registryName,
                metaFactory
        );
    }

    @SuppressWarnings("unchecked")
    private static <M extends BaseMeta> FeatureContext<M> castContext(FeatureContext<?> context) {
        return (FeatureContext<M>) Objects.requireNonNull(context, "context");
    }

    private static BukkitBaseFeature<?> instantiate(String registryName, FeatureContext<?> context) {
        Objects.requireNonNull(context, "context");
        return switch (registryName) {
            case "AFK" -> new AFK(castContext(context));
            case "Skins" -> new Skins(castContext(context));
            case "Backup" -> new Backup(castContext(context));
            case "Titles" -> new Titles(castContext(context));
            case "Tablist" -> new Tablist(castContext(context));
            case "ChatLog" -> new ChatLog(castContext(context));
            case "Bossbars" -> new Bossbars(castContext(context));
            case "Glow" -> new Glow(castContext(context));
            case "Sanitize" -> new Sanitize(castContext(context));
            case "Balloons" -> new Balloons(castContext(context));
            case "ItemEdit" -> new ItemEdit(castContext(context));
            case "Whitelist" -> new Whitelist(castContext(context));
            case "ChatTools" -> new ChatTools(castContext(context));
            case "StaffChat" -> new StaffChat(castContext(context));
            case "AutoLapis" -> new AutoLapis(castContext(context));
            case "DeepHaste" -> new DeepHaste(castContext(context));
            case "JoinItems" -> new JoinItems(castContext(context));
            case "Sanctions" -> new Sanctions(castContext(context));
            case "Holograms" -> new Holograms(castContext(context));
            case "Actionbar" -> new Actionbar(castContext(context));
            case "Broadcast" -> new Broadcast(castContext(context));
            case "Scoreboard" -> new Scoreboard(castContext(context));
            case "InstaSkull" -> new InstaSkull(castContext(context));
            case "EnderFrame" -> new EnderFrame(castContext(context));
            case "LiquidTank" -> new LiquidTank(castContext(context));
            case "LagMonitor" -> new LagMonitor(castContext(context));
            case "VoteReward" -> new VoteReward(castContext(context));
            case "RepairNPC" -> new RepairNPC(castContext(context));
            case "BetterCoral" -> new BetterCoral(castContext(context));
            case "NightVision" -> new NightVision(castContext(context));
            case "BetterDoors" -> new BetterDoors(castContext(context));
            case "NotifyLogin" -> new NotifyLogin(castContext(context));
            case "AntiRaidFarm" -> new AntiRaidFarm(castContext(context));
            case "SilkSpawners" -> new SilkSpawners(castContext(context));
            case "Nickname" -> new Nickname(castContext(context));
            case "CustomRecipes" -> new CustomRecipes(castContext(context));
            case "CommandLogger" -> new CommandLogger(castContext(context));
            case "Portals" -> new Portals(castContext(context));
            case "PlayerLanguage" -> new PlayerLanguage(castContext(context));
            case "DurabilityAlert" -> new DurabilityAlert(castContext(context));
            case "ChatFilter" -> new ChatFilter(castContext(context));
            case "VillagerOptimizer" -> new VillagerOptimizer(castContext(context));
            case "Teleportation" -> new Teleportation(castContext(context));
            case "VersionRecommender" -> new VersionRecommender(castContext(context));
            case "Capacity" -> new Capacity(castContext(context));
            case "Votifier" -> new Votifier(castContext(context));
            case "ChatLayout" -> new ChatLayout(castContext(context));
            case "Nametags" -> new Nametags(castContext(context));
            case "SpawnerToggle" -> new SpawnerToggle(castContext(context));
            case "PlayerCount" -> new PlayerCount(castContext(context));
            case "Vanish" -> new Vanish(castContext(context));
            case "Parcour" -> new Parcour(castContext(context));
            case "CommandRelay" -> new CommandRelay(castContext(context));
            case "WorldEditVisualizer" -> new WorldEditVisualizer(castContext(context));
            case "Economy" -> new Economy(castContext(context));
            case "AutoPickup" -> new AutoPickup(castContext(context));
            case "Restart" -> new Restart(castContext(context));
            case "InvTools" -> new InvTools(castContext(context));
            case "LimitSpawners" -> new LimitSpawners(castContext(context));
            case "CombatTag" -> new CombatTag(castContext(context));
            case "Lottery" -> new Lottery(castContext(context));
            case "Graveyard" -> new Graveyard(castContext(context));
            case "CommandScheduler" -> new CommandScheduler(castContext(context));
            case "FairPerks" -> new FairPerks(castContext(context));
            default -> throw new IllegalArgumentException("Unknown built-in feature: " + registryName);
        };
    }

    /**
     * Compatibility view for the transitional loader. This intentionally contains only the
     * implementation binary name; it must not resolve the implementation class during discovery.
     */
    public record ImplementationType(String name) {
        public ImplementationType {
            name = Objects.requireNonNull(name, "name");
        }

        public String getName() {
            return name;
        }
    }

    public record Definition(
            String registryName,
            String implementationClassName,
            Supplier<? extends BaseMeta> metaFactory
    ) {
        public Definition {
            registryName = Objects.requireNonNull(registryName, "registryName");
            implementationClassName = Objects.requireNonNull(implementationClassName, "implementationClassName");
            metaFactory = Objects.requireNonNull(metaFactory, "metaFactory");
        }

        public ImplementationType implementationType() {
            return new ImplementationType(implementationClassName);
        }

        public BaseMeta createMeta() {
            return Objects.requireNonNull(metaFactory.get(), "metaFactory returned null");
        }

        public BukkitBaseFeature<?> createFeature(FeatureContext<?> context) {
            return Objects.requireNonNull(
                    instantiate(registryName, Objects.requireNonNull(context, "context")),
                    "feature instantiation returned null"
            );
        }
    }
}
