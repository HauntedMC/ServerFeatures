package nl.hauntedmc.serverfeatures.features.chatlayout;

import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.chatlayout.internal.ChatFormatRegistry;
import nl.hauntedmc.serverfeatures.features.chatlayout.meta.Meta;
import nl.hauntedmc.serverfeatures.features.chatlayout.internal.ChatHandler;
import nl.hauntedmc.serverfeatures.features.chatlayout.listener.ChatListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatLayout extends BukkitBaseFeature<Meta> {

    private ChatHandler chatHandler;
    private ChatFormatRegistry chatFormatRegistry;

    public ChatLayout(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);

        Map<String, Object> defaultFormat = new HashMap<>();
        defaultFormat.put("priority", 100);
        defaultFormat.put("prefix", "&f%vault_rankprefix%");
        defaultFormat.put("name", "&7%serverfeatures_nickname%");
        defaultFormat.put("suffix", "&f: ");
        defaultFormat.put("prefix_tooltip", List.of(
                "&6Rank: %vault_rankprefix%",
                "&6Star Tier: &f<star_tier>",
                "",
                "&7Bekijk de voordelen op",
                "&bwww.hauntedmc.nl/ranks",
                "",
                "&7Bekijk onze winkel op",
                "&astore.hauntedmc.nl",
                "",
                "&eKlik hier voor een link naar de Store."
        ));
        defaultFormat.put("name_tooltip", List.of(
                "&6Naam: &f%player_name%",
                "&bPing: &7%player_ping%ms",
                "",
                "&bKlik om &a%player_name% &bte msg-en."
        ));
        defaultFormat.put("suffix_tooltip", List.of(""));

        defaultFormat.put("prefix_click_command", "/store");
        defaultFormat.put("name_click_command", "/msg %player_name% ");
        defaultFormat.put("suffix_click_command", "");

        Map<String, Object> formats = new HashMap<>();
        formats.put("default", defaultFormat);
        defaults.put("formats", formats);

        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        this.chatFormatRegistry = new ChatFormatRegistry(this);
        this.chatHandler = new ChatHandler(this);
        getLifecycleManager().getListenerManager().registerListener(new ChatListener(this));
    }

    @Override
    public void disable() {
    }

    public ChatHandler getChatHandler() {
        return chatHandler;
    }

    public ChatFormatRegistry getChatFormatRegistry() {
        return chatFormatRegistry;
    }
}
