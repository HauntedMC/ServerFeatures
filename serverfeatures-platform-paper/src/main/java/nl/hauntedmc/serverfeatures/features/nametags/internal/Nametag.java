package nl.hauntedmc.serverfeatures.features.nametags.internal;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.util.Vector3f;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import nl.hauntedmc.serverfeatures.features.nametags.internal.hook.PlaceholderHook;
import nl.hauntedmc.serverfeatures.features.nametags.internal.packet.NametagPacketProperties;
import nl.hauntedmc.serverfeatures.features.nametags.internal.packet.properties.BillboardConstraints;
import org.bukkit.Color;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One logical client-side nametag and its per-viewer delivery state.
 *
 * <p>The fake entity identity is rotated for hard lifecycle transitions. This prevents a late packet
 * from an older world, respawn, or teleport generation from mutating the current display.</p>
 */
public final class Nametag {
    private final Player nametagOwner;
    private final NametagPacketProperties properties;
    private final Map<UUID, NametagViewerState> viewerStates = new HashMap<>();

    private int ownerEntityId;
    private int entityId;
    private UUID entityUuid;
    private long entityGeneration;

    public Nametag(Player nametagOwner) {
        this.nametagOwner = nametagOwner;
        this.properties = new NametagPacketProperties();
        rotateEntityIdentity();
        initDefaultProperties();
    }

    public Player getNametagOwner() {
        return nametagOwner;
    }

    public UUID getNametagOwnerId() {
        return nametagOwner.getUniqueId();
    }

    public int getOwnerEntityId() {
        return ownerEntityId;
    }

    public int getEntityId() {
        return entityId;
    }

    public UUID getEntityUuid() {
        return entityUuid;
    }

    public long getEntityGeneration() {
        return entityGeneration;
    }

    public NametagPacketProperties getNametagProperties() {
        return properties;
    }

    public List<EntityData<?>> snapshotMetadata() {
        return List.copyOf(properties.getMetadata());
    }

    public NametagViewerState getOrCreateViewerState(UUID viewerId) {
        return viewerStates.computeIfAbsent(viewerId, ignored -> new NametagViewerState());
    }

    public NametagViewerState getViewerState(UUID viewerId) {
        return viewerStates.get(viewerId);
    }

    public Map<UUID, NametagViewerState> snapshotViewerStates() {
        return Map.copyOf(viewerStates);
    }

    public void removeViewerState(UUID viewerId, NametagViewerState expectedState) {
        viewerStates.remove(viewerId, expectedState);
    }

    public void clearViewerStates() {
        viewerStates.clear();
    }

    public void rotateEntityIdentity() {
        this.ownerEntityId = nametagOwner.getEntityId();
        this.entityId = SpigotReflectionUtil.generateEntityId();
        this.entityUuid = UUID.randomUUID();
        this.entityGeneration++;
    }

    private void initDefaultProperties() {
        updateNametagText();
        properties.setBillboardConstraints(BillboardConstraints.CENTER);
        properties.setTranslation(new Vector3f(0.0f, 0.3f, 0.0f));
        properties.setHasShadow(true);
        properties.setIsSeeThrough(false);
        properties.setHasNoGravity(true);
        properties.setBackgroundColor(Color.BLACK.setAlpha(0).asARGB());
    }

    public void updateNametagText() {
        properties.setText(PlaceholderHook.getInstance().getNametagText(getNametagOwner()));
    }
}
