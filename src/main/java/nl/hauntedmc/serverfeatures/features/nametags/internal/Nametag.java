package nl.hauntedmc.serverfeatures.features.nametags.internal;

import com.github.retrooper.packetevents.util.Vector3f;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import nl.hauntedmc.serverfeatures.features.nametags.internal.hook.PlaceholderHook;
import nl.hauntedmc.serverfeatures.features.nametags.internal.packet.NametagPacketProperties;
import nl.hauntedmc.serverfeatures.features.nametags.internal.packet.properties.BillboardConstraints;
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
    private final NametagPacketProperties properties;
    private final Set<Player> viewers = ConcurrentHashMap.newKeySet();

    public Nametag(Player nametagOwner) {
        this.nametagOwner = nametagOwner;
        this.properties = new NametagPacketProperties();
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

    public NametagPacketProperties getNametagProperties() {
        return properties;
    }

    public Set<Player> getViewers() {
        return viewers;
    }

    /**
     * Updates the default properties based on owner's properties.
     */
    private void initDefaultProperties() {
        updateNametagText();
        this.properties.setBillboardConstraints(BillboardConstraints.CENTER);
        this.properties.setTranslation(new Vector3f(0.0f, 0.3f, 0.0f));
        this.properties.setHasShadow(true);
        this.properties.setIsSeeThrough(false);
        this.properties.setBackgroundColor(Color.BLACK.setAlpha(0).asARGB());
    }

    public void updateNametagText() {
        this.properties.setText(PlaceholderHook.getInstance().getNametagText(getNametagOwner()));
    }
}
