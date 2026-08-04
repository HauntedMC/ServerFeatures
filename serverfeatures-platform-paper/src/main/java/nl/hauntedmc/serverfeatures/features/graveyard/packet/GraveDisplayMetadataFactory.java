package nl.hauntedmc.serverfeatures.features.graveyard.packet;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Creates version-correct display metadata from unspawned Bukkit entity templates.
 *
 * <p>Metadata indexes are protocol-version-specific. Letting Paper and PacketEvents translate an
 * unspawned template keeps the grave renderer aligned with the exact runtime version instead of
 * hard-coding indexes that silently stop rendering after protocol changes.</p>
 */
final class GraveDisplayMetadataFactory {
    private volatile Integer textComponentIndex;

    List<EntityData<?>> block(
            World world,
            Material material,
            com.github.retrooper.packetevents.util.Vector3f translation,
            com.github.retrooper.packetevents.util.Vector3f scale,
            int glowRgb
    ) {
        BlockDisplay display = world.createEntity(templateLocation(world), BlockDisplay.class);
        configureDisplay(display, glowRgb, Math.max(scale.getX(), scale.getZ()), scale.getY());
        display.setBlock(Bukkit.createBlockData(material));
        display.setTransformation(new Transformation(
                new Vector3f(translation.getX(), translation.getY(), translation.getZ()),
                new Quaternionf(),
                new Vector3f(scale.getX(), scale.getY(), scale.getZ()),
                new Quaternionf()
        ));
        return List.copyOf(SpigotConversionUtil.getEntityMetadata(display));
    }

    List<EntityData<?>> text(
            World world,
            Component title,
            String timer,
            int glowRgb
    ) {
        TextDisplay display = world.createEntity(templateLocation(world), TextDisplay.class);
        configureDisplay(display, glowRgb, 2.5f, 1.5f);
        display.text(title.append(Component.newline()).append(Component.text(timer)));
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(true);
        display.setShadowed(true);
        display.setBackgroundColor(Color.fromARGB(112, 0, 0, 0));
        display.setLineWidth(180);
        display.setTransformation(new Transformation(
                new Vector3f(0.0f, 1.55f, 0.0f),
                new Quaternionf(),
                new Vector3f(1.0f, 1.0f, 1.0f),
                new Quaternionf()
        ));
        List<EntityData<?>> metadata = List.copyOf(SpigotConversionUtil.getEntityMetadata(display));
        textComponentIndex = findTextComponentIndex(metadata);
        return metadata;
    }

    List<EntityData<?>> timer(World world, Component title, String timer) {
        Integer index = textComponentIndex;
        if (index == null) {
            TextDisplay template = world.createEntity(templateLocation(world), TextDisplay.class);
            template.text(Component.empty());
            index = findTextComponentIndex(SpigotConversionUtil.getEntityMetadata(template));
            textComponentIndex = index;
        }
        return List.of(new EntityData<>(
                index,
                EntityDataTypes.ADV_COMPONENT,
                title.append(Component.newline()).append(Component.text(timer))
        ));
    }

    List<EntityData<?>> interaction(World world, float width, float height) {
        Interaction interaction = world.createEntity(templateLocation(world), Interaction.class);
        interaction.setInteractionWidth(width);
        interaction.setInteractionHeight(height);
        interaction.setResponsive(true);
        return List.copyOf(SpigotConversionUtil.getEntityMetadata(interaction));
    }

    private static int findTextComponentIndex(List<EntityData<?>> metadata) {
        return metadata.stream()
                .filter(data -> EntityDataTypes.ADV_COMPONENT.equals(data.getType()))
                .mapToInt(EntityData::getIndex)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Paper did not expose TextDisplay text metadata"));
    }

    private static void configureDisplay(
            Display display,
            int glowRgb,
            float displayWidth,
            float displayHeight
    ) {
        display.setBrightness(new Display.Brightness(15, 15));
        display.setGlowing(true);
        display.setGlowColorOverride(Color.fromRGB(glowRgb & 0xFFFFFF));
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(0);
        display.setTeleportDuration(0);
        display.setViewRange(1.25f);
        display.setDisplayWidth(Math.max(1.0f, displayWidth));
        display.setDisplayHeight(Math.max(1.0f, displayHeight));
        display.setShadowRadius(0.0f);
        display.setShadowStrength(0.0f);
    }

    private static org.bukkit.Location templateLocation(World world) {
        double y = world.getMinHeight() + ((world.getMaxHeight() - world.getMinHeight()) / 2.0);
        return new org.bukkit.Location(world, 0.0, y, 0.0);
    }
}
