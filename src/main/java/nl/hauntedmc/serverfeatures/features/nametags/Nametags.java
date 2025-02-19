package nl.hauntedmc.serverfeatures.features.nametags;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.BaseFeature;
import nl.hauntedmc.serverfeatures.common.packet.impl.NametagPacket;
import nl.hauntedmc.serverfeatures.common.packet.PacketManager;
import nl.hauntedmc.serverfeatures.common.scoreboard.ScoreboardManager;
import nl.hauntedmc.serverfeatures.features.nametags.listener.NametagListener;
import nl.hauntedmc.serverfeatures.features.nametags.meta.Meta;
import nl.hauntedmc.serverfeatures.localization.MessageMap;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Nametags extends BaseFeature<Meta> {
    private static final Map<UUID, Integer> entityIdMap = new HashMap<>();

    public Nametags(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public void initialize() {
        getLifecycleManager().registerListener(new NametagListener(this));
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", true);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("nametag.updated", "§aNametag updated!");
        return messages;
    }

    /**
     * Updates a player's nametag:
     * <ul>
     *   <li>Ensures the player is part of the scoreboard team for glow and hidden nametag.</li>
     *   <li>Creates/updates a client-side ArmorStand using a NametagPacket.</li>
     * </ul>
     *
     * @param player The player whose nametag to update.
     */
    public void updateNametag(@NotNull Player player) {
        if (!ScoreboardManager.hasValidTeam(player)) {
            return;
        }

        Component customName = Component.empty();
        if (player.hasPermission("nametag.owner")) {
            customName = customName.append(Component.text("[Owner] ", NamedTextColor.RED));
        }
        customName = customName.append(Component.text(player.getName(), NamedTextColor.GRAY));

        int entityId = entityIdMap.computeIfAbsent(player.getUniqueId(), k -> (int) (Math.random() * Integer.MAX_VALUE));
        NametagPacket nametagPacket = new NametagPacket(player, customName, entityId);
        PacketManager.sendMulticast(player, 50, nametagPacket);
    }

    /**
     * Removes a player’s nametag from the entity tracking map and removes the entity from the client.
     *
     * @param player The player whose nametag should be removed.
     */
    public void removeNametag(@NotNull Player player) {
        int entityId = entityIdMap.remove(player.getUniqueId());
        if (entityId != -1) {
            PacketManager.sendBroadcast(new NametagPacket(player, Component.empty(), entityId));
        }
    }
}