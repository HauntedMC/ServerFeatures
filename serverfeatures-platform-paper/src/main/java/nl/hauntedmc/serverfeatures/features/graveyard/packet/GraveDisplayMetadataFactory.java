package nl.hauntedmc.serverfeatures.features.graveyard.packet;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3f;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

final class GraveDisplayMetadataFactory {
    private static final byte GLOWING_FLAG = 0x40;

    List<EntityData<?>> block(
            Material material,
            Vector3f translation,
            Vector3f scale,
            int glowRgb
    ) {
        List<EntityData<?>> metadata = displayBase(translation, scale, glowRgb, (byte) 0);
        WrappedBlockState blockState = SpigotConversionUtil.fromBukkitBlockData(material.createBlockData());
        metadata.add(new EntityData<>(23, EntityDataTypes.BLOCK_STATE, blockState.getGlobalId()));
        return List.copyOf(metadata);
    }

    List<EntityData<?>> text(Component text, String timer, int glowRgb) {
        List<EntityData<?>> metadata = displayBase(
                new Vector3f(0.0f, 1.55f, 0.0f),
                new Vector3f(1.0f, 1.0f, 1.0f),
                glowRgb,
                (byte) 3
        );
        metadata.add(new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, text.append(Component.newline())
                .append(Component.text(timer))));
        metadata.add(new EntityData<>(24, EntityDataTypes.INT, 180));
        metadata.add(new EntityData<>(25, EntityDataTypes.INT, 0));
        metadata.add(new EntityData<>(26, EntityDataTypes.BYTE, (byte) -1));
        metadata.add(new EntityData<>(27, EntityDataTypes.BYTE, (byte) 1));
        return List.copyOf(metadata);
    }

    List<EntityData<?>> timer(Component text, String timer) {
        return List.of(new EntityData<>(
                23,
                EntityDataTypes.ADV_COMPONENT,
                text.append(Component.newline()).append(Component.text(timer))
        ));
    }

    List<EntityData<?>> interaction(float width, float height) {
        return List.of(
                new EntityData<>(5, EntityDataTypes.BOOLEAN, true),
                new EntityData<>(8, EntityDataTypes.FLOAT, width),
                new EntityData<>(9, EntityDataTypes.FLOAT, height),
                new EntityData<>(10, EntityDataTypes.BOOLEAN, true)
        );
    }

    private List<EntityData<?>> displayBase(
            Vector3f translation,
            Vector3f scale,
            int glowRgb,
            byte billboard
    ) {
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(0, EntityDataTypes.BYTE, GLOWING_FLAG));
        metadata.add(new EntityData<>(5, EntityDataTypes.BOOLEAN, true));
        metadata.add(new EntityData<>(8, EntityDataTypes.INT, 0));
        metadata.add(new EntityData<>(9, EntityDataTypes.INT, 0));
        metadata.add(new EntityData<>(10, EntityDataTypes.INT, 0));
        metadata.add(new EntityData<>(11, EntityDataTypes.VECTOR3F, translation));
        metadata.add(new EntityData<>(12, EntityDataTypes.VECTOR3F, scale));
        metadata.add(new EntityData<>(13, EntityDataTypes.QUATERNION, identityRotation()));
        metadata.add(new EntityData<>(14, EntityDataTypes.QUATERNION, identityRotation()));
        metadata.add(new EntityData<>(15, EntityDataTypes.BYTE, billboard));
        metadata.add(new EntityData<>(16, EntityDataTypes.INT, -1));
        metadata.add(new EntityData<>(17, EntityDataTypes.FLOAT, 1.0f));
        metadata.add(new EntityData<>(18, EntityDataTypes.FLOAT, 0.0f));
        metadata.add(new EntityData<>(19, EntityDataTypes.FLOAT, 1.0f));
        metadata.add(new EntityData<>(20, EntityDataTypes.FLOAT, 0.0f));
        metadata.add(new EntityData<>(21, EntityDataTypes.FLOAT, 0.0f));
        metadata.add(new EntityData<>(22, EntityDataTypes.INT, glowRgb));
        return metadata;
    }

    private Quaternion4f identityRotation() {
        return new Quaternion4f(0.0f, 0.0f, 0.0f, 1.0f);
    }
}
