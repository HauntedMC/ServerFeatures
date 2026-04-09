package nl.hauntedmc.serverfeatures.features.votereward;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.cache.CacheDirectory;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.votereward.internal.VoteHandler;
import nl.hauntedmc.serverfeatures.features.votereward.listener.NativeVoteListener;
import nl.hauntedmc.serverfeatures.features.votereward.listener.VoteJoinListener;
import nl.hauntedmc.serverfeatures.features.votereward.listener.VotifierVoteListener;
import nl.hauntedmc.serverfeatures.features.votereward.meta.Meta;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureCacheManager;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.Locale;

public class VoteReward extends BukkitBaseFeature<Meta> {

    private static final String VOTIFIER_FEATURE_NAME = "Votifier";

    private CacheDirectory playerCacheDir;
    private VoteHandler voteHandler;

    public VoteReward(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("vote_source", VoteSource.NATIVE.configValue());
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

        String configuredVoteSource = getConfigHandler().get("vote_source", String.class, VoteSource.NATIVE.configValue());
        ResolvedVoteSource voteSource = resolveVoteSource(configuredVoteSource);
        if (voteSource.invalidConfiguredValue()) {
            getLogger().warning("Unknown VoteReward vote_source \"" + voteSource.configuredValue()
                    + "\"; defaulting to \"" + VoteSource.NATIVE.configValue()
                    + "\". Allowed values: " + VoteSource.allowedValues() + ".");
        }

        registerVoteListener(voteSource.source());

        getLifecycleManager().getListenerManager().registerListener(
                new VoteJoinListener(this)
        );
    }

    private void registerVoteListener(VoteSource voteSource) {
        switch (voteSource) {
            case VOTIFIER -> registerVotifierVoteListener();
            case NATIVE -> registerNativeVoteListener();
        }
    }

    private void registerNativeVoteListener() {
        getLifecycleManager().getListenerManager().registerListener(new NativeVoteListener(this));
        getLogger().info("VoteReward listening for native vote events from ServerFeatures Votifier.");

        String warning = unavailableSourceWarning(VoteSource.NATIVE, isNativeVoteFeatureEnabled(), isExternalVotifierPluginEnabled());
        if (warning != null) {
            getLogger().warning(warning);
        }
    }

    private void registerVotifierVoteListener() {
        getLifecycleManager().getListenerManager().registerListener(new VotifierVoteListener(this));
        getLogger().info("VoteReward listening for Votifier plugin vote events.");

        String warning = unavailableSourceWarning(VoteSource.VOTIFIER, isNativeVoteFeatureEnabled(), isExternalVotifierPluginEnabled());
        if (warning != null) {
            getLogger().warning(warning);
        }
    }

    private boolean isNativeVoteFeatureEnabled() {
        return getPlugin().getConfigHandler().isFeatureEnabled(VOTIFIER_FEATURE_NAME);
    }

    private boolean isExternalVotifierPluginEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled(VOTIFIER_FEATURE_NAME);
    }

    @Override
    public void disable() {
    }

    /**
     * Used by listener to queue/replay votes
     */
    public CacheDirectory getPlayerCacheDir() {
        return playerCacheDir;
    }

    public VoteHandler getVoteHandler() {
        return voteHandler;
    }

    static ResolvedVoteSource resolveVoteSource(String configuredSource) {
        if (configuredSource == null) {
            return new ResolvedVoteSource(VoteSource.NATIVE, false, null);
        }

        String normalized = configuredSource.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return new ResolvedVoteSource(VoteSource.NATIVE, false, "");
        }

        for (VoteSource source : VoteSource.values()) {
            if (source.configValue().equals(normalized)) {
                return new ResolvedVoteSource(source, false, normalized);
            }
        }

        return new ResolvedVoteSource(VoteSource.NATIVE, true, normalized);
    }

    static String unavailableSourceWarning(
            VoteSource voteSource,
            boolean nativeVoteFeatureEnabled,
            boolean externalVotifierPluginEnabled
    ) {
        return switch (voteSource) {
            case NATIVE -> nativeVoteFeatureEnabled
                    ? null
                    : "VoteReward vote_source is \"" + VoteSource.NATIVE.configValue()
                    + "\", but the ServerFeatures Votifier feature is not enabled. "
                    + "VoteReward will not receive native vote events until that feature is enabled.";
            case VOTIFIER -> externalVotifierPluginEnabled
                    ? null
                    : "VoteReward vote_source is \"" + VoteSource.VOTIFIER.configValue()
                    + "\", but the Votifier plugin is not enabled. "
                    + "VoteReward will not receive Votifier events until that plugin is enabled.";
        };
    }

    enum VoteSource {
        NATIVE("native"),
        VOTIFIER("votifier");

        private final String configValue;

        VoteSource(String configValue) {
            this.configValue = configValue;
        }

        String configValue() {
            return configValue;
        }

        static String allowedValues() {
            return NATIVE.configValue + ", " + VOTIFIER.configValue;
        }
    }

    record ResolvedVoteSource(VoteSource source, boolean invalidConfiguredValue, String configuredValue) {
    }
}
