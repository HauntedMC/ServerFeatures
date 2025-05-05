package nl.hauntedmc.serverfeatures.features.randomtp;

import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.randomtp.meta.Meta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RandomTP extends BukkitBaseFeature<Meta> {

    public RandomTP(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", true);
        defaults.put("min_x", -18000);
        defaults.put("max_x", 18000);
        defaults.put("min_z", -18000);
        defaults.put("max_z", 18000);
        defaults.put("play_sounds", true);
        defaults.put("show_particles", true);
        defaults.put("console_msg", true);
        defaults.put("disabled_blocks", List.of(
                "LAVA",
                "WATER",
                "LILY_PAD",
                "CACTUS"
        ));
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("randomtp.teleport_msg", "&bJe bent naar een willekeurige plek geteleporteerd. &aOm gerichter een goede plek te vinden, kun je gebruik maken van onze Dynmap: &7www.hauntedmc.nl/dynmap");
        messages.add("randomtp.teleport_position_msg", "&bJe bent naar de gewenste locatie geteleporteerd. &aOm gericht een goede plek te vinden, kun je gebruik maken van onze Dynmap: &7www.hauntedmc.nl/dynmap");
        return messages;
    }

    @Override
    public void initialize() {
    }

    @Override
    public void disable() {}
}
