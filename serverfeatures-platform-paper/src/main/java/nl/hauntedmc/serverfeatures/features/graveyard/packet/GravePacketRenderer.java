package nl.hauntedmc.serverfeatures.features.graveyard.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.api.io.packet.BundleDelimiterPacket;
import nl.hauntedmc.serverfeatures.features.graveyard.config.GraveyardSettings;
import nl.hauntedmc.serverfeatures.features.graveyard.model.Grave;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * Emits one coherent client-side grave composition. A failed partial spawn is always followed by a
 * destroy packet for every component id.
 */
public final class GravePacketRenderer {
    private final GraveyardSettings settings;
    private final GraveDisplayMetadataFactory metadataFactory = new GraveDisplayMetadataFactory();
    private final Logger logger;

    public GravePacketRenderer(GraveyardSettings settings, Logger logger) {
        this.settings = settings;
        this.logger = logger;
    }

    public void spawn(
            Grave grave,
            GravePacketIdentity identity,
            Player viewer,
            String timer,
            int glowRgb
    ) {
        org.bukkit.Location bukkitLocation = grave.location().resolve()
                .orElseThrow(() -> new IllegalStateException("Cannot render a grave in an unavailable world"));
        Location packetLocation = SpigotConversionUtil.fromBukkitLocation(bukkitLocation);
        Component title = Component.text(grave.ownerName() + "'s Grave", NamedTextColor.GRAY);

        boolean bundleOpened = false;
        RuntimeException failure = null;
        try {
            new BundleDelimiterPacket().sendTo(viewer);
            bundleOpened = true;
            sendSpawn(viewer, identity.baseEntityId(), identity.baseEntityUuid(), EntityTypes.BLOCK_DISPLAY, packetLocation);
            sendMetadata(viewer, identity.baseEntityId(), metadataFactory.block(
                    settings.baseMaterial(),
                    new com.github.retrooper.packetevents.util.Vector3f(-0.75f, 0.0f, -0.45f),
                    new com.github.retrooper.packetevents.util.Vector3f(1.5f, 0.5f, 0.9f),
                    glowRgb
            ));
            sendSpawn(
                    viewer,
                    identity.headstoneEntityId(),
                    identity.headstoneEntityUuid(),
                    EntityTypes.BLOCK_DISPLAY,
                    packetLocation
            );
            sendMetadata(viewer, identity.headstoneEntityId(), metadataFactory.block(
                    settings.headstoneMaterial(),
                    new com.github.retrooper.packetevents.util.Vector3f(-0.25f, 0.15f, 0.25f),
                    new com.github.retrooper.packetevents.util.Vector3f(0.5f, 1.1f, 0.25f),
                    glowRgb
            ));
            sendSpawn(viewer, identity.textEntityId(), identity.textEntityUuid(), EntityTypes.TEXT_DISPLAY, packetLocation);
            sendMetadata(viewer, identity.textEntityId(), metadataFactory.text(title, timer, glowRgb));
            sendSpawn(
                    viewer,
                    identity.interactionEntityId(),
                    identity.interactionEntityUuid(),
                    EntityTypes.INTERACTION,
                    packetLocation
            );
            sendMetadata(viewer, identity.interactionEntityId(), metadataFactory.interaction(1.6f, 1.4f));
        } catch (RuntimeException exception) {
            failure = exception;
        } finally {
            if (bundleOpened) {
                try {
                    new BundleDelimiterPacket().sendTo(viewer);
                } catch (RuntimeException closeFailure) {
                    if (failure == null) {
                        failure = closeFailure;
                    } else {
                        failure.addSuppressed(closeFailure);
                    }
                }
            }
        }
        if (failure != null) {
            destroy(identity, viewer);
            throw failure;
        }
    }

    public void updateTimer(Grave grave, GravePacketIdentity identity, Player viewer, String timer) {
        Component title = Component.text(grave.ownerName() + "'s Grave", NamedTextColor.GRAY);
        sendMetadata(viewer, identity.textEntityId(), metadataFactory.timer(title, timer));
    }

    public void destroy(GravePacketIdentity identity, Player viewer) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        RuntimeException first = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                send(viewer, new WrapperPlayServerDestroyEntities(identity.entityIds()));
                return;
            } catch (RuntimeException exception) {
                if (first == null) {
                    first = exception;
                } else {
                    first.addSuppressed(exception);
                }
            }
        }
        logger.warning(
                "Failed to destroy Graveyard packet entities for " + viewer.getName()
                        + ": " + rootMessage(first)
        );
    }

    private void sendSpawn(
            Player viewer,
            int entityId,
            java.util.UUID entityUuid,
            EntityType entityType,
            Location location
    ) {
        send(viewer, new WrapperPlayServerSpawnEntity(
                entityId,
                entityUuid,
                entityType,
                location,
                location.getYaw(),
                0,
                null
        ));
    }

    private void sendMetadata(
            Player viewer,
            int entityId,
            java.util.List<com.github.retrooper.packetevents.protocol.entity.data.EntityData<?>> metadata
    ) {
        send(viewer, new WrapperPlayServerEntityMetadata(entityId, metadata));
    }

    private void send(Player viewer, Object packet) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
    }

    private static String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown packet failure";
        }
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
