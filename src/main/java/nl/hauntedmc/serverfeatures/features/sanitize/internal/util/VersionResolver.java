package nl.hauntedmc.serverfeatures.features.sanitize.internal.util;

import nl.hauntedmc.serverfeatures.api.util.text.pattern.FormatPatterns;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.lang.reflect.Method;
import java.util.regex.Matcher;

public final class VersionResolver {

    private VersionResolver() {
    }

    public static String resolveVersion(Server server, FeatureLogger log) {
        try {
            Method m = server.getClass().getMethod("getMinecraftVersion");
            Object v = m.invoke(server);
            if (v instanceof String s && !s.isBlank()) {
                return s.trim();
            }
        } catch (Throwable ignored) {
        }

        String bukkit = Bukkit.getBukkitVersion();
        if (!bukkit.isBlank()) {
            String base = bukkit.split("-")[0].trim();
            if (!base.isBlank()) return base;
        }

        String serverVersion = server.getVersion();
        Matcher m = FormatPatterns.MC_IN_VERSION.matcher(serverVersion);
        if (m.find()) {
            return m.group(1);
        }

        // 4) Fallback — extremely unlikely
        log.warning("Could not resolve Minecraft version reliably; defaulting to Bukkit version raw.");
        return bukkit;
    }
}
