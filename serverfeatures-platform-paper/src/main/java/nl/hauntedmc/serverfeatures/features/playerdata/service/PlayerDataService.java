package nl.hauntedmc.serverfeatures.features.playerdata.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.features.playerdata.PlayerData;
import nl.hauntedmc.serverfeatures.features.playerdata.inspect.NbtPlayerDataReader;
import nl.hauntedmc.serverfeatures.features.playerdata.inspect.PersistentDataInspector;
import nl.hauntedmc.serverfeatures.features.playerdata.model.PlayerDataEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PlayerDataService {

    public enum View {
        OVERVIEW,
        RUNTIME,
        SETTINGS,
        PDC,
        NBT
    }

    private final PlayerData feature;
    private final PersistentDataInspector pdcInspector;
    private final NbtPlayerDataReader offlineReader;
    private final int maxEntries;
    private final int maxValueLength;
    private final AtomicBoolean active = new AtomicBoolean(true);

    public PlayerDataService(
            PlayerData feature,
            int maxEntries,
            int maxValueLength,
            int maxCompressedBytes,
            int maxDecompressedBytes
    ) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.maxEntries = maxEntries;
        this.maxValueLength = maxValueLength;
        this.pdcInspector = new PersistentDataInspector();
        this.offlineReader = new NbtPlayerDataReader(
                feature.getPlugin().getServer().getLevelDirectory(),
                maxCompressedBytes,
                maxDecompressedBytes
        );
    }

    public void inspect(CommandSender sender, String requestedTarget, View view, String nbtPath) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(view, "view");
        if (!active.get()) {
            return;
        }

        String target = requestedTarget == null ? "" : requestedTarget.trim();
        if (target.isEmpty()) {
            feature.send(sender, "playerdata.usage");
            return;
        }

        Player online = findOnline(target);
        if (online != null) {
            inspectOnline(sender, online, view);
            return;
        }

        UUID preferredPlayerId = preferredOfflineId(target);
        feature.send(sender, "playerdata.loading", "player", target);
        try {
            feature.getLifecycleManager().getTaskManager().supplyAsync(() -> {
                try {
                    Optional<NbtPlayerDataReader.ResolvedPlayerData> resolved =
                            offlineReader.resolve(target, preferredPlayerId);
                    if (resolved.isEmpty()) {
                        return OfflineResult.notFound();
                    }
                    NbtPlayerDataReader.ResolvedPlayerData player = resolved.get();
                    if (view == View.RUNTIME) {
                        return OfflineResult.runtimeUnavailable(player);
                    }
                    NbtPlayerDataReader.Inspection inspection = switch (view) {
                        case OVERVIEW -> offlineReader.inspectOverview(player, maxValueLength);
                        case SETTINGS -> offlineReader.inspectSettings(player, maxEntries, maxValueLength);
                        case PDC -> offlineReader.inspectPdc(player, maxEntries, maxValueLength);
                        case NBT -> offlineReader.inspectNbt(player, nbtPath, maxEntries, maxValueLength);
                        case RUNTIME -> throw new IllegalStateException("Runtime handled above");
                    };
                    return OfflineResult.inspection(inspection);
                } catch (IOException exception) {
                    throw new CompletionException(exception);
                }
            }).whenComplete((result, failure) -> scheduleMain(() ->
                    completeOffline(sender, target, view, result, failure)
            ));
        } catch (RuntimeException exception) {
            feature.getLogger().warning("Could not schedule PlayerData inspection: " + exception.getMessage());
            feature.send(sender, "playerdata.read_failed", "player", target);
        }
    }

    public void close() {
        active.set(false);
    }

    private void inspectOnline(CommandSender sender, Player player, View view) {
        switch (view) {
            case OVERVIEW -> sendOnlineOverview(sender, player);
            case RUNTIME -> sendOnlineRuntime(sender, player);
            case SETTINGS -> sendPdc(sender, player, true);
            case PDC -> sendPdc(sender, player, false);
            case NBT -> feature.send(
                    sender,
                    "playerdata.online_nbt",
                    "player",
                    player.getName()
            );
        }
    }

    private void sendOnlineOverview(CommandSender sender, Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        List<PlayerDataEntry> entries = List.of(
                entry("name", "string", player.getName()),
                entry("uuid", "uuid", player.getUniqueId()),
                entry("source", "state", "live player"),
                entry("world", "key", player.getWorld().getKey()),
                entry("game-mode", "enum", player.getGameMode()),
                entry("pdc-keys", "count", pdc.getKeys().size()),
                entry(
                        "serverfeatures-settings",
                        "count",
                        pdcInspector.count(pdc, PersistentDataInspector::isServerFeaturesKey)
                )
        );
        sendInspection(sender, player.getName(), player.getUniqueId(), "overview", "live", entries, entries.size());
    }

    private void sendOnlineRuntime(CommandSender sender, Player player) {
        Location location = player.getLocation();
        List<PlayerDataEntry> entries = new ArrayList<>();
        entries.add(entry("world", "key", player.getWorld().getKey()));
        entries.add(entry("x", "double", round(location.getX())));
        entries.add(entry("y", "double", round(location.getY())));
        entries.add(entry("z", "double", round(location.getZ())));
        entries.add(entry("yaw", "float", round(location.getYaw())));
        entries.add(entry("pitch", "float", round(location.getPitch())));
        entries.add(entry("game-mode", "enum", player.getGameMode()));
        entries.add(entry("health", "double", round(player.getHealth())));
        entries.add(entry("food", "int", player.getFoodLevel()));
        entries.add(entry("saturation", "float", round(player.getSaturation())));
        entries.add(entry("exhaustion", "float", round(player.getExhaustion())));
        entries.add(entry("level", "int", player.getLevel()));
        entries.add(entry("exp-progress", "float", round(player.getExp())));
        entries.add(entry("total-exp", "int", player.getTotalExperience()));
        entries.add(entry("allow-flight", "boolean", player.getAllowFlight()));
        entries.add(entry("flying", "boolean", player.isFlying()));
        entries.add(entry("invulnerable", "boolean", player.isInvulnerable()));
        entries.add(entry("sleeping", "boolean", player.isSleeping()));
        entries.add(entry("time-since-rest", "ticks", player.getStatistic(Statistic.TIME_SINCE_REST)));
        entries.add(entry("fire-ticks", "ticks", player.getFireTicks()));
        entries.add(entry("air", "ticks", player.getRemainingAir() + "/" + player.getMaximumAir()));
        entries.add(entry("fall-distance", "float", round(player.getFallDistance())));
        sendInspection(
                sender,
                player.getName(),
                player.getUniqueId(),
                "runtime",
                "live",
                List.copyOf(entries),
                entries.size()
        );
    }

    private void sendPdc(CommandSender sender, Player player, boolean serverFeaturesOnly) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        java.util.function.Predicate<org.bukkit.NamespacedKey> filter = serverFeaturesOnly
                ? PersistentDataInspector::isServerFeaturesKey
                : ignored -> true;
        List<PlayerDataEntry> entries = pdcInspector.inspect(pdc, filter, maxEntries, maxValueLength);
        int total = Math.toIntExact(pdcInspector.count(pdc, filter));
        sendInspection(
                sender,
                player.getName(),
                player.getUniqueId(),
                serverFeaturesOnly ? "settings" : "pdc",
                "live",
                entries,
                total
        );
    }

    private void completeOffline(
            CommandSender sender,
            String requestedTarget,
            View view,
            OfflineResult result,
            Throwable failure
    ) {
        if (!active.get() || !canReceive(sender)) {
            return;
        }

        Player online = findOnline(requestedTarget);
        if (online == null && result != null && result.target() != null) {
            online = Bukkit.getPlayer(result.target().playerId());
        }
        if (online != null) {
            inspectOnline(sender, online, view);
            return;
        }

        if (failure != null) {
            Throwable cause = rootCause(failure);
            feature.getLogger().warning(
                    "Could not inspect playerdata for " + requestedTarget + ": " + cause.getMessage()
            );
            feature.send(sender, "playerdata.read_failed", "player", requestedTarget);
            return;
        }
        if (result == null || result.kind() == OfflineResult.Kind.NOT_FOUND) {
            feature.send(sender, "playerdata.not_found", "player", requestedTarget);
            return;
        }
        if (result.kind() == OfflineResult.Kind.RUNTIME_UNAVAILABLE) {
            feature.send(
                    sender,
                    "playerdata.offline_runtime",
                    "player",
                    result.target().playerName()
            );
            return;
        }
        NbtPlayerDataReader.Inspection inspection = result.inspection();
        sendInspection(
                sender,
                inspection.target().playerName(),
                inspection.target().playerId(),
                inspection.section(),
                "offline .dat",
                inspection.entries(),
                inspection.totalEntries()
        );
    }

    private void sendInspection(
            CommandSender sender,
            String playerName,
            UUID playerId,
            String section,
            String source,
            List<PlayerDataEntry> entries,
            int totalEntries
    ) {
        Component header = Component.text("PlayerData ", NamedTextColor.GOLD)
                .append(Component.text(playerName, NamedTextColor.YELLOW))
                .append(Component.text(" [", NamedTextColor.DARK_GRAY))
                .append(Component.text(section, NamedTextColor.AQUA))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text("(" + source + ")", NamedTextColor.GRAY));
        sender.sendMessage(header);
        sender.sendMessage(Component.text("UUID: " + playerId, NamedTextColor.DARK_GRAY));

        if (entries.isEmpty()) {
            feature.send(sender, "playerdata.empty");
            return;
        }
        for (PlayerDataEntry entry : entries) {
            sender.sendMessage(Component.text(" • ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(entry.key(), NamedTextColor.GRAY))
                    .append(Component.text(" [" + entry.type() + "]", NamedTextColor.DARK_GRAY))
                    .append(Component.text(" = ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(entry.value(), NamedTextColor.WHITE)));
        }
        if (totalEntries > entries.size()) {
            feature.send(
                    sender,
                    "playerdata.truncated",
                    "shown",
                    Integer.toString(entries.size()),
                    "total",
                    Integer.toString(totalEntries)
            );
        }
    }

    private PlayerDataEntry entry(String key, String type, Object value) {
        String rendered = String.valueOf(value);
        if (rendered.length() > maxValueLength) {
            rendered = rendered.substring(0, maxValueLength - 1) + "…";
        }
        return new PlayerDataEntry(key, type, rendered);
    }

    private static Player findOnline(String target) {
        Player exact = Bukkit.getPlayerExact(target);
        if (exact != null) {
            return exact;
        }
        try {
            return Bukkit.getPlayer(UUID.fromString(target));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static UUID preferredOfflineId(String target) {
        try {
            return UUID.fromString(target);
        } catch (IllegalArgumentException ignored) {
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(target);
            return cached == null ? null : cached.getUniqueId();
        }
    }

    private void scheduleMain(Runnable task) {
        if (!active.get()) {
            return;
        }
        try {
            feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(task);
        } catch (RuntimeException ignored) {
            // Feature shutdown can race an async completion; cleanup owns cancellation.
        }
    }

    private static boolean canReceive(CommandSender sender) {
        return !(sender instanceof Player player) || player.isOnline();
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable result = throwable;
        while (result.getCause() != null && result.getCause() != result) {
            result = result.getCause();
        }
        return result;
    }

    private static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private static float round(float value) {
        return Math.round(value * 100.0F) / 100.0F;
    }

    private record OfflineResult(
            Kind kind,
            NbtPlayerDataReader.ResolvedPlayerData target,
            NbtPlayerDataReader.Inspection inspection
    ) {
        enum Kind {
            INSPECTION,
            NOT_FOUND,
            RUNTIME_UNAVAILABLE
        }

        static OfflineResult inspection(NbtPlayerDataReader.Inspection inspection) {
            return new OfflineResult(Kind.INSPECTION, inspection.target(), inspection);
        }

        static OfflineResult notFound() {
            return new OfflineResult(Kind.NOT_FOUND, null, null);
        }

        static OfflineResult runtimeUnavailable(NbtPlayerDataReader.ResolvedPlayerData target) {
            return new OfflineResult(Kind.RUNTIME_UNAVAILABLE, target, null);
        }
    }
}
