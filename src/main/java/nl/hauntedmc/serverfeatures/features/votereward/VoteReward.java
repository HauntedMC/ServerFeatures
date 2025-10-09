package nl.hauntedmc.serverfeatures.features.votereward;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.votereward.internal.VoteHandler;
import nl.hauntedmc.serverfeatures.features.votereward.listener.NativeVoteListener;
import nl.hauntedmc.serverfeatures.features.votereward.listener.VoteJoinListener;
import nl.hauntedmc.serverfeatures.features.votereward.listener.VotifierVoteListener;
import nl.hauntedmc.serverfeatures.features.votereward.meta.Meta;
import nl.hauntedmc.serverfeatures.internal.cache.CacheDirectory;
import nl.hauntedmc.serverfeatures.internal.lifecycle.FeatureCacheManager;
import org.bukkit.Bukkit;

import java.util.List;

public class VoteReward extends BukkitBaseFeature<Meta> {

    private CacheDirectory playerCacheDir;
    private VoteHandler voteHandler;

    public VoteReward(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("vote_whitelist", List.of("SERVERPACTTEST", "TopMinecraftServers", "SERVERPACT.NL", "minecraftkrant.nl", "Minecraft-MP.com"));
        defaults.put("rewards", List.of("eco give {player} 10"));
        defaults.put("join_message_delay", 100);
        defaults.put("rewards_start_delay", 100);
        defaults.put("reward_interval", 20);
        defaults.put("cache_ttl_millis", 24 * 60 * 60 * 1_000L);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap msgs = new MessageMap();
        msgs.add("votereward.vote_received",
                "&8[&bVote&8]&r &7Bedankt voor je vote! Je ontvangt nu je beloningen.");
        msgs.add("votereward.offline_votes_retrieved",
                "&8[&bVote&8]&r &7Er zijn &6{count}&7 offline votes gevonden die nu verwerkt worden. &8Offline votes worden maximaal 24 uur bewaard.");
        msgs.add("votereward.vote_broadcast",
                "&8[&bVote&8]&r &b{player} &7heeft gevote! Gebruik &e/vote &7voor rewards!");
        return msgs;
    }

    @Override
    public void initialize() {
        FeatureCacheManager cacheMgr = getLifecycleManager().getCacheManager();
        this.playerCacheDir = cacheMgr.getCacheDirectory(getFeatureName(), "players");

        this.voteHandler = new VoteHandler(this);

        boolean nativeVotifierAvailable = detectVotifierOnce();

        if (nativeVotifierAvailable) {
            getLifecycleManager().getListenerManager().registerListener(new VotifierVoteListener(this));
        } else {
            getLifecycleManager().getListenerManager().registerListener(new NativeVoteListener(this));
            getLogger().info("Votifier not available or incompatible; using native vote events.");
        }

        getLifecycleManager().getListenerManager().registerListener(
                new VoteJoinListener(this)
        );
    }

    private boolean detectVotifierOnce() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Votifier")) {
            return false;
        }

        try {
            Class.forName("com.vexsoftware.votifier.model.Vote", false, getClass().getClassLoader());
            Class.forName("com.vexsoftware.votifier.model.VotifierEvent", false, getClass().getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void disable() {
    }

    /** Used by listener to queue/replay votes */
    public CacheDirectory getPlayerCacheDir() { return playerCacheDir; }
    public VoteHandler getVoteHandler() { return voteHandler; }
}
