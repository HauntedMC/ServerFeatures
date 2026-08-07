package nl.hauntedmc.serverfeatures.framework.loader;

import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
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
import nl.hauntedmc.serverfeatures.features.van​ish.Vanish;
import nl.hauntedmc.serverfeatures.features.versionrecommender.VersionRecommender;
import nl.hauntedmc.serverfeatures.features.villageroptimizer.VillagerOptimizer;
import nl.hauntedmc.serverfeatures.features.votereward.VoteReward;
import nl.hauntedmc.serverfeatures.features.votifier.Votifier;
import nl.hauntedmc.serverfeatures.features.whitelist.Whitelist;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.WorldEditVisualizer;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Explicit inventory of every built-in ServerFeatures implementation. */
public final class BuiltInFeatures {
    private static final List<Definition> DEFINITIONS = List.of(
            def(AFK.class, nl.hauntedmc.serverfeatures.features.afk.meta.Meta::new),
            def(Skins.class, nl.hauntedmc.serverfeatures.features.skins.meta.Meta::new),
            def(Backup.class, nl.hauntedmc.serverfeatures.features.backup.meta.Meta::new),
            def(Titles.class, nl.hauntedmc.serverfeatures.features.titles.meta.Meta::new),
            def(Tablist.class, nl.hauntedmc.serverfeatures.features.tablist.meta.Meta::new),
            def(ChatLog.class, nl.hauntedmc.serverfeatures.features.chatlog.meta.Meta::new),
            def(Bossbars.class, nl.hauntedmc.serverfeatures.features.bossbar.meta.Meta::new),
            def(Glow.class, nl.hauntedmc.serverfeatures.features.glow.meta.Meta::new),
            def(Sanitize.class, nl.hauntedmc.serverfeatures.features.sanitize.meta.Meta::new),
            def(Balloons.class, nl.hauntedmc.serverfeatures.features.balloons.meta.Meta::new),
            def(ItemEdit.class, nl.hauntedmc.serverfeatures.features.itemedit.meta.Meta::new),
            def(Whitelist.class, nl.hauntedmc.serverfeatures.features.whitelist.meta.Meta::new),
            def(ChatTools.class, nl.hauntedmc.serverfeatures.features.chattools.meta.Meta::new),
            def(StaffChat.class, nl.hauntedmc.serverfeatures.features.staffchat.meta.Meta::new),
            def(AutoLapis.class, nl.hauntedmc.serverfeatures.features.autolapis.meta.Meta::new),
            def(DeepHaste.class, nl.hauntedmc.serverfeatures.features.deephaste.meta.Meta::new),
            def(JoinItems.class, nl.hauntedmc.serverfeatures.features.joinitems.meta.Meta::new),
            def(Sanctions.class, nl.hauntedmc.serverfeatures.features.sanctions.meta.Meta::new),
            def(Holograms.class, nl.hauntedmc.serverfeatures.features.holograms.meta.Meta::new),
            def(Actionbar.class, nl.hauntedmc.serverfeatures.features.actionbar.meta.Meta::new),
            def(Broadcast.class, nl.hauntedmc.serverfeatures.features.broadcast.meta.Meta::new),
            def(Scoreboard.class, nl.hauntedmc.serverfeatures.features.scoreboard.meta.Meta::new),
            def(InstaSkull.class, nl.hauntedmc.serverfeatures.features.instaskull.meta.Meta::new),
            def(EnderFrame.class, nl.hauntedmc.serverfeatures.features.enderframe.meta.Meta::new),
            def(LiquidTank.class, nl.hauntedmc.serverfeatures.features.liquidtank.meta.Meta::new),
            def(LagMonitor.class, nl.hauntedmc.serverfeatures.features.lagmonitor.meta.Meta::new),
            def(VoteReward.class, nl.hauntedmc.serverfeatures.features.votereward.meta.Meta::new),
            def(RepairNPC.class, nl.hauntedmc.serverfeatures.features.repairnpc.meta.Meta::new),
            def(BetterCoral.class, nl.hauntedmc.serverfeatures.features.bettercoral.meta.Meta::new),
            def(NightVision.class, nl.hauntedmc.serverfeatures.features.nightvision.meta.Meta::new),
            def(BetterDoors.class, nl.hauntedmc.serverfeatures.features.betterdoors.meta.Meta::new),
            def(NotifyLogin.class, nl.hauntedmc.serverfeatures.features.notifylogin.meta.Meta::new),
            def(AntiRaidFarm.class, nl.hauntedmc.serverfeatures.features.antiraidfarm.meta.Meta::new),
            def(SilkSpawners.class, nl.hauntedmc.serverfeatures.features.silkspawners.meta.Meta::new),
            def(Nickname.class, nl.hauntedmc.serverfeatures.features.nickname.meta.Meta::new),
            def(CustomRecipes.class, nl.hauntedmc.serverfeatures.features.customrecipes.meta.Meta::new),
            def(CommandLogger.class, nl.hauntedmc.serverfeatures.features.commandlogger.meta.Meta::new),
            def(Portals.class, nl.hauntedmc.serverfeatures.features.portals.meta.Meta::new),
            def(PlayerLanguage.class, nl.hauntedmc.serverfeatures.features.playerlanguage.meta.Meta::new),
            def(DurabilityAlert.class, nl.hauntedmc.serverfeatures.features.durabilityalert.meta.Meta::new),
            def(ChatFilter.class, nl.hauntedmc.serverfeatures.features.chatfilter.meta.Meta::new),
            def(VillagerOptimizer.class, nl.hauntedmc.serverfeatures.features.villageroptimizer.meta.Meta::new),
            def(Teleportation.class, nl.hauntedmc.serverfeatures.features.teleportation.meta.Meta::new),
            def(VersionRecommender.class, nl.hauntedmc.serverfeatures.features.versionrecommender.meta.Meta::new),
            def(Capacity.class, nl.hauntedmc.serverfeatures.features.capacity.meta.Meta::new),
            def(Votifier.class, nl.hauntedmc.serverfeatures.features.votifier.meta.Meta::new),
            def(ChatLayout.class, nl.hauntedmc.serverfeatures.features.chatlayout.meta.Meta::new),
            def(Nametags.class, nl.hauntedmc.serverfeatures.features.nametags.meta.Meta::new),
            def(SpawnerToggle.class, nl.hauntedmc.serverfeatures.features.spawnertoggle.meta.Meta::new),
            def(PlayerCount.class, nl.hauntedmc.serverfeatures.features.playercount.meta.Meta::new),
            def(Vanish.class, nl.hauntedmc.serverfeatures.features.vanish.meta.Meta::new),
            def(Parcour.class, nl.hauntedmc.serverfeatures.features.parcour.meta.Meta::new),
            def(CommandRelay.class, nl.hauntedmc.serverfeatures.features.commandrelay.meta.Meta::new),
            def(WorldEditVisualizer.class, nl.hauntedmc.serverfeatures.features.worldeditvisualizer.meta.Meta::new),
            def(Economy.class, nl.hauntedmc.serverfeatures.features.economy.meta.Meta::new),
            def(AutoPickup.class, nl.hauntedmc.serverfeatures.features.autopickup.meta.Meta::new),
            def(Restart.class, nl.hauntedmc.serverfeatures.features.restart.meta.Meta::new),
            def(InvTools.class, nl.hauntedmc.serverfeatures.features.invtools.meta.Meta::new),
            def(LimitSpawners.class, nl.hauntedmc.serverfeatures.features.limitspawners.meta.Meta::new),
            def(CombatTag.class, nl.hauntedmc.serverfeatures.features.combattag.meta.Meta::new),
            def(Lottery.class, nl.hauntedmc.serverfeatures.features.lottery.meta.Meta::new),
            def(Graveyard.class, nl.hauntedmc.serverfeatures.features.graveyard.meta.Meta::new),
            def(CommandScheduler.class, nl.hauntedmc.serverfeatures.features.commandscheduler.meta.Meta::new),
            def(FairPerks.class, nl.hauntedmc.serverfeatures.features.fairperks.meta.Meta::new)
    );

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

    private static Definition def(Class<? extends BukkitBaseFeature<?>> implementation,
                                  Supplier<? extends BaseMeta> metaFactory) {
        return new Definition(implementation.getSimpleName(), implementation, metaFactory);
    }

    public record Definition(
            String registryName,
            Class<? extends BukkitBaseFeature<?>> implementationType,
            Supplier<? extends BaseMeta> metaFactory
    ) {
        public Definition {
            registryName = Objects.requireNonNull(registryName, "registryName");
            implementationType = Objects.requireNonNull(implementationType, "implementationType");
            metaFactory = Objects.requireNonNull(metaFactory, "metaFactory");
        }

        public BaseMeta createMeta() {
            return Objects.requireNonNull(metaFactory.get(), "metaFactory returned null");
        }
    }
}
