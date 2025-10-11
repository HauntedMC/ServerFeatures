package nl.hauntedmc.serverfeatures.features.chatlayout;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.chatlayout.internal.ChatFormatRegistry;
import nl.hauntedmc.serverfeatures.features.chatlayout.internal.ChatHandler;
import nl.hauntedmc.serverfeatures.features.chatlayout.listener.SignedChatListener;
import nl.hauntedmc.serverfeatures.features.chatlayout.meta.Meta;

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
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
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
                "&eKlik voor een link",
                "&enaar de Store."
        ));
        defaultFormat.put("name_tooltip", List.of(
                "&bUsername: &7%player_name%",
                "&bPing: &7%player_ping%ms",
                "",
                "&eKlik om &a%player_name% &eeen",
                "&ebericht te sturen."
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
        // You can localize the remove buttons later if you want.
        // For now the listener renders the buttons directly.
        return new MessageMap();
    }

    @Override
    public void initialize() {
        this.chatFormatRegistry = new ChatFormatRegistry(this);
        this.chatHandler = new ChatHandler(this);
        getLifecycleManager().getListenerManager().registerListener(new SignedChatListener(this));
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
