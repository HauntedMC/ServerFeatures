package nl.hauntedmc.serverfeatures.features.graveyard.packet;

import io.github.retrooper.packetevents.util.SpigotReflectionUtil;

import java.util.List;
import java.util.UUID;

public record GravePacketIdentity(
        long generation,
        int baseEntityId,
        UUID baseEntityUuid,
        int headstoneEntityId,
        UUID headstoneEntityUuid,
        int textEntityId,
        UUID textEntityUuid,
        int interactionEntityId,
        UUID interactionEntityUuid
) {
    public static GravePacketIdentity create(long generation) {
        return new GravePacketIdentity(
                generation,
                SpigotReflectionUtil.generateEntityId(),
                UUID.randomUUID(),
                SpigotReflectionUtil.generateEntityId(),
                UUID.randomUUID(),
                SpigotReflectionUtil.generateEntityId(),
                UUID.randomUUID(),
                SpigotReflectionUtil.generateEntityId(),
                UUID.randomUUID()
        );
    }

    public int[] entityIds() {
        return new int[] {baseEntityId, headstoneEntityId, textEntityId, interactionEntityId};
    }

    public List<Integer> entityIdList() {
        return List.of(baseEntityId, headstoneEntityId, textEntityId, interactionEntityId);
    }
}
