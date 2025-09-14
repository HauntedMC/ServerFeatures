package nl.hauntedmc.serverfeatures.features.skins.service;

import nl.hauntedmc.serverfeatures.features.skins.Skins;
import nl.hauntedmc.serverfeatures.features.skins.event.SkinUpdateEvent;
import nl.hauntedmc.serverfeatures.features.skins.event.SkinUpdateType;
import nl.hauntedmc.serverfeatures.features.skins.internal.SkinState;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public class SkinService {

    // Mojang username rules: 3-16 characters, letters/digits/underscore
    private static final Pattern MC_NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    // Mojang endpoints
    private static final String URL_NAME_TO_UUID = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String URL_SESSION      = "https://sessionserver.mojang.com/session/minecraft/profile/";

    // HTTP
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQ_TIMEOUT     = Duration.ofSeconds(8);
    private static final int      MAX_RETRIES     = 2;   // total requests = 1 + retries
    private static final long     BASE_BACKOFF_MS = 250; // + jitter

    private final Skins feature;
    private final SkinState state;

    private final HttpClient http;

    // Simple TTL caches to reduce pressure on Mojang and stay well under rate limits.
    // Name -> UUID (5 min)
    private final Map<String, CacheEntry<UUID>> nameToUuidCache = new ConcurrentHashMap<>();
    // UUID -> signed 'textures' property + canonical name (2 min)
    private final Map<UUID, CacheEntry<ProfileData>> uuidToProfileCache = new ConcurrentHashMap<>();

    // TTLs
    private static final long TTL_NAME_UUID_MS   = Duration.ofMinutes(5).toMillis();
    private static final long TTL_PROFILE_MS     = Duration.ofMinutes(2).toMillis();

    public SkinService(Skins feature) {
        this.feature = feature;
        this.state = feature.getState();
        this.http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    /* ------------------------------------------------------- */
    /* Public API                                              */
    /* ------------------------------------------------------- */

    /**
     * Apply skin from another Minecraft account to the target player.
     * Uses direct Mojang lookups to fetch signed 'textures' and avoids server caches.
     */
    public void applySkin(CommandSender actor, Player target, String donorName, boolean isSelf) {
        final String cleaned = donorName.trim();
        if (!isValidName(cleaned)) {
            sendSync(actor, "skins.invalid_name", Map.of("skin", cleaned));
            return;
        }

        // Enforce cooldown for self-usage unless bypass permission
        if (isSelf && !actor.hasPermission("serverfeatures.feature.skins.bypass.cooldown")) {
            if (!checkAndMaybeStartCooldown(target.getUniqueId(), actor)) {
                return;
            }
        }

        sendSync(actor, "skins.working", Map.of("skin", cleaned));
        feature.getLogger().info("[Skins] " + actor.getName() + " requested skin '" + cleaned + "' for " + target.getName());

        feature.getLifecycleManager().getTaskManager().scheduleAsyncTask(() -> {
            try {
                ProfileData donor = fetchOfficialProfileByName(cleaned);
                if (donor == null || donor.texturesProp == null) {
                    scheduleSend(actor, "skins.lookup_failed", Map.of("skin", cleaned));
                    feature.getLogger().warning("[Skins] Failed to resolve official textures for '" + cleaned + "'");
                    if (isSelf && !actor.hasPermission("serverfeatures.feature.skins.bypass.cooldown")) {
                        state.clearLastUse(target.getUniqueId());
                    }
                    return;
                }

                feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
                    Player now = Bukkit.getPlayer(target.getUniqueId());
                    if (now == null || !now.isOnline()) {
                        feature.getLogger().info("[Skins] Target went offline before skin could be applied: " + target.getName());
                        if (isSelf && !actor.hasPermission("serverfeatures.feature.skins.bypass.cooldown")) {
                            state.clearLastUse(target.getUniqueId());
                        }
                        return;
                    }
                    applyTexturesToPlayer(now, donor); // inject signed 'textures' + refresh clients
                    state.markCustomSkin(now.getUniqueId(), true);

                    Bukkit.getPluginManager().callEvent(new SkinUpdateEvent(now, SkinUpdateType.SET, donor.name()));

                    if (isSelf) {
                        send(actor, "skins.applied.self", Map.of("skin", donor.name));
                    } else {
                        send(actor, "skins.applied.other", Map.of("player", now.getName(), "skin", donor.name));
                        now.sendMessage(feature.getLocalizationHandler()
                                .getMessage("skins.notify_target_applied")
                                .withPlaceholders(Map.of("skin", donor.name))
                                .forAudience(now)
                                .build());
                    }

                    feature.getLogger().info("[Skins] Applied skin '" + donor.name + "' to " + now.getName() + " (by " + actor.getName() + ")");
                });

            } catch (Throwable ex) {
                feature.getLogger().warning("[Skins] Exception while resolving skin '" + cleaned + "': " + ex);
                scheduleSend(actor, "skins.lookup_failed", Map.of("skin", cleaned));
                if (isSelf && !actor.hasPermission("serverfeatures.feature.skins.bypass.cooldown")) {
                    state.clearLastUse(target.getUniqueId());
                }
            }
        });
    }

    /**
     * Remove the temporary custom skin applied by this feature.
     * Fetches the player's official textures from Mojang Session Server and reapplies those textures.
     * No cooldown for remove.
     */
    public void removeSkin(CommandSender actor, Player target, boolean isSelf) {
        UUID uuid = target.getUniqueId();

        if (!state.hasCustomSkin(uuid)) {
            if (isSelf) {
                sendSync(actor, "skins.none_applied.self", Map.of());
            } else {
                sendSync(actor, "skins.none_applied.other", Map.of("player", target.getName()));
            }
            return;
        }

        if (isSelf) {
            sendSync(actor, "skins.removing", Map.of());
        }

        feature.getLogger().info("[Skins] " + actor.getName() + " requested skin removal for " + target.getName());

        feature.getLifecycleManager().getTaskManager().scheduleAsyncTask(() -> {
            try {
                // official own profile (uncached from Bukkit; cached in our TTL cache)
                ProfileData official = fetchOfficialProfileByUuid(uuid, target.getName());

                feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
                    Player now = Bukkit.getPlayer(uuid);
                    if (now == null || !now.isOnline()) {
                        state.markCustomSkin(uuid, false);
                        return;
                    }

                    if (official != null && official.texturesProp != null) {
                        applyTexturesToPlayer(now, official);
                        Bukkit.getPluginManager().callEvent(new SkinUpdateEvent(now, SkinUpdateType.REMOVE, official.name()));
                    } else {
                        feature.getLogger().warning("[Skins] Kon originele textures niet ophalen voor " + now.getName());
                    }

                    state.markCustomSkin(uuid, false);
                    if (isSelf) {
                        send(actor, "skins.removed.self", Map.of());
                    } else {
                        send(actor, "skins.removed.other", Map.of("player", now.getName()));
                        now.sendMessage(feature.getLocalizationHandler()
                                .getMessage("skins.notify_target_removed")
                                .forAudience(now)
                                .build());
                    }

                    feature.getLogger().info("[Skins] Removed custom skin from " + now.getName() + " (by " + actor.getName() + ")");
                });

            } catch (Throwable ex) {
                feature.getLogger().warning("[Skins] Exception while removing skin for " + target.getName() + ": " + ex);
                feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
                    Player now = Bukkit.getPlayer(uuid);
                    if (now != null && now.isOnline()) {
                        state.markCustomSkin(uuid, false);
                        if (isSelf) {
                            send(actor, "skins.removed.self", Map.of());
                        } else {
                            send(actor, "skins.removed.other", Map.of("player", now.getName()));
                            now.sendMessage(feature.getLocalizationHandler()
                                    .getMessage("skins.notify_target_removed")
                                    .forAudience(now)
                                    .build());
                        }
                    }
                });
            }
        });
    }

    /* ------------------------------------------------------- */
    /* Helpers                                                 */
    /* ------------------------------------------------------- */

    private boolean isValidName(String name) {
        return MC_NAME.matcher(name).matches();
    }

    /** Returns true if the caller may proceed; otherwise sends cooldown message and returns false. */
    private boolean checkAndMaybeStartCooldown(UUID uuid, CommandSender actor) {
        int cd = state.getCooldownSeconds();
        if (cd <= 0) return true;

        long now = System.currentTimeMillis();
        long last = state.getLastUse(uuid);
        long elapsed = (now - last) / 1000L;

        if (last != 0L && elapsed < cd) {
            long remaining = cd - elapsed;
            sendSync(actor, "skins.cooldown_active", Map.of("seconds", String.valueOf(remaining)));
            return false;
        }

        state.setLastUse(uuid, now);
        return true;
    }

    /**
     * Inject EXACT signed 'textures' property into the player's profile, then refresh client views.
     */
    private void applyTexturesToPlayer(Player target, ProfileData donor) {
        if (donor == null || donor.texturesProp == null) {
            feature.getLogger().warning("[Skins] applyTexturesToPlayer called without textures");
            return;
        }

        var profile = target.getPlayerProfile();
        profile.getProperties().removeIf(p -> "textures".equals(p.getName()));
        profile.setProperty(donor.texturesProp);
        target.setPlayerProfile(profile);
    }

    private void send(CommandSender audience, String key, Map<String, String> placeholders) {
        var msg = feature.getLocalizationHandler().getMessage(key);
        if (placeholders != null && !placeholders.isEmpty()) {
            msg = msg.withPlaceholders(placeholders);
        }
        audience.sendMessage(msg.forAudience(audience).build());
    }

    private void scheduleSend(CommandSender audience, String key, Map<String, String> placeholders) {
        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> send(audience, key, placeholders));
    }

    /** Convenience: always send on main thread. */
    private void sendSync(CommandSender audience, String key, Map<String, String> placeholders) {
        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> send(audience, key, placeholders));
    }

    /* ------------------------------------------------------- */
    /* Mojang lookup (fresh, with TTL cache & retries)         */
    /* ------------------------------------------------------- */

    private ProfileData fetchOfficialProfileByName(String name) {
        // 1) name -> uuid (TTL cache)
        UUID uuid = cachedOr(nameToUuidCache, name, TTL_NAME_UUID_MS, () -> resolveUuidByName(name));
        if (uuid == null) return null;

        // 2) uuid -> signed textures + canonical name (TTL cache)
        return cachedOr(uuidToProfileCache, uuid, TTL_PROFILE_MS, () -> resolveProfileByUuid(uuid, name));
    }

    private ProfileData fetchOfficialProfileByUuid(UUID uuid, String fallbackName) {
        return cachedOr(uuidToProfileCache, uuid, TTL_PROFILE_MS, () -> resolveProfileByUuid(uuid, fallbackName));
    }

    private UUID resolveUuidByName(String name) {
        HttpRequest req = baseGet(URI.create(URL_NAME_TO_UUID + name));
        HttpResponse<String> resp = sendWithRetries(req);
        if (!isOk(resp)) return null;

        try {
            var obj = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
            if (!obj.has("id")) return null;
            String rawId = obj.get("id").getAsString();
            return uuidFromUndashed(rawId);
        } catch (Throwable t) {
            feature.getLogger().warning("[Skins] JSON parse error (name->uuid) for '" + name + "': " + t.getMessage());
            return null;
        }
    }

    private ProfileData resolveProfileByUuid(UUID uuid, String fallbackName) {
        String undashed = uuid.toString().replace("-", "");
        HttpRequest req = baseGet(URI.create(URL_SESSION + undashed + "?unsigned=false"));
        HttpResponse<String> resp = sendWithRetries(req);
        if (!isOk(resp)) return null;

        try {
            var obj = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
            String name = obj.has("name") ? obj.get("name").getAsString() : fallbackName;

            if (!obj.has("properties")) return null;
            var props = obj.getAsJsonArray("properties");
            com.destroystokyo.paper.profile.ProfileProperty texturesProp = null;
            for (var el : props) {
                var p = el.getAsJsonObject();
                if (!p.has("name")) continue;
                if ("textures".equals(p.get("name").getAsString())) {
                    String value = p.has("value") ? p.get("value").getAsString() : null;
                    String sig = p.has("signature") ? p.get("signature").getAsString() : null;
                    if (value != null && sig != null) {
                        texturesProp = new com.destroystokyo.paper.profile.ProfileProperty("textures", value, sig);
                    }
                    break;
                }
            }
            if (texturesProp == null) return null;
            return new ProfileData(uuid, name, texturesProp);
        } catch (Throwable t) {
            feature.getLogger().warning("[Skins] JSON parse error (session) for " + uuid + ": " + t.getMessage());
            return null;
        }
    }

    /* ------------------------------------------------------- */
    /* HTTP utilities                                          */
    /* ------------------------------------------------------- */

    private HttpRequest baseGet(URI uri) {
        // Identify your plugin politely (helps Mojang ops)
        String ua = feature.getPlugin().getName() + "/" + feature.getPlugin().getDescription().getVersion()
                + " (+https://hauntedmc.nl; Admin contact in server logs)";
        return HttpRequest.newBuilder()
                .GET()
                .uri(uri)
                .timeout(REQ_TIMEOUT)
                .header("User-Agent", ua)
                .build();
    }

    private HttpResponse<String> sendWithRetries(HttpRequest req) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                int code = resp.statusCode();

                // Success
                if (code == 200) return resp;

                // 204 (name not found) / 404 -> no data
                if (code == 204 || code == 404) return resp;

                // 429 Too Many Requests: respect Retry-After if present
                if (code == 429 && attempt <= MAX_RETRIES + 1) {
                    long sleep = retryAfterMs(resp.headers(), attempt);
                    sleepQuiet(sleep);
                    continue;
                }

                // 5xx: retry with backoff
                if (code >= 500 && code < 600 && attempt <= MAX_RETRIES + 1) {
                    sleepQuiet(backoffMs(attempt));
                    continue;
                }

                // Other non-OK: return as-is (caller checks isOk)
                return resp;

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception ex) {
                if (attempt > MAX_RETRIES + 1) {
                    feature.getLogger().warning("[Skins] HTTP failed for " + req.uri() + ": " + ex.getMessage());
                    return null;
                }
                sleepQuiet(backoffMs(attempt));
            }
        }
    }

    private static long retryAfterMs(HttpHeaders headers, int attempt) {
        try {
            Optional<String> ra = headers.firstValue("Retry-After");
            if (ra.isPresent()) {
                String v = ra.get().trim();
                // Mojang tends to send seconds; support both seconds or HTTP-date not needed here
                long sec = Long.parseLong(v);
                return Math.max(1000L, sec * 1000L);
            }
        } catch (Exception ignored) {}
        return backoffMs(attempt);
    }

    private static long backoffMs(int attempt) {
        long jitter = ThreadLocalRandom.current().nextLong(100, 400);
        return (long) (BASE_BACKOFF_MS * Math.pow(2, Math.max(0, attempt - 1))) + jitter;
    }

    private static void sleepQuiet(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    private static boolean isOk(HttpResponse<String> resp) {
        return resp != null && resp.statusCode() == 200 && resp.body() != null && !resp.body().isEmpty();
    }

    /* ------------------------------------------------------- */
    /* Small cache helpers                                     */
    /* ------------------------------------------------------- */

    private static final class CacheEntry<T> {
        final long expiresAt;
        final T value;
        CacheEntry(T value, long ttlMs) {
            this.value = value;
            this.expiresAt = System.currentTimeMillis() + ttlMs;
        }
        boolean fresh() { return System.currentTimeMillis() < expiresAt; }
    }

    private interface SupplierWithException<T> { T get(); }

    private static <K, V> V cachedOr(Map<K, CacheEntry<V>> map, K key, long ttlMs, SupplierWithException<V> supplier) {
        try {
            CacheEntry<V> e = map.get(key);
            if (e != null && e.fresh()) return e.value;
            V v = supplier.get();
            if (v != null) map.put(key, new CacheEntry<>(v, ttlMs));
            return v;
        } catch (Exception ignored) {
            return null;
        }
    }

    /* ------------------------------------------------------- */
    /* Utils                                                   */
    /* ------------------------------------------------------- */

    private static UUID uuidFromUndashed(String undashed) {
        if (undashed == null || undashed.length() != 32) return null;
        String dashed = undashed.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5"
        );
        try {
            return UUID.fromString(dashed);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /* ------------------------------------------------------- */
    /* Data holder                                             */
    /* ------------------------------------------------------- */

    /**
     * @param name canonical name from SessionServer (or fallback)
     */
    private record ProfileData(UUID uuid, String name, com.destroystokyo.paper.profile.ProfileProperty texturesProp) {
    }

}
