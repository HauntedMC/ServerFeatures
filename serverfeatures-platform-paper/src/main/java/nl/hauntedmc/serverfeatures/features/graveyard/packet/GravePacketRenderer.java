package nl.hauntedmc.serverfeatures.features.graveyard.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.features.graveyard.config.GraveyardSettings;
import nl.hauntedmc.serverfeatures.features.graveyard.model.Grave;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Emits one coherent client-side grave composition.
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
        World world = bukkitLocation.getWorld();
        Location packetLocation = SpigotConversionUtil.fromBukkitLocation(bukkitLocation);
        Component title = Component.text(grave.ownerName() + "'s Grave", NamedTextColor.GRAY);

        try {
            spawnBlock(
                    viewer,
                    identity.baseEntityId(),
                    identity.baseEntityUuid(),
                    packetLocation,
                    world,
                    settings.baseMaterial(),
                    GraveVisualLayout.BASE,
                    glowRgb
            );
            spawnBlock(
                    viewer,
                    identity.headstoneEntityId(),
                    identity.headstoneEntityUuid(),
                    packetLocation,
                    world,
                    settings.headstoneMaterial(),
                    GraveVisualLayout.HEADSTONE_STEM,
                    glowRgb
            );
            spawnBlock(
                    viewer,
                    identity.crossbarEntityId(),
                    identity.crossbarEntityUuid(),
                    packetLocation,
                    world,
                    settings.headstoneMaterial(),
                    GraveVisualLayout.HEADSTONE_CROSSBAR,
                    glowRgb
            );
            sendSpawn(viewer, identity.textEntityId(), identity.textEntityUuid(), EntityTypes.TEXT_DISPLAY, packetLocation);
            sendMetadata(viewer, identity.textEntityId(), metadataFactory.text(
                    world,
                    title,
                    timer,
                    GraveVisualLayout.TEXT_OFFSET_Y,
                    glowRgb
            ));
            sendSpawn(
                    viewer,
                    identity.interactionEntityId(),
                    identity.interactionEntityUuid(),
                    EntityTypes.INTERACTION,
                    packetLocation
            );
            sendMetadata(
                    viewer,
                    identity.interactionEntityId(),
                    metadataFactory.interaction(
                            world,
                            GraveVisualLayout.INTERACTION_WIDTH,
                            GraveVisualLayout.INTERACTION_HEIGHT
                    )
            );
        } catch (RuntimeException exception) {
            try {
                destroy(identity, viewer);
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    public void updateTimer(
            Grave grave,
            GravePacketIdentity identity,
            Player viewer,
            String timer
    ) {
        org.bukkit.Location location = grave.location().resolve()
                .orElseThrow(() -> new IllegalStateException("Cannot update a grave in an unavailable world"));
        Component title = Component.text(grave.ownerName() + "'s Grave", NamedTextColor.GRAY);
        sendMetadata(
                viewer,
                identity.textEntityId(),
                metadataFactory.timer(location.getWorld(), title, timer)
        );
    }

    public void destroy(GravePacketIdentity identity, Player viewer) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        try {
            send(viewer, new WrapperPlayServerDestroyEntities(identity.entityIds()));
        } catch (RuntimeException exception) {
            logger.warning(
                    "Failed to destroy Graveyard packet entities for " + viewer.getName()
                            + ": " + rootMessage(exception)
            );
        }
    }

    private void spawnBlock(
            Player viewer,
            int entityId,
            UUID entityUuid,
            Location packetLocation,
            World world,
            Material material,
            GraveVisualLayout.Part part,
            int glowRgb
    ) {
        sendSpawn(viewer, entityId, entityUuid, EntityTypes.BLOCK_DISPLAY, packetLocation);
        sendMetadata(viewer, entityId, metadataFactory.block(
                world,
                material,
                part.translation(),
                part.scale(),
                glowRgb
        ));
    }

    private void sendSpawn(
            Player viewer,
            int entityId,
            UUID entityUuid,
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
            List<com.github.retrooper.packetevents.protocol.entity.data.EntityData<?>> metadata
    ) {
        send(viewer, new WrapperPlayServerEntityMetadata(entityId, metadata));
    }

    private static void send(Player viewer, PacketWrapper<?> packet) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
