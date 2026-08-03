package nl.hauntedmc.serverfeatures.features.votereward;

import nl.hauntedmc.serverfeatures.api.io.cache.CacheDirectory;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.votereward.internal.VoteHandler;
import nl.hauntedmc.serverfeatures.features.votereward.listener.NativeVoteListener;
import nl.hauntedmc.serverfeatures.features.votereward.listener.VoteJoinListener;
import nl.hauntedmc.serverfeatures.features.votereward.meta.Meta;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureCacheManager;

import java.util.List;

public class VoteReward extends BukkitBaseFeature<Meta> {

    private static final String VOTIFIER_FEATURE_NAME = "Votifier";

    private CacheDirectory playerCacheDir;
    private VoteHandler voteHandler;

    public VoteReward(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("vote_whitelist", List.of(
                "SERVERPACTTEST",
                "TopMinecraftServers",
                "SERVERPACT.NL",
                "minecraftkrant.nl",
                "Minecraft-MP.com"
        ));
        defaults.put("rewards", List.of("eco give {player} 10"));
        defaults.put("join_message_delay", 100);
        defaults.put("rewards_start_delay", 100);
        defaults.put("reward_interval", 20);
        defaults.put("cache_ttl_millis", 24 * 60 * 60 * 1_000L);
        defaults.put("processed_vote_ttl_millis", 8 * 24 * 60 * 60 * 1_000L);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add(
                "votereward.vote_received",
                "&8[&bVote&8]&r &7Bedankt voor je vote! Je ontvangt nu je beloningen."
        );
        messages.add(
                "votereward.offline_votes_retrieved",
                "&8[&bVote&8]&r &7Er zijn &6{count}&7 offline votes gevonden die nu verwerkt worden. "
                        + "&8Offline votes worden maximaal 24 uur bewaard."
        );
        messages.add(
                "votereward.vote_broadcast",
                "&8[&bVote&8]&r &b{player} &7heeft gevote! Gebruik &e/vote &7voor rewards!"
        );
        return messages;
    }

    @Override
    public void initialize() {
        FeatureCacheManager cacheManager = getLifecycleManager().getCacheManager();
        this.playerCacheDir = cacheManager.getCacheDirectory(getFeatureName(), "players");
        this.voteHandler = new VoteHandler(this);

        if (!getPlugin().getConfigHandler().isFeatureEnabled(VOTIFIER_FEATURE_NAME)) {
            throw new IllegalStateException(
                    "VoteReward requires the ServerFeatures Votifier feature to be enabled."
            );
        }

        getLifecycleManager().getListenerManager().registerListener(new NativeVoteListener(this));
        getLifecycleManager().getListenerManager().registerListener(new VoteJoinListener(this));
        getLogger().info("VoteReward listening for tracked ServerFeatures vote events.");
    }

    @Override
    public void disable() {
    }

    public CacheDirectory getPlayerCacheDir() {
        return playerCacheDir;
    }

    public VoteHandler getVoteHandler() {
        return voteHandler;
    }
}
