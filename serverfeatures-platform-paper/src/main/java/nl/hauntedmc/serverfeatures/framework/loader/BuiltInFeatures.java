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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Explicit inventory of every built-in ServerFeatures implementation. */
public final class BuiltInFeatures {
    private static final List<Definition> DEFINITIONS = List.of(
            def(AFK.class, nl.hauntedmc.serverfeatures.features.afk.meta.Meta::new, AFK::new),
            def(Skins.class, nl.hauntedmc.serverfeatures.features.skins.meta.Meta::new, Skins::new),
            def(Backup.class, nl.hauntedmc.serverfeatures.features.backup.meta.Meta::new, Backup::new),
            def(Titles.class, nl.hauntedmc.serverfeatures.features.titles.meta.Meta::new, Titles::new),
            def(Tablist.class, nl.hauntedmc.serverfeatures.features.tablist.meta.Meta::new, Tablist::new),
            def(ChatLog.class, nl.hauntedmc.serverfeatures.features.chatlog.meta.Meta::new, ChatLog::new),
            def(Bossbars.class, nl.hauntedmc.serverfeatures.features.bossbar.meta.Meta::new, Bossbars::new),
            def(Glow.class, nl.hauntedmc.serverfeatures.features.glow.meta.Meta::new, Glow::new),
            def(Sanitize.class, nl.hauntedmc.serverfeatures.features.sanitize.meta.Meta::new, Sanitize::new),
            def(Balloons.class, nl.hauntedmc.serverfeatures.features.balloons.meta.Meta::new, Balloons::new),
            def(ItemEdit.class, nl.hauntedmc.serverfeatures.features.itemedit.meta.Meta::new, ItemEdit::new),
            def(Whitelist.class, nl.hauntedmc.serverfeatures.features.whitelist.meta.Meta::new, Whitelist::new),
            def(ChatTools.class, nl.hauntedmc.serverfeatures.features.chattools.meta.Meta::new, ChatTools::new),
            def(StaffChat.class, nl.hauntedmc.serverfeatures.features.staffchat.meta.Meta::new, StaffChat::new),
            def(AutoLapis.class, nl.hauntedmc.serverfeatures.features.autolapis.meta.Meta::new, AutoLapis::new),
            def(DeepHaste.class, nl.hauntedmc.serverfeatures.features.deephaste.meta.Meta::new, DeepHaste::new),
            def(JoinItems.class, nl.hauntedmc.serverfeatures.features.joinitems.meta.Meta::new, JoinItems::new),
            def(Sanctions.class, nl.hauntedmc.serverfeatures.features.sanctions.meta.Meta::new, Sanctions::new),
            def(Holograms.class, nl.hauntedmc.serverfeatures.features.holograms.meta.Meta::new, Holograms::new),
            def(Actionbar.class, nl.hauntedmc.serverfeatures.features.actionbar.meta.Meta::new, Actionbar::new),
            def(Broadcast.class, nl.hauntedmc.serverfeatures.features.broadcast.meta.Meta::new, Broadcast::new),
            def(Scoreboard.class, nl.hauntedmc.serverfeatures.features.scoreboard.meta.Meta::new, Scoreboard::new),
            def(InstaSkull.class, nl.hauntedmc.serverfeatures.features.instaskull.meta.Meta::new, InstaSkull::new),
            def(EnderFrame.class, nl.hauntedmc.serverfeatures.features.enderframe.meta.Meta::new, EnderFrame::new),
            def(LiquidTank.class, nl.hauntedmc.serverfeatures.features.liquidtank.meta.Meta::new, LiquidTank::new),
            def(LagMonitor.class, nl.hauntedmc.serverfeatures.features.lagmonitor.meta.Meta::new, LagMonitor::new),
            def(VoteReward.class, nl.hauntedmc.serverfeatures.features.votereward.meta.Meta::new, VoteReward::new),
            def(RepairNPC.class, nl.hauntedmc.serverfeatures.features.repairnpc.meta.Meta::new, RepairNPC::new),
            def(BetterCoral.class, nl.hauntedmc.serverfeatures.features.bettercoral.meta.Meta::new, BetterCoral::new),
            def(NightVision.class, nl.hauntedmc.serverfeatures.features.nightvision.meta.Meta::new, NightVision::new),
            def(BetterDoors.class, nl.hauntedmc.serverfeatures.features.betterdoors.meta.Meta::new, BetterDoors::new),
            def(NotifyLogin.class, nl.hauntedmc.serverfeatures.features.notifylogin.meta.Meta::new, NotifyLogin::new),
            def(AntiRaidFarm.class, nl.hauntedmc.serverfeatures.features.antiraidfarm.meta.Meta::new, AntiRaidFarm::new),
            def(SilkSpawners.class, nl.hauntedmc.serverfeatures.features.silkspawners.meta.Meta::new, SilkSpawners::new),
            def(Nickname.class, nl.hauntedmc.serverfeatures.features.nickname.meta.Meta::new, Nickname::new),
            def(CustomRecipes.class, nl.hauntedmc.serverfeatures.features.customrecipes.meta.Meta::new, CustomRecipes::new),
            def(CommandLogger.class, nl.hauntedmc.serverfeatures.features.commandlogger.meta.Meta::new, CommandLogger::new),
            def(Portals.class, nl.hauntedmc.serverfeatures.features.portals.meta.Meta::new, Portals::new),
            def(PlayerLanguage.class, nl.hauntedmc.serverfeatures.features.playerlanguage.meta.Meta::new, PlayerLanguage::new),
            def(DurabilityAlert.class, nl.hauntedmc.serverfeatures.features.durabilityalert.meta.Meta::new, DurabilityAlert::new),
            def(ChatFilter.class, nl.hauntedmc.serverfeatures.features.chatfilter.meta.Meta::new, ChatFilter::new),
            def(VillagerOptimizer.class, nl.hauntedmc.serverfeatures.features.villageroptimizer.meta.Meta::new, VillagerOptimizer::new),
            def(Teleportation.class, nl.hauntedmc.serverfeatures.features.teleportation.meta.Meta::new, Teleportation::new),
            def(VersionRecommender.class, nl.hauntedmc.serverfeatures.features.versionrecommender.meta.Meta::new, VersionRecommender::new),
            def(Capacity.class, nl.hauntedmc.serverfeatures.features.capacity.meta.Meta::new, Capacity::new),
            def(Votifier.class, nl.hauntedmc.serverfeatures.features.votifier.meta.Meta::new, Votifier::new),
            def(ChatLayout.class, nl.hauntedmc.serverfeatures.features.chatlayout.meta.Meta::new, ChatLayout::new),
            def(Nametags.class, nl.hauntedmc.serverfeatures.features.nametags.meta.Meta::new, Nametags::new),
            def(SpawnerToggle.class, nl.hauntedmc.serverfeatures.features.spawnertoggle.meta.Meta::new, SpawnerToggle::new),
            def(PlayerCount.class, nl.hauntedmc.serverfeatures.features.playercount.meta.Meta::new, PlayerCount::new),
            def(Vanish.class, nl.hauntedmc.serverfeatures.features.vanish.meta.Meta::new, Vanish::new),
            def(Parcour.class, nl.hauntedmc.serverfeatures.features.parcour.meta.Meta::new, Parcour::new),
            def(CommandRelay.class, nl.hauntedmc.serverfeatures.features.commandrelay.meta.Meta::new, CommandRelay::new),
            def(WorldEditVisualizer.class, nl.hauntedmc.serverfeatures.features.worldeditvisualizer.meta.Meta::new, WorldEditVisualizer::new),
            def(Economy.class, nl.hauntedmc.serverfeatures.features.economy.meta.Meta::new, Economy::new),
            def(AutoPickup.class, nl.hauntedmc.serverfeatures.features.autopickup.meta.Meta::new, AutoPickup::new),
            def(Restart.class, nl.hauntedmc.serverfeatures.features.restart.meta.Meta::new, Restart::new),
            def(InvTools.class, nl.hauntedmc.serverfeatures.features.invtools.meta.Meta::new, InvTools::new),
            def(LimitSpawners.class, nl.hauntedmc.serverfeatures.features.limitspawners.meta.Meta::new, LimitSpawners::new),
            def(CombatTag.class, nl.hauntedmc.serverfeatures.features.combattag.meta.Meta::new, CombatTag::new),
            def(Lottery.class, nl.hauntedmc.serverfeatures.features.lottery.meta.Meta::new, Lottery::new),
            def(Graveyard.class, nl.hauntedmc.serverfeatures.features.graveyard.meta.Meta::new, Graveyard::new),
            def(CommandScheduler.class, nl.hauntedmc.serverfeatures.features.commandscheduler.meta.Meta::new, CommandScheduler::new),
            def(FairPerks.class, nl.hauntedmc.serverfeatures.features.fairperks.meta.Meta::new, FairPerks::new)
    );
    private static final Map<String, Definition> BY_IMPLEMENTATION = indexByImplementation();

    static {
        if (DEFINITIONS.size() != 64) {
            throw new IllegalStateException("Expected 64 built-in ServerFeatures definitions");
        }
        Set<String> names = new HashSet<>();
        Set<Class<?>> implementations = new HashSet<>();
        for (Definition definition : DEFINITIONS) {
            if (!names.add(definition.registryName().toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalStateException("Duplicate feature name: " + definition.registryName());
            }
            if (!implementations.add(definition.implementationType())) {
                throw new IllegalStateException("Duplicate feature implementation: "
                        + definition.implementationType().getName());
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
            Definition previous = definitions.put(definition.implementationType().getName(), definition);
            if (previous != null) {
                throw new IllegalStateException("Duplicate feature implementation: "
                        + definition.implementationType().getName());
            }
        }
        return Map.copyOf(definitions);
    }

    private static <M extends BaseMeta, F extends BukkitBaseFeature<M>> Definition def(
            Class<F> implementation,
            Supplier<M> metaFactory,
            TypedFeatureFactory<M, F> featureFactory
    ) {
        return new Definition(
                implementation.getSimpleName(),
                implementation,
                metaFactory,
                context -> featureFactory.create(castContext(context))
        );
    }

    @SuppressWarnings("unchecked")
    private static <M extends BaseMeta> FeatureContext<M> castContext(FeatureContext<?> context) {
        return (FeatureContext<M>) Objects.requireNonNull(context, "context");
    }

    @FunctionalInterface
    private interface TypedFeatureFactory<M extends BaseMeta, F extends BukkitBaseFeature<M>> {
        F create(FeatureContext<M> context);
    }

    @FunctionalInterface
    public interface FeatureInstantiator {
        BukkitBaseFeature<?> create(FeatureContext<?> context);
    }

    public record Definition(
            String registryName,
            Class<? extends BukkitBaseFeature<?>> implementationType,
            Supplier<? extends BaseMeta> metaFactory,
            FeatureInstantiator featureInstantiator
    ) {
        public Definition {
            registryName = Objects.requireNonNull(registryName, "registryName");
            implementationType = Objects.requireNonNull(implementationType, "implementationType");
            metaFactory = Objects.requireNonNull(metaFactory, "metaFactory");
            featureInstantiator = Objects.requireNonNull(featureInstantiator, "featureInstantiator");
        }

        public BaseMeta createMeta() {
            return Objects.requireNonNull(metaFactory.get(), "metaFactory returned null");
        }

        public BukkitBaseFeature<?> createFeature(FeatureContext<?> context) {
            BukkitBaseFeature<?> feature = featureInstantiator.create(Objects.requireNonNull(context, "context"));
            return Objects.requireNonNull(feature, "featureInstantiator returned null");
        }
    }
}
