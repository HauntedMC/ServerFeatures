package nl.hauntedmc.serverfeatures.features.nametags.internal;

import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.features.nametags.internal.properties.BillboardConstraints;
import com.github.retrooper.packetevents.util.Vector3f;
import org.bukkit.Color;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents an individual nametag.
 * Holds the owner, its properties configuration, a custom entity id and
 * the current viewers that can see this nametag.
 */
public class Nametag {
    private final Player nametagOwner;
    private final int entityId;
    private NametagProperties properties;
    private final Set<Player> viewers = ConcurrentHashMap.newKeySet();

    public Nametag(Player nametagOwner) {
        this.nametagOwner = nametagOwner;
        this.properties = new NametagProperties();
        this.entityId = SpigotReflectionUtil.generateEntityId();
        initDefaultProperties();
    }

    public Player getNametagOwner() {
        return nametagOwner;
    }

    public UUID getNametagOwnerId() {
        return nametagOwner.getUniqueId();
    }

    public int getEntityId() {
        return entityId;
    }

    public NametagProperties getNametagProperties() {
        return properties;
    }

    public Set<Player> getViewers() {
        return viewers;
    }

    /**
     * Updates the default properties based on owner's properties.
     */
    private void initDefaultProperties() {
        Component customName = Component.empty();
        if (nametagOwner.hasPermission("nametag.owner")) {
            customName = customName.append(Component.text("[Owner] ", NamedTextColor.DARK_RED));
        }
        customName = customName.append(Component.text(nametagOwner.getName(), NamedTextColor.GRAY));

        NametagProperties newLayout = new NametagProperties();
        newLayout.setText(customName);
        newLayout.setBillboardConstraints(BillboardConstraints.CENTER);
        newLayout.setTranslation(new Vector3f(0.0f, 0.3f, 0.0f));
        newLayout.setHasShadow(true);
        newLayout.setIsSeeThrough(false);
        newLayout.setBackgroundColor(Color.BLACK.setAlpha(0).asARGB());

        this.properties = newLayout;
    }
}
