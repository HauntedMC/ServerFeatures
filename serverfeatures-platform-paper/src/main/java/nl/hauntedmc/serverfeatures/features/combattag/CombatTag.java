package nl.hauntedmc.serverfeatures.features.combattag;

import nl.hauntedmc.serverfeatures.api.combat.CombatTagResult;
import nl.hauntedmc.serverfeatures.api.combat.CombatTags;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.api.feature.stateful.SnapshotState;
import nl.hauntedmc.serverfeatures.api.feature.stateful.StatefulFeature;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.api.ui.hud.actionbar.ActionBars;
import nl.hauntedmc.serverfeatures.api.ui.hud.actionbar.PauseMode;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.combattag.command.CombatTagCommand;
import nl.hauntedmc.serverfeatures.features.combattag.config.CombatTagSettings;
import nl.hauntedmc.serverfeatures.features.combattag.event.CombatTagAppliedEvent;
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
import java.util.logging.Level;

public final class CombatTag extends BukkitBaseFeature<Meta>
        implements StatefulFeature<CombatTag.ReloadSnapshot> {

    public static final String BYPASS_PERMISSION = "serverfeatures.feature.combattag.bypass";
    public static final String STATUS_PERMISSION = "serverfeatures.feature.combattag.command.status";
    public static final String STATUS_OTHERS_PERMISSION =
            "serverfeatures.feature.combattag.command.status.others";
    public static final String UNTAG_PERMISSION = "serverfeatures.feature.combattag.command.untag";

    private static final String ACTION_BAR_OWNER = "serverfeatures:combattag";

    private CombatTagSettings settings;
    private CombatTagService service;
    private CombatTagListener listener;

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
        config.put("logout-punishment.punish-kicked-players", false);
        config.put("logout-punishment.commands", List.of());

        config.put("display.chat.enter", true);
        config.put("display.chat.exit", true);
        config.put("display.action-bar.enabled", true);
        config.put("display.action-bar.update-interval-ticks", 2);
        config.put("display.action-bar.segments", 15);
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
        messages.add(
                "combattag.logout-broadcast-unknown",
                "&c{player} logde uit tijdens combat en is gestraft."
        );
        messages.add(
                "combattag.command.player-only",
                "&cDit commando kan alleen door een speler worden gebruikt."
        );
        messages.add(
                "combattag.command.player-not-found",
                "&cSpeler {target} is niet online."
        );
        messages.add(
                "combattag.command.status.tagged",
                "&cJe bent nog {seconds}s in combat met {opponent} &7({reason})."
        );
        messages.add(
                "combattag.command.status.not-tagged",
                "&aJe bent niet in combat."
        );
        messages.add(
                "combattag.command.status-other.tagged",
                "&c{target} is nog {seconds}s in combat met {opponent} &7({reason})."
        );
        messages.add(
                "combattag.command.status-other.not-tagged",
                "&a{target} is niet in combat."
        );
        messages.add(
                "combattag.command.untagged",
                "&aDe combat tag van {target} is verwijderd."
        );
        messages.add(
                "combattag.command.already-untagged",
                "&e{target} was niet in combat."
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
                new CombatSourceResolver(settings.attribution()),
                getLifecycleManager().getTaskManager()
        );

        getLifecycleManager().getListenerManager().registerListener(listener);
        registerRequiredCommand(new CombatTagCommand(this));
        getLifecycleManager().getTaskManager().scheduleRepeatingTask(
                service::tick,
                BukkitTime.ticks(settings.display().actionBar().updateIntervalTicks())
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
            service.shutdown();
        }
        if (listener != null) {
            listener.clear();
        }
    }

    @Override
    public Optional<ReloadSnapshot> captureReloadState() {
        return service == null
                ? Optional.empty()
                : Optional.of(new ReloadSnapshot(service.snapshotForReload()));
    }

    @Override
    public void restoreReloadState(ReloadSnapshot snapshot) {
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

    public void publishAppliedTag(Player player, CombatTagResult result) {
        try {
            getPlugin().getServer().getPluginManager().callEvent(
                    new CombatTagAppliedEvent(player, result)
            );
        } catch (RuntimeException exception) {
            reportFailure("Could not publish CombatTag applied event for " + player.getName(), exception);
        }
    }

    public void sendMessage(CommandSender audience, String key) {
        sendMessage(audience, key, Map.of());
    }

    public void sendMessage(
            CommandSender audience,
            String key,
            Map<String, String> placeholders
    ) {
        try {
            audience.sendMessage(buildMessage(audience, key, placeholders));
        } catch (RuntimeException exception) {
            reportFailure("Could not send CombatTag message '" + key + "'", exception);
        }
    }

    public void sendActionBar(
            Player player,
            String key,
            Map<String, String> placeholders
    ) {
        try {
            ActionBars.service().sendOverride(
                    player,
                    buildMessage(player, key, placeholders),
                    2,
                    PauseMode.PAUSE_CYCLE,
                    ACTION_BAR_OWNER
            );
        } catch (RuntimeException exception) {
            reportFailure("Could not send CombatTag action bar to " + player.getName(), exception);
        }
    }

    public void clearActionBar(Player player) {
        try {
            ActionBars.service().clearOverride(player, ACTION_BAR_OWNER);
        } catch (RuntimeException exception) {
            reportFailure("Could not clear CombatTag action bar for " + player.getName(), exception);
        }
    }

    public void broadcastMessage(String key, Map<String, String> placeholders) {
        for (Player player : getPlugin().getServer().getOnlinePlayers()) {
            sendMessage(player, key, placeholders);
        }
        sendMessage(getPlugin().getServer().getConsoleSender(), key, placeholders);
    }

    public void reportFailure(String message, Throwable throwable) {
        if (throwable == null) {
            getLogger().warning(message);
        } else {
            getLogger().log(Level.WARNING, message, throwable);
        }
    }

    private net.kyori.adventure.text.Component buildMessage(
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

    private void registerRequiredCommand(BrigadierCommand command) {
        var commandManager = getLifecycleManager().getCommandManager();
        commandManager.registerBrigadierCommand(command);
        if (commandManager.getRegisteredBrigadierCommands().get(command.name()) != command) {
            throw new IllegalStateException(
                    "CombatTag could not register required command '/" + command.name() + "'."
            );
        }
    }

    public record ReloadSnapshot(Map<UUID, CombatTagService.StoredSession> sessions)
            implements SnapshotState {
        public ReloadSnapshot {
            sessions = sessions == null ? Map.of() : Map.copyOf(sessions);
        }
    }
}
