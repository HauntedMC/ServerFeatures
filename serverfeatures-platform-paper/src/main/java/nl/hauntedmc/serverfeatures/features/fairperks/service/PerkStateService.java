package nl.hauntedmc.serverfeatures.features.fairperks.service;

import nl.hauntedmc.serverfeatures.features.fairperks.FairPerks;
import nl.hauntedmc.serverfeatures.features.fairperks.config.FairPerksSettings;
import nl.hauntedmc.serverfeatures.features.fairperks.migration.LegacyEssentialsStateMigrator;
import nl.hauntedmc.serverfeatures.features.fairperks.model.PerkChangeResult;
import nl.hauntedmc.serverfeatures.features.fairperks.model.PerkType;
import nl.hauntedmc.serverfeatures.features.fairperks.policy.FairPerksPolicy;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class PerkStateService {

    private static final byte TRUE = 1;
    private static final int CURRENT_MIGRATION_VERSION = 2;

    private static final NamespacedKey FLY_ENABLED_KEY = key("fly_enabled");
    private static final NamespacedKey FLY_ACTIVE_KEY = key("fly_active");
    private static final NamespacedKey FLY_OWNED_KEY = key("fly_owned");
    private static final NamespacedKey GOD_ENABLED_KEY = key("god_enabled");
    private static final NamespacedKey GOD_MACRO_KEY = key("god_macro");
    private static final NamespacedKey MIGRATION_VERSION_KEY = key("migration_version");
    private static final NamespacedKey LEGACY_ESSENTIALS_MIGRATED_KEY =
            key("legacy_essentials_migrated");
    private static final NamespacedKey LEGACY_GOD_MACRO_KEY =
            new NamespacedKey("fairperks", "godmacro");

    private final FairPerks feature;
    private final FairPerksSettings settings;
    private final FairPerksPolicy policy;
    private final LegacyEssentialsStateMigrator essentialsMigrator;
    private final Map<UUID, SessionState> states = new HashMap<>();
    private final Set<UUID> fallDamageGrace = new HashSet<>();

    public PerkStateService(
            FairPerks feature,
            FairPerksSettings settings,
            FairPerksPolicy policy,
            LegacyEssentialsStateMigrator essentialsMigrator
    ) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.essentialsMigrator = Objects.requireNonNull(essentialsMigrator, "essentialsMigrator");
    }

    /**
     * Creates the session state and performs the only permission-loss reconciliation:
     * player login. No repeating permission task or permission-provider listener is used.
     */
    public void initialize(Player player) {
        migrateLegacyState(player);
        SessionState state = new SessionState();
        states.put(player.getUniqueId(), state);

        boolean storedFlight = readBoolean(player, FLY_ENABLED_KEY);
        boolean hadStaleFlight = storedFlight
                || readBoolean(player, FLY_OWNED_KEY)
                || (!isNativeFlightMode(player) && (player.getAllowFlight() || player.isFlying()));
        boolean canRestoreFlight = player.hasPermission(FairPerks.FLY_USE_PERMISSION)
                && player.hasPermission(FairPerks.FLY_PERSIST_PERMISSION)
                && settings.flight().persistenceEnabled();

        if (storedFlight && canRestoreFlight) {
            state.flyDesired = true;
            boolean restoreActive = settings.flight().restoreActiveFlight()
                    && (readBoolean(player, FLY_ACTIVE_KEY)
                    || (settings.flight().restoreWhenAirborne() && isAirborne(player)));
            if (policy.allowsEnvironment(player, PerkType.FLY)) {
                applyFlight(player, state, restoreActive, true);
            }
        } else {
            clearFlightPersistence(player);
            if (hadStaleFlight && !isNativeFlightMode(player)) {
                revokeFlight(player, state, true, true);
                feature.sendMessage(player, "fairperks.flight.removed_permission");
            }
        }

        boolean storedGod = readBoolean(player, GOD_ENABLED_KEY);
        boolean canRestoreGod = player.hasPermission(FairPerks.GOD_USE_PERMISSION)
                && player.hasPermission(FairPerks.GOD_PERSIST_PERMISSION)
                && settings.god().persistenceEnabled();
        if (storedGod && canRestoreGod) {
            state.godDesired = true;
        } else {
            remove(player, GOD_ENABLED_KEY);
            if (storedGod) {
                feature.sendMessage(player, "fairperks.god.removed_permission");
            }
        }

        boolean storedMacro = readBoolean(player, GOD_MACRO_KEY);
        if (storedMacro && player.hasPermission(FairPerks.GOD_MACRO_PERMISSION)) {
            state.godMacroEnabled = true;
        } else {
            remove(player, GOD_MACRO_KEY);
        }
    }

    public void initializeIfAbsent(Player player) {
        if (!states.containsKey(player.getUniqueId())) {
            initialize(player);
        }
    }

    public boolean isInitialized(Player player) {
        return states.containsKey(player.getUniqueId());
    }

    public void remove(Player player) {
        SessionState state = states.remove(player.getUniqueId());
        fallDamageGrace.remove(player.getUniqueId());
        if (state == null) {
            return;
        }

        boolean persistFlight = persistFlightChoice(player, state);
        if (persistFlight) {
            writeBoolean(player, FLY_OWNED_KEY, state.flyOwned);
        } else {
            clearFlightPersistence(player);
            revokeFlight(player, state, false, false);
        }

        persistGodChoice(player, state);
        writeBoolean(
                player,
                GOD_MACRO_KEY,
                state.godMacroEnabled && player.hasPermission(FairPerks.GOD_MACRO_PERMISSION)
        );
    }

    public PerkChangeResult toggle(
            Player player,
            PerkType perk,
            boolean bypassActivationGuard
    ) {
        return toggle(player, perk, bypassActivationGuard, false);
    }

    public PerkChangeResult toggle(
            Player player,
            PerkType perk,
            boolean bypassActivationGuard,
            boolean bypassUsePermission
    ) {
        return set(
                player,
                perk,
                !isDesired(player, perk),
                bypassActivationGuard,
                bypassUsePermission
        );
    }

    public PerkChangeResult set(
            Player player,
            PerkType perk,
            boolean enabled,
            boolean bypassActivationGuard
    ) {
        return set(player, perk, enabled, bypassActivationGuard, false);
    }

    public PerkChangeResult set(
            Player player,
            PerkType perk,
            boolean enabled,
            boolean bypassActivationGuard,
            boolean bypassUsePermission
    ) {
        SessionState state = stateFor(player);
        boolean current = desired(state, perk);
        if (current == enabled) {
            return PerkChangeResult.already(enabled);
        }

        if (enabled) {
            String permission = perk == PerkType.FLY
                    ? FairPerks.FLY_USE_PERMISSION
                    : FairPerks.GOD_USE_PERMISSION;
            if (!bypassUsePermission && !player.hasPermission(permission)) {
                return PerkChangeResult.denied(PerkChangeResult.Status.NO_PERMISSION);
            }
            PerkChangeResult.Status decision = policy.canEnable(player, perk, bypassActivationGuard);
            if (decision.denied()) {
                return PerkChangeResult.denied(decision);
            }
        }

        switch (perk) {
            case FLY -> {
                state.flyDesired = enabled;
                if (enabled) {
                    applyFlight(player, state, settings.flight().enableStartsFlying(), false);
                    persistFlightChoice(player, state);
                } else {
                    clearFlightPersistence(player);
                    revokeFlight(player, state, false, true);
                }
            }
            case GOD -> {
                state.godDesired = enabled;
                persistGodChoice(player, state);
                if (enabled) {
                    clearHostileTargets(player);
                }
            }
        }
        return PerkChangeResult.changed(enabled);
    }

    public boolean isDesired(Player player, PerkType perk) {
        SessionState state = states.get(player.getUniqueId());
        return state != null && desired(state, perk);
    }

    public boolean isGodEffective(Player player) {
        SessionState state = states.get(player.getUniqueId());
        return state != null
                && state.godDesired
                && policy.allowsEnvironment(player, PerkType.GOD);
    }

    public boolean isFlightEffective(Player player) {
        SessionState state = states.get(player.getUniqueId());
        return state != null
                && state.flyDesired
                && policy.allowsEnvironment(player, PerkType.FLY)
                && player.isFlying();
    }

    public boolean isRestricted(Player player) {
        return isGodEffective(player) || isFlightEffective(player);
    }

    public String activeRestrictionMessageSuffix(Player player) {
        return isGodEffective(player) ? "god" : "flying";
    }

    public boolean isGodMacroEnabled(Player player) {
        SessionState state = states.get(player.getUniqueId());
        return state != null && state.godMacroEnabled;
    }

    public boolean setGodMacro(Player player, boolean enabled) {
        SessionState state = stateFor(player);
        if (state.godMacroEnabled == enabled) {
            return false;
        }
        state.godMacroEnabled = enabled;
        writeBoolean(player, GOD_MACRO_KEY, enabled);
        return true;
    }

    /**
     * Reconciles world or game-mode policy only. It intentionally does not recheck permissions.
     */
    public void reconcileEnvironment(Player player) {
        SessionState state = states.get(player.getUniqueId());
        if (state == null) {
            return;
        }

        if (state.flyDesired && !isNativeFlightMode(player)) {
            if (policy.allowsEnvironment(player, PerkType.FLY)) {
                if (!player.getAllowFlight()) {
                    applyFlight(player, state, false, false);
                }
            } else if (state.flyOwned || readBoolean(player, FLY_OWNED_KEY)) {
                revokeFlight(player, state, false, true);
            }
        }

        if (state.godDesired && policy.allowsEnvironment(player, PerkType.GOD)) {
            clearHostileTargets(player);
        }
    }

    public void reconcileAfterRespawn(Player player) {
        SessionState state = states.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        if (state.flyDesired && policy.allowsEnvironment(player, PerkType.FLY)) {
            applyFlight(player, state, false, false);
        }
        if (state.godDesired && policy.allowsEnvironment(player, PerkType.GOD)) {
            clearHostileTargets(player);
        }
    }

    public boolean consumeFallDamageGrace(Player player) {
        return fallDamageGrace.remove(player.getUniqueId());
    }

    public void clearFallDamageGrace(Player player) {
        fallDamageGrace.remove(player.getUniqueId());
    }

    public RuntimeView view(Player player) {
        SessionState state = states.get(player.getUniqueId());
        if (state == null) {
            return new RuntimeView(false, false, false, false, false, false, false);
        }
        return new RuntimeView(
                state.flyDesired,
                isFlightEffective(player),
                state.godDesired,
                isGodEffective(player),
                state.godMacroEnabled,
                state.flyOwned || readBoolean(player, FLY_OWNED_KEY),
                fallDamageGrace.contains(player.getUniqueId())
        );
    }

    public Map<UUID, PlayerSnapshot> snapshot() {
        Map<UUID, PlayerSnapshot> snapshot = new HashMap<>();
        for (Map.Entry<UUID, SessionState> entry : states.entrySet()) {
            SessionState state = entry.getValue();
            snapshot.put(entry.getKey(), new PlayerSnapshot(
                    state.flyDesired,
                    state.godDesired,
                    state.godMacroEnabled,
                    state.flyOwned,
                    state.previousAllowFlight,
                    state.previousFlying,
                    fallDamageGrace.contains(entry.getKey())
            ));
        }
        return Map.copyOf(snapshot);
    }

    public void restore(Map<UUID, PlayerSnapshot> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        for (Player player : feature.getPlugin().getServer().getOnlinePlayers()) {
            PlayerSnapshot saved = snapshot.get(player.getUniqueId());
            if (saved == null) {
                continue;
            }
            SessionState state = new SessionState();
            state.flyDesired = saved.flyDesired();
            state.godDesired = saved.godDesired();
            state.godMacroEnabled = saved.godMacroEnabled();
            state.flyOwned = saved.flyOwned();
            state.previousAllowFlight = saved.previousAllowFlight();
            state.previousFlying = saved.previousFlying();
            states.put(player.getUniqueId(), state);
            if (saved.fallDamageGrace()) {
                fallDamageGrace.add(player.getUniqueId());
            }
            if (state.flyDesired && policy.allowsEnvironment(player, PerkType.FLY)) {
                applyFlight(player, state, player.isFlying(), true);
            }
            if (state.godDesired && policy.allowsEnvironment(player, PerkType.GOD)) {
                clearHostileTargets(player);
            }
        }
    }

    public void cleanupForDisable() {
        for (Player player : feature.getPlugin().getServer().getOnlinePlayers()) {
            SessionState state = states.get(player.getUniqueId());
            if (state == null) {
                continue;
            }
            persistFlightChoice(player, state);
            persistGodChoice(player, state);
            writeBoolean(
                    player,
                    GOD_MACRO_KEY,
                    state.godMacroEnabled && player.hasPermission(FairPerks.GOD_MACRO_PERMISSION)
            );
            revokeFlight(player, state, false, true);
        }
        states.clear();
        fallDamageGrace.clear();
    }

    private SessionState stateFor(Player player) {
        initializeIfAbsent(player);
        return states.get(player.getUniqueId());
    }

    private boolean persistFlightChoice(Player player, SessionState state) {
        boolean persistent = state.flyDesired
                && settings.flight().persistenceEnabled()
                && player.hasPermission(FairPerks.FLY_USE_PERMISSION)
                && player.hasPermission(FairPerks.FLY_PERSIST_PERMISSION);
        writeBoolean(player, FLY_ENABLED_KEY, persistent);
        if (persistent) {
            writeBoolean(player, FLY_ACTIVE_KEY, player.isFlying());
        } else {
            remove(player, FLY_ACTIVE_KEY);
        }
        return persistent;
    }

    private void persistGodChoice(Player player, SessionState state) {
        boolean persistent = state.godDesired
                && settings.god().persistenceEnabled()
                && player.hasPermission(FairPerks.GOD_USE_PERMISSION)
                && player.hasPermission(FairPerks.GOD_PERSIST_PERMISSION);
        writeBoolean(player, GOD_ENABLED_KEY, persistent);
    }

    private void applyFlight(
            Player player,
            SessionState state,
            boolean startFlying,
            boolean restoring
    ) {
        if (isNativeFlightMode(player) || !policy.allowsEnvironment(player, PerkType.FLY)) {
            return;
        }
        if (!state.flyOwned) {
            boolean alreadyOwned = readBoolean(player, FLY_OWNED_KEY);
            state.previousAllowFlight = restoring || alreadyOwned ? false : player.getAllowFlight();
            state.previousFlying = restoring || alreadyOwned ? false : player.isFlying();
            state.flyOwned = true;
        }
        player.setAllowFlight(true);
        writeBoolean(player, FLY_OWNED_KEY, true);
        if (startFlying) {
            player.setFlying(true);
        }
    }

    private void revokeFlight(
            Player player,
            SessionState state,
            boolean forceCleanup,
            boolean grantFallGrace
    ) {
        if (isNativeFlightMode(player)) {
            state.flyOwned = false;
            remove(player, FLY_OWNED_KEY);
            return;
        }
        boolean owned = state.flyOwned || readBoolean(player, FLY_OWNED_KEY);
        if (!owned && !forceCleanup) {
            return;
        }
        if (grantFallGrace
                && settings.flight().cancelNextFallDamageOnRevocation()
                && isAirborne(player)) {
            fallDamageGrace.add(player.getUniqueId());
        }

        boolean restoreAllowFlight = owned && !forceCleanup && state.previousAllowFlight;
        boolean restoreFlying = restoreAllowFlight && state.previousFlying;
        if (player.isFlying()) {
            player.setFlying(false);
        }
        player.setAllowFlight(restoreAllowFlight);
        if (restoreFlying) {
            player.setFlying(true);
        }
        state.flyOwned = false;
        state.previousAllowFlight = false;
        state.previousFlying = false;
        remove(player, FLY_OWNED_KEY);
    }

    private void clearHostileTargets(Player player) {
        if (!settings.restrictions().hostileTargeting()
                || player.hasPermission(FairPerks.RESTRICTION_BYPASS_PERMISSION)) {
            return;
        }
        int horizontalRadius = settings.activationGuard().horizontalRadius();
        int verticalRadius = settings.activationGuard().verticalRadius();
        if (horizontalRadius <= 0 || verticalRadius < 0) {
            return;
        }
        player.getNearbyEntities(horizontalRadius, verticalRadius, horizontalRadius).forEach(entity -> {
            if (entity instanceof Mob mob
                    && feature.hostileClassifier().isHostile(mob)
                    && player.equals(mob.getTarget())) {
                mob.setTarget(null);
            }
        });
    }

    private void migrateLegacyState(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        Integer storedVersion = safeGet(data, MIGRATION_VERSION_KEY, PersistentDataType.INTEGER);
        int version = storedVersion == null ? 0 : storedVersion;

        if (version < 1 && settings.migration().migrateLegacyGodMacro() && !data.has(GOD_MACRO_KEY)) {
            Boolean legacyMacro = readLegacyMacro(data);
            if (legacyMacro != null) {
                writeBoolean(player, GOD_MACRO_KEY, legacyMacro);
            }
        }

        migrateLegacyEssentialsState(player, data);

        if (version < 1
                && settings.migration().adoptExistingFlightForPersistentUsers()
                && !data.has(FLY_ENABLED_KEY)
                && player.hasPermission(FairPerks.FLY_USE_PERMISSION)
                && player.hasPermission(FairPerks.FLY_PERSIST_PERMISSION)
                && !isNativeFlightMode(player)
                && player.getAllowFlight()) {
            writeBoolean(player, FLY_ENABLED_KEY, true);
            writeBoolean(player, FLY_ACTIVE_KEY, player.isFlying() || isAirborne(player));
            writeBoolean(player, FLY_OWNED_KEY, true);
        }

        if (version < CURRENT_MIGRATION_VERSION) {
            data.set(MIGRATION_VERSION_KEY, PersistentDataType.INTEGER, CURRENT_MIGRATION_VERSION);
        }
    }

    private void migrateLegacyEssentialsState(Player player, PersistentDataContainer data) {
        if (!settings.migration().clearLegacyEssentialsState()
                || readBoolean(player, LEGACY_ESSENTIALS_MIGRATED_KEY)) {
            return;
        }

        LegacyEssentialsStateMigrator.MigrationResult result = essentialsMigrator.migrate(player);
        if (!result.completed()) {
            return;
        }
        if (result.flyEnabled()
                && settings.migration().adoptExistingFlightForPersistentUsers()
                && !data.has(FLY_ENABLED_KEY)) {
            writeBoolean(player, FLY_ENABLED_KEY, true);
            writeBoolean(player, FLY_ACTIVE_KEY, player.isFlying() || isAirborne(player));
        }
        if (result.godEnabled()
                && settings.migration().adoptExistingGodForPersistentUsers()
                && !data.has(GOD_ENABLED_KEY)) {
            writeBoolean(player, GOD_ENABLED_KEY, true);
        }
        writeBoolean(player, LEGACY_ESSENTIALS_MIGRATED_KEY, true);
    }

    private static Boolean readLegacyMacro(PersistentDataContainer data) {
        Byte byteValue = safeGet(data, LEGACY_GOD_MACRO_KEY, PersistentDataType.BYTE);
        if (byteValue != null) {
            return byteValue == TRUE;
        }
        String stringValue = safeGet(data, LEGACY_GOD_MACRO_KEY, PersistentDataType.STRING);
        return stringValue == null ? null : Boolean.parseBoolean(stringValue);
    }

    private static boolean desired(SessionState state, PerkType perk) {
        return perk == PerkType.FLY ? state.flyDesired : state.godDesired;
    }

    private static boolean isNativeFlightMode(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
    }

    private static boolean isAirborne(Player player) {
        return !((Entity) player).isOnGround()
                && !player.isGliding()
                && !player.isSwimming()
                && player.getVehicle() == null;
    }

    private static NamespacedKey key(String suffix) {
        return new NamespacedKey("serverfeatures", "fairperks_" + suffix);
    }

    private static boolean readBoolean(Player player, NamespacedKey key) {
        Byte value = safeGet(player.getPersistentDataContainer(), key, PersistentDataType.BYTE);
        return value != null && value == TRUE;
    }

    private static void writeBoolean(Player player, NamespacedKey key, boolean value) {
        if (!value) {
            remove(player, key);
            return;
        }
        PersistentDataContainer data = player.getPersistentDataContainer();
        data.remove(key);
        data.set(key, PersistentDataType.BYTE, TRUE);
    }

    private static void clearFlightPersistence(Player player) {
        remove(player, FLY_ENABLED_KEY);
        remove(player, FLY_ACTIVE_KEY);
        remove(player, FLY_OWNED_KEY);
    }

    private static void remove(Player player, NamespacedKey key) {
        player.getPersistentDataContainer().remove(key);
    }

    private static <P, C> C safeGet(
            PersistentDataContainer data,
            NamespacedKey key,
            PersistentDataType<P, C> type
    ) {
        try {
            return data.get(key, type);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static final class SessionState {
        private boolean flyDesired;
        private boolean godDesired;
        private boolean godMacroEnabled;
        private boolean flyOwned;
        private boolean previousAllowFlight;
        private boolean previousFlying;
    }

    public record RuntimeView(
            boolean flyDesired,
            boolean flyEffective,
            boolean godDesired,
            boolean godEffective,
            boolean godMacroEnabled,
            boolean flightOwned,
            boolean fallDamageGrace
    ) {
    }

    public record PlayerSnapshot(
            boolean flyDesired,
            boolean godDesired,
            boolean godMacroEnabled,
            boolean flyOwned,
            boolean previousAllowFlight,
            boolean previousFlying,
            boolean fallDamageGrace
    ) {
    }
}
