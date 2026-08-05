package nl.hauntedmc.serverfeatures.features.fairperks;

import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.api.feature.stateful.SnapshotState;
import nl.hauntedmc.serverfeatures.api.feature.stateful.StatefulFeature;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.fairperks.command.FairPerksCommand;
import nl.hauntedmc.serverfeatures.features.fairperks.command.GodMacroCommand;
import nl.hauntedmc.serverfeatures.features.fairperks.command.PerkCommand;
import nl.hauntedmc.serverfeatures.features.fairperks.config.FairPerksSettings;
import nl.hauntedmc.serverfeatures.features.fairperks.listener.GodMacroListener;
import nl.hauntedmc.serverfeatures.features.fairperks.listener.InteractionRestrictionListener;
import nl.hauntedmc.serverfeatures.features.fairperks.listener.PlayerLifecycleListener;
import nl.hauntedmc.serverfeatures.features.fairperks.listener.ProtectionListener;
import nl.hauntedmc.serverfeatures.features.fairperks.meta.Meta;
import nl.hauntedmc.serverfeatures.features.fairperks.migration.LegacyEssentialsStateMigrator;
import nl.hauntedmc.serverfeatures.features.fairperks.model.PerkType;
import nl.hauntedmc.serverfeatures.features.fairperks.policy.CombatStatusProvider;
import nl.hauntedmc.serverfeatures.features.fairperks.policy.FairPerksPolicy;
import nl.hauntedmc.serverfeatures.features.fairperks.policy.HostileEntityClassifier;
import nl.hauntedmc.serverfeatures.features.fairperks.service.PerkStateService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class FairPerks extends BukkitBaseFeature<Meta>
        implements StatefulFeature<FairPerks.ReloadSnapshot> {

    public static final String FLY_USE_PERMISSION = "serverfeatures.feature.fairperks.fly.use";
    public static final String FLY_OTHERS_PERMISSION = "serverfeatures.feature.fairperks.fly.others";
    public static final String FLY_PERSIST_PERMISSION = "serverfeatures.feature.fairperks.fly.persist";
    public static final String FLY_BYPASS_PERMISSION = "serverfeatures.feature.fairperks.fly.bypass-activation";
    public static final String GOD_USE_PERMISSION = "serverfeatures.feature.fairperks.god.use";
    public static final String GOD_OTHERS_PERMISSION = "serverfeatures.feature.fairperks.god.others";
    public static final String GOD_PERSIST_PERMISSION = "serverfeatures.feature.fairperks.god.persist";
    public static final String GOD_BYPASS_PERMISSION = "serverfeatures.feature.fairperks.god.bypass-activation";
    public static final String GOD_MACRO_PERMISSION = "serverfeatures.feature.fairperks.godmacro.use";
    public static final String RESTRICTION_BYPASS_PERMISSION =
            "serverfeatures.feature.fairperks.restrictions.bypass";
    public static final String INSPECT_PERMISSION = "serverfeatures.feature.fairperks.admin.inspect";

    private final Map<UUID, Long> actionBarFeedback = new HashMap<>();
    private FairPerksSettings settings;
    private HostileEntityClassifier hostileClassifier;
    private FairPerksPolicy policy;
    private PerkStateService stateService;
    private boolean reloadSnapshotCaptured;

    public FairPerks(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap config = new ConfigMap();
        config.put("enabled", false);
        config.put("commands.fly-aliases", List.of());
        config.put("commands.god-aliases", List.of());
        config.put("commands.godmacro-aliases", List.of());

        config.put("flight.enable-starts-flying", true);
        config.put("flight.allowed-game-modes", List.of("SURVIVAL", "ADVENTURE"));
        config.put("flight.worlds.mode", "BLACKLIST");
        config.put("flight.worlds.values", List.of());
        config.put("flight.persistence.enabled", true);
        config.put("flight.persistence.restore-active-flight", true);
        config.put("flight.persistence.restore-when-airborne", true);
        config.put("flight.revocation.cancel-next-fall-damage", true);

        config.put("god.allowed-game-modes", List.of("SURVIVAL", "ADVENTURE", "CREATIVE", "SPECTATOR"));
        config.put("god.worlds.mode", "BLACKLIST");
        config.put("god.worlds.values", List.of());
        config.put("god.persistence.enabled", true);
        config.put("god.damage.protect-void", false);

        config.put("activation-guard.combat.enabled", true);
        config.put("activation-guard.combat.allow-when-unavailable", true);
        config.put("activation-guard.hostile-nearby.enabled", true);
        config.put("activation-guard.hostile-nearby.horizontal-radius", 16);
        config.put("activation-guard.hostile-nearby.vertical-radius", 16);

        config.put("restrictions.pvp", true);
        config.put("restrictions.hostile-melee", true);
        config.put("restrictions.hostile-projectiles", true);
        config.put("restrictions.hostile-targeting", true);
        config.put("restrictions.exploding-beds", true);
        config.put("restrictions.exploding-anchors", true);
        config.put("restrictions.end-crystals", true);
        config.put("restrictions.tnt-prime", true);
        config.put("restrictions.tnt-ignite", true);
        config.put("restrictions.creeper-ignite", true);
        config.put("restrictions.lava-near-hostiles", true);
        config.put("restrictions.block-ignite-near-hostiles", true);
        config.put("restrictions.nearby-radii.ignite", 5);
        config.put("restrictions.nearby-radii.lava", 5);
        config.put("restrictions.nearby-radii.tnt", 10);
        config.put("restrictions.block-ignite-causes", List.of("FLINT_AND_STEEL", "FIREBALL"));

        config.put("hostiles.include", List.of());
        config.put("hostiles.exclude", List.of());
        config.put("hostiles.spawner-mobs-exempt", true);
        config.put("hostiles.mark-spawner-mobs", false);

        config.put("god-macro.enabled", true);
        config.put("god-macro.interval-millis", 350L);
        config.put("feedback.actionbar-cooldown-millis", 1_000L);
        config.put("migration.migrate-legacy-godmacro", true);
        config.put("migration.clear-legacy-essentials-state", true);
        config.put("migration.adopt-existing-flight-for-persistent-users", true);
        config.put("migration.adopt-existing-god-for-persistent-users", true);
        return config;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("fairperks.player_only", "&cDit commando kan alleen door een speler worden gebruikt.");
        messages.add("fairperks.player_not_found", "&cSpeler {target} is niet online.");
        messages.add("fairperks.no_permission", "&cJe hebt hier geen toestemming voor.");
        messages.add("fairperks.denied.combat", "&cJe kunt deze perk niet inschakelen terwijl je in combat bent.");
        messages.add("fairperks.denied.hostile", "&cJe kunt deze perk niet inschakelen terwijl vijandige mobs in de buurt zijn.");
        messages.add("fairperks.denied.world", "&cDeze perk is in deze wereld niet beschikbaar.");
        messages.add("fairperks.denied.game_mode", "&cDeze perk is in je huidige gamemode niet beschikbaar.");

        messages.add("fairperks.fly.enabled", "&aVliegen is ingeschakeld.");
        messages.add("fairperks.fly.disabled", "&7Vliegen is uitgeschakeld.");
        messages.add("fairperks.fly.already_enabled", "&eVliegen was al ingeschakeld.");
        messages.add("fairperks.fly.already_disabled", "&eVliegen was al uitgeschakeld.");
        messages.add("fairperks.fly.status_enabled", "&aVliegen staat ingeschakeld.");
        messages.add("fairperks.fly.status_disabled", "&7Vliegen staat uitgeschakeld.");
        messages.add("fairperks.fly.enabled_other", "&aVliegen is voor {target} ingeschakeld.");
        messages.add("fairperks.fly.disabled_other", "&7Vliegen is voor {target} uitgeschakeld.");
        messages.add("fairperks.fly.already_enabled_other", "&eVliegen was voor {target} al ingeschakeld.");
        messages.add("fairperks.fly.already_disabled_other", "&eVliegen was voor {target} al uitgeschakeld.");
        messages.add("fairperks.fly.status_enabled_other", "&aVliegen staat voor {target} ingeschakeld.");
        messages.add("fairperks.fly.status_disabled_other", "&7Vliegen staat voor {target} uitgeschakeld.");
        messages.add("fairperks.fly.target_enabled", "&a{actor} heeft vliegen voor je ingeschakeld.");
        messages.add("fairperks.fly.target_disabled", "&7{actor} heeft vliegen voor je uitgeschakeld.");
        messages.add("fairperks.flight.removed_permission", "&cVliegen is verwijderd omdat je bij het inloggen niet de vereiste permissies had.");

        messages.add("fairperks.god.enabled", "&aGod mode is ingeschakeld.");
        messages.add("fairperks.god.disabled", "&7God mode is uitgeschakeld.");
        messages.add("fairperks.god.already_enabled", "&eGod mode was al ingeschakeld.");
        messages.add("fairperks.god.already_disabled", "&eGod mode was al uitgeschakeld.");
        messages.add("fairperks.god.status_enabled", "&aGod mode staat ingeschakeld.");
        messages.add("fairperks.god.status_disabled", "&7God mode staat uitgeschakeld.");
        messages.add("fairperks.god.enabled_other", "&aGod mode is voor {target} ingeschakeld.");
        messages.add("fairperks.god.disabled_other", "&7God mode is voor {target} uitgeschakeld.");
        messages.add("fairperks.god.already_enabled_other", "&eGod mode was voor {target} al ingeschakeld.");
        messages.add("fairperks.god.already_disabled_other", "&eGod mode was voor {target} al uitgeschakeld.");
        messages.add("fairperks.god.status_enabled_other", "&aGod mode staat voor {target} ingeschakeld.");
        messages.add("fairperks.god.status_disabled_other", "&7God mode staat voor {target} uitgeschakeld.");
        messages.add("fairperks.god.target_enabled", "&a{actor} heeft god mode voor je ingeschakeld.");
        messages.add("fairperks.god.target_disabled", "&7{actor} heeft god mode voor je uitgeschakeld.");
        messages.add("fairperks.god.removed_permission", "&cGod mode is verwijderd omdat je bij het inloggen niet de vereiste permissies had.");

        messages.add("fairperks.godmacro.enabled", "&aDe dubbele-shift god macro is ingeschakeld.");
        messages.add("fairperks.godmacro.disabled", "&7De dubbele-shift god macro is uitgeschakeld.");
        messages.add("fairperks.godmacro.already_enabled", "&eDe god macro was al ingeschakeld.");
        messages.add("fairperks.godmacro.already_disabled", "&eDe god macro was al uitgeschakeld.");
        messages.add("fairperks.godmacro.status_enabled", "&aDe god macro staat ingeschakeld.");
        messages.add("fairperks.godmacro.status_disabled", "&7De god macro staat uitgeschakeld.");

        messages.add("fairperks.restriction.pvp.god", "&cJe kunt spelers niet aanvallen terwijl god mode actief is.");
        messages.add("fairperks.restriction.pvp.flying", "&cJe kunt spelers niet aanvallen terwijl je vliegt.");
        messages.add("fairperks.restriction.hostile.god", "&cJe kunt vijandige mobs niet aanvallen terwijl god mode actief is.");
        messages.add("fairperks.restriction.hostile.flying", "&cJe kunt vijandige mobs niet aanvallen terwijl je vliegt.");
        messages.add("fairperks.restriction.interaction.god", "&cDeze actie is niet toegestaan terwijl god mode actief is.");
        messages.add("fairperks.restriction.interaction.flying", "&cDeze actie is niet toegestaan terwijl je vliegt.");

        messages.add(
                "fairperks.inspect",
                "&7FairPerks voor &e{target}&7: fly desired=&f{fly_desired}&7, effective=&f{fly_effective}&7, owned=&f{fly_owned}&7; god desired=&f{god_desired}&7, effective=&f{god_effective}&7; macro=&f{macro}&7; fall grace=&f{fall_grace}&7."
        );
        return messages;
    }

    @Override
    public void initialize() {
        settings = FairPerksSettings.load(getConfigHandler());
        hostileClassifier = new HostileEntityClassifier(settings.hostiles());
        policy = new FairPerksPolicy(
                settings,
                hostileClassifier,
                CombatStatusProvider.resolve(this)
        );
        stateService = new PerkStateService(
                this,
                settings,
                policy,
                new LegacyEssentialsStateMigrator(this)
        );

        validateCommandOwnership();
        registerRequiredCommand(new PerkCommand(this, PerkType.FLY));
        registerRequiredCommand(new PerkCommand(this, PerkType.GOD));
        if (settings.godMacro().enabled()) {
            registerRequiredCommand(new GodMacroCommand(this));
            getLifecycleManager().getListenerManager().registerListener(new GodMacroListener(this));
        }
        registerRequiredCommand(new FairPerksCommand(this));

        getLifecycleManager().getListenerManager().registerListener(new PlayerLifecycleListener(this));
        getLifecycleManager().getListenerManager().registerListener(new ProtectionListener(this));
        getLifecycleManager().getListenerManager().registerListener(new InteractionRestrictionListener(this));
        getLifecycleManager().getTaskManager().scheduleOneTimeTask(this::initializeOnlinePlayers);

        getLogger().info(
                "FairPerks loaded with native flight and god-mode ownership; "
                        + "Essentials is consulted only for one-time legacy state migration."
        );
    }

    @Override
    public void disable() {
        if (stateService != null && !reloadSnapshotCaptured) {
            stateService.cleanupForDisable();
        }
        actionBarFeedback.clear();
    }

    @Override
    public Optional<ReloadSnapshot> captureReloadState() {
        reloadSnapshotCaptured = true;
        return stateService == null
                ? Optional.empty()
                : Optional.of(new ReloadSnapshot(stateService.snapshot()));
    }

    @Override
    public void restoreReloadState(ReloadSnapshot snapshot) {
        reloadSnapshotCaptured = false;
        if (snapshot != null && stateService != null) {
            stateService.restore(snapshot.players());
        }
    }

    public FairPerksSettings settings() {
        return settings;
    }

    public HostileEntityClassifier hostileClassifier() {
        return hostileClassifier;
    }

    public FairPerksPolicy policy() {
        return policy;
    }

    public PerkStateService stateService() {
        return stateService;
    }

    public void sendMessage(CommandSender audience, String key) {
        audience.sendMessage(getLocalizationHandler().getMessage(key).forAudience(audience).build());
    }

    public void sendMessage(CommandSender audience, String key, Map<String, String> placeholders) {
        var message = getLocalizationHandler().getMessage(key).forAudience(audience);
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            message.with(placeholder.getKey(), placeholder.getValue());
        }
        audience.sendMessage(message.build());
    }

    public void sendActionBar(Player player, String key) {
        long now = System.nanoTime();
        long previous = actionBarFeedback.getOrDefault(player.getUniqueId(), Long.MIN_VALUE);
        if (previous != Long.MIN_VALUE
                && now - previous < settings.feedback().actionBarCooldownNanos()) {
            return;
        }
        actionBarFeedback.put(player.getUniqueId(), now);
        player.sendActionBar(getLocalizationHandler().getMessage(key).forAudience(player).build());
    }

    public void clearFeedback(Player player) {
        actionBarFeedback.remove(player.getUniqueId());
    }

    private void initializeOnlinePlayers() {
        for (Player player : getPlugin().getServer().getOnlinePlayers()) {
            stateService.initializeIfAbsent(player);
        }
    }

    private void registerRequiredCommand(BrigadierCommand command) {
        var commandManager = getLifecycleManager().getCommandManager();
        commandManager.registerBrigadierCommand(command);
        if (commandManager.getRegisteredBrigadierCommands().get(command.name()) != command) {
            throw new IllegalStateException(
                    "FairPerks could not register required command '/" + command.name() + "'."
            );
        }
    }

    private void validateCommandOwnership() {
        Map<String, String> labels = new LinkedHashMap<>();
        registerCommandLabel(labels, "fly", "fly command");
        registerCommandLabels(labels, settings.commands().flyAliases(), "fly alias");
        registerCommandLabel(labels, "god", "god command");
        registerCommandLabels(labels, settings.commands().godAliases(), "god alias");
        registerCommandLabel(labels, "fairperks", "administration command");
        if (settings.godMacro().enabled()) {
            registerCommandLabel(labels, "godmacro", "god macro command");
            registerCommandLabels(labels, settings.commands().godMacroAliases(), "god macro alias");
        }

        for (Map.Entry<String, String> entry : labels.entrySet()) {
            ensureCommandAvailable(entry.getKey(), entry.getValue());
        }
    }

    private static void registerCommandLabels(
            Map<String, String> labels,
            List<String> configuredLabels,
            String purpose
    ) {
        for (String label : configuredLabels) {
            registerCommandLabel(labels, label, purpose);
        }
    }

    private static void registerCommandLabel(Map<String, String> labels, String label, String purpose) {
        String previous = labels.putIfAbsent(label, purpose);
        if (previous != null) {
            throw new IllegalArgumentException(
                    "FairPerks command label '" + label + "' is configured more than once ("
                            + previous + " and " + purpose + ")."
            );
        }
    }

    private void ensureCommandAvailable(String label, String purpose) {
        if (getPlugin().getBrigadierDispatcher().hasRootLiteral(label)) {
            throw new IllegalStateException(
                    "FairPerks cannot register the " + purpose + " '/" + label
                            + "' because the Brigadier root is already registered."
            );
        }

        Command command = getPlugin().getServer().getCommandMap().getCommand(label);
        if (command == null) {
            return;
        }
        String owner = command instanceof PluginIdentifiableCommand identifiable
                ? identifiable.getPlugin().getName()
                : command.getClass().getName();
        throw new IllegalStateException(
                "FairPerks cannot register the " + purpose + " '/" + label
                        + "' because it is already owned by " + owner
                        + ". Disable the conflicting command before enabling FairPerks."
        );
    }

    public record ReloadSnapshot(Map<UUID, PerkStateService.PlayerSnapshot> players)
            implements SnapshotState {
        public ReloadSnapshot {
            players = players == null ? Map.of() : Map.copyOf(players);
        }
    }
}
