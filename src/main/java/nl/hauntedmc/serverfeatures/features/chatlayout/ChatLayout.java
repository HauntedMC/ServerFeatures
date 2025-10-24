package nl.hauntedmc.serverfeatures.features.chatlayout;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.chatlayout.command.ChatplaceholdersCommand;
import nl.hauntedmc.serverfeatures.features.chatlayout.internal.ChatFormatRegistry;
import nl.hauntedmc.serverfeatures.features.chatlayout.internal.ChatHandler;
import nl.hauntedmc.serverfeatures.features.chatlayout.internal.ChatPlaceholderRegistry;
import nl.hauntedmc.serverfeatures.features.chatlayout.listener.SignedChatListener;
import nl.hauntedmc.serverfeatures.features.chatlayout.meta.Meta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatLayout extends BukkitBaseFeature<Meta> {

    private ChatHandler chatHandler;

    public ChatLayout(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);

        defaults.put("mention.enabled", true);
        defaults.put("mention.cooldown_seconds", 60);
        defaults.put("command_suggest.enabled", true);

        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put("ping", "[ping]");
        defaults.put("placeholders", placeholders);


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
        MessageMap m = new MessageMap();
        m.add("chatlayout.mention.toast_title", "&fJe bent getagged door &e{player}&f!");
        m.add("chatlayout.placeholders.hover", "&a&l✓ &rGeverifieerd bericht");
        m.add("chatlayout.placeholders.ping.replacetext", "&fMijn ping is %player_colored_ping% ms&f.");
        m.add("chatlayout.placeholders.ping.description", "Laat je ping zien.");
        m.add("chatlayout.command.placeholders.header", "&eLijst van alle Chatplaceholders:");
        m.add("chatlayout.command.placeholders.empty", "&7Er zijn geen chatplaceholders geconfigureerd.");
        m.add("chatlayout.command.placeholders.entry", "&f{pos}. &b{placeholder} &f- &7{desc}");
        m.add("chatlayout.command_suggest.hover", "&eKlik om dit commando over te nemen.");
        return m;
    }

    @Override
    public void initialize() {
        ChatFormatRegistry chatFormatRegistry = new ChatFormatRegistry(this);
        ChatPlaceholderRegistry placeholderRegistry = new ChatPlaceholderRegistry(this);
        this.chatHandler = new ChatHandler(this, chatFormatRegistry, placeholderRegistry);
        getLifecycleManager().getListenerManager().registerListener(new SignedChatListener(this));
        getLifecycleManager().getCommandManager().registerBrigadierCommand(new ChatplaceholdersCommand(this));
    }

    @Override
    public void disable() {
    }

    public ChatHandler getChatHandler() {
        return chatHandler;
    }

}
