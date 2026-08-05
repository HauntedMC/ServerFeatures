package nl.hauntedmc.serverfeatures.features.combattag;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.combat.CombatTags;
import nl.hauntedmc.serverfeatures.api.feature.stateful.SnapshotState;
import nl.hauntedmc.serverfeatures.api.feature.stateful.StatefulFeature;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.combattag.config.CombatTagSettings;
import nl.hauntedmc.serverfeatures.features.combattag.listener.CombatTagListener;
import nl.hauntedmc.serverfeatures.features.combattag.meta.Meta;
import nl.hauntedmc.serverfeatures.features.combattag.service.CombatTagService;
import nl.hauntedmc.serverfeatures.features.combattag.source.CombatSourceResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class CombatTag extends BukkitBaseFeature<Meta>
        implements StatefulFeature<CombatTag.ReloadSnapshot> {

    public static final String BYPASS_PERMISSION = "serverfeatures.feature.combattag.bypass";

    private CombatTagSettings settings;
    private CombatTagService service;
    private CombatTagListener listener;
    private boolean reloadSnapshotCaptured;

    public CombatTag(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap config = new ConfigMap();
        config.put("enabled", true);

        config.put("tagging.mode", "BOTH");
        config.put("tagging.duration-seconds", 15);
        config.put("tagging.allow-self-combat", false);
        config.put("tagging.worlds.mode", "ALL");
        config.put("tagging.worlds.values", List.of());

        config.put("attribution.link-tamed-pets", true);
        config.put("attribution.projectiles.enabled", true);
        config.put(
                "attribution.projectiles.ignored-types",
                List.of("EGG", "ENDER_PEARL", "SNOWBALL")
        );
        config.put("attribution.link-fishing-hooks", true);
        config.put("attribution.link-primed-tnt", true);
        config.put("attribution.mob-spawn-exclusions", List.of("SPAWNER"));

        config.put("lifecycle.clear-on-player-death", true);
        config.put("lifecycle.clear-when-opponent-dies", true);

        config.put("teleport.prevent-portals", true);
        config.put("teleport.prevent-other-teleports", true);
        config.put(
                "teleport.allowed-causes",
                List.of("PLUGIN", "UNKNOWN", "ENDER_PEARL")
        );
        config.put("teleport.ender-pearl-resets-timer", false);
        config.put("teleport.clear-after-allowed-teleport", false);

        config.put("logout-punishment.enabled", true);
        config.put("logout-punishment.kill-player", true);
        config.put("logout-punishment.broadcast", true);
        config.put("logout-punishment.commands", List.of());

        config.put("display.chat.enter", true);
        config.put("display.chat.exit", true);
        config.put("display.action-bar.enabled", true);
        config.put("display.action-bar.update-interval-ticks", 5);
        config.put("display.action-bar.segments", 20);
        config.put("display.action-bar.filled-symbol", "█");
        config.put("display.action-bar.empty-symbol", "█");

        config.put("feedback.restriction-message-cooldown-millis", 1_000L);
        return config;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add(
                "combattag.enter",
                "&cJe bent nu in combat met &f{opponent}&c."
        );
        messages.add(
                "combattag.exit",
                "&aJe bent niet langer in combat."
        );
        messages.add(
                "combattag.action-bar",
                "&c⚔ Combat &8[&c{filled}&7{empty}&8] &f{seconds}s &8• &7{opponent}"
        );
        messages.add(
                "combattag.portal-blocked",
                "&cJe kunt geen Nether- of End-portaal gebruiken terwijl je in combat bent."
        );
        messages.add(
                "combattag.teleport-blocked",
                "&cJe kunt niet teleporteren terwijl je in combat bent."
        );
        messages.add(
                "combattag.logout-broadcast",
                "&c{player} logde uit tijdens combat met {attacker} en is gestraft."
        );
        return messages;
    }

    @Override
    public void initialize() {
        settings = CombatTagSettings.load(getConfigHandler());
        service = new CombatTagService(this, settings);
        listener = new CombatTagListener(
                settings,
                service,
                new CombatSourceResolver(settings.attribution())
        );

        getLifecycleManager().getListenerManager().registerListener(listener);
        getLifecycleManager().getTaskManager().scheduleRepeatingTask(
                service::tick,
                BukkitTime.ticks(settings.display().actionBar().updateIntervalTicks())
        );
        getLifecycleManager().getTaskManager().scheduleRepeatingTask(
                listener::pruneSpawnExclusions,
                BukkitTime.ticks(200L)
        );

        CombatTags.bootstrap(service);
        getLogger().info(
                "CombatTag loaded in " + settings.tagging().mode()
                        + " mode with a " + settings.tagging().durationSeconds() + " second timer."
        );
    }

    @Override
    public void disable() {
        if (service != null) {
            CombatTags.shutdown(service);
            service.shutdown(reloadSnapshotCaptured);
        }
        if (listener != null) {
            listener.clear();
        }
    }

    @Override
    public Optional<ReloadSnapshot> captureReloadState() {
        reloadSnapshotCaptured = true;
        return service == null
                ? Optional.empty()
                : Optional.of(new ReloadSnapshot(service.snapshotForReload()));
    }

    @Override
    public void restoreReloadState(ReloadSnapshot snapshot) {
        reloadSnapshotCaptured = false;
        if (snapshot != null && service != null) {
            service.restore(snapshot.sessions());
        }
    }

    public CombatTagSettings settings() {
        return settings;
    }

    public CombatTagService service() {
        return service;
    }

    public void sendMessage(CommandSender audience, String key) {
        audience.sendMessage(getLocalizationHandler().getMessage(key).forAudience(audience).build());
    }

    public void sendMessage(
            CommandSender audience,
            String key,
            Map<String, String> placeholders
    ) {
        audience.sendMessage(buildMessage(audience, key, placeholders));
    }

    public void sendActionBar(
            Player player,
            String key,
            Map<String, String> placeholders
    ) {
        player.sendActionBar(buildMessage(player, key, placeholders));
    }

    public void broadcastMessage(String key, Map<String, String> placeholders) {
        for (Player player : getPlugin().getServer().getOnlinePlayers()) {
            player.sendMessage(buildMessage(player, key, placeholders));
        }
        getPlugin().getServer().getConsoleSender().sendMessage(
                buildMessage(getPlugin().getServer().getConsoleSender(), key, placeholders)
        );
    }

    private Component buildMessage(
            CommandSender audience,
            String key,
            Map<String, String> placeholders
    ) {
        var message = getLocalizationHandler().getMessage(key).forAudience(audience);
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            message.with(placeholder.getKey(), placeholder.getValue());
        }
        return message.build();
    }

    public record ReloadSnapshot(Map<UUID, CombatTagService.StoredSession> sessions)
            implements SnapshotState {
        public ReloadSnapshot {
            sessions = sessions == null ? Map.of() : Map.copyOf(sessions);
        }
    }
}
