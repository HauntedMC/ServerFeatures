package nl.hauntedmc.serverfeatures.common.hook;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.ViaAPI;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Thin wrapper around ViaVersion to keep external API usage contained.
 */
public final class ViaVersionHook {

    private final ViaAPI<?> api;

    public ViaVersionHook() {
        this.api = Via.getAPI();
    }

    public boolean isAvailable() {
        return api != null;
    }

    public int getServerNativeProtocolId() {
        ensureAvailable();

        return api.getServerVersion().highestSupportedProtocolVersion().getVersion();
    }

    public String getServerNativeProtocolName() {
        ensureAvailable();
        return safeNameForId(getServerNativeProtocolId());
    }

    public int getClientProtocolId(Player player) {
        Objects.requireNonNull(player, "player");
        ensureAvailable();
        return api.getPlayerVersion(player.getUniqueId());
    }

    public String getClientProtocolName(Player player) {
        return safeNameForId(getClientProtocolId(player));
    }

    private String safeNameForId(int id) {
        ProtocolVersion pv = ProtocolVersion.getProtocol(id);
        return pv.getName();
    }

    private void ensureAvailable() {
        if (!isAvailable()) throw new IllegalStateException("ViaVersion is not available");
    }
}
