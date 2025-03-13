package nl.hauntedmc.serverfeatures.features.chatfilter;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BaseFeature;
import nl.hauntedmc.serverfeatures.features.chatfilter.internal.ChatHandler;
import nl.hauntedmc.serverfeatures.features.chatfilter.listener.ChatListener;
import nl.hauntedmc.serverfeatures.features.chatfilter.meta.Meta;
import nl.hauntedmc.serverfeatures.localization.MessageMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatFilter extends BaseFeature<Meta> {

    private ChatHandler chatHandler;

    public ChatFilter(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);
        // Anti-caps settings
        defaults.put("minCapsLength", 10);
        defaults.put("maxCapsPercentage", 20.0);
        // Word and link filtering
        // Whitelisted domains
        defaults.put("whitelistedDomains", List.of(
                "minecraft.net",
                "mojang.com",
                "curseforge.com",
                "planetminecraft.com",
                "minecraftforum.net",
                "spigotmc.org",
                "bukkit.org",
                "topg.org",
                "minecraftservers.org",
                "twitter.com",
                "youtube.com",
                "wikipedia.org",
                "minecraft.fandom.com",
                "minecraft.gamepedia.com",
                "minecraftwiki.net",
                "mcfandom.wikia.com",
                "twitch.tv",
                "netflix.com",
                "hulu.com",
                "disneyplus.com",
                "primevideo.com",
                "hauntedmc.nl"
        ));

        // Disallowed words
        defaults.put("disallowedWords", List.of(
                "kut",
                "godverdomme",
                "kanker",
                "tering",
                "tyfus",
                "hoer",
                "neger",
                "wanker",
                "flikker",
                "klootzak",
                "eikel",
                "fuck",
                "debiel",
                "sukkel",
                "seks",
                "neuk",
                "rotzak",
                "nigger",
                "fisting",
                "borsten",
                "vagina",
                "condoom",
                "cock",
                "suck",
                "geil",
                "blowjob",
                "anaal",
                "tieten",
                "racist",
                "nazi",
                "hitler",
                "pedo",
                "sperma",
                "bitch",
                "bastard",
                "cunt",
                "dildo",
                "retard",
                "mongool",
                "porn",
                "slut",
                "nigga",
                "anus",
                "milf",
                "testicle",
                "cancer",
                "k4nker",
                "knkr",
                "tiefes",
                "tiefus",
                "tyfes",
                "teef",
                "hentai",
                "boob",
                "pussy",
                "whore"
        ));

        // Spam filtering
        defaults.put("maxRecentMessages", 1);
        defaults.put("similarityThreshold", 0.95);
        defaults.put("server", "default_server");
        defaults.put("discordWebhookURL", "https://discordhook.url");
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messageMap = new MessageMap();
        messageMap.add("chatfilter.blocked_word", "&fJouw bericht bevat een niet toegestaan woord en is geblokkeerd.");
        messageMap.add("chatfilter.blocked_ip", "&fIP-adressen zijn niet toegestaan in de chat.");
        messageMap.add("chatfilter.blocked_link", "&fDeze weblink is niet toegestaan in de chat.");
        messageMap.add("chatfilter.blocked_spam", "&fJe bericht lijkt op spam en is geblokkeerd.");
        messageMap.add("chatfilter.notify_blocked_word", "&f{name} is geblokkeerd door het chatfilter: \"{message}\"");
        messageMap.add("chatfilter.notify_blocked_ip", "&f{name} is geblokkeerd door het IP filter: \"{message}\"");
        messageMap.add("chatfilter.notify_blocked_link", "&f{name} is geblokkeerd door het weblink: \"{message}\"");
        messageMap.add("chatfilter.notify_blocked_spam", "&f{name} is geblokkeerd door het spamfilter: \"{message}\"");
        return messageMap;
    }

    @Override
    public void initialize() {
        this.chatHandler = new ChatHandler(this);
        getLifecycleManager().getListenerManager().registerListener(new ChatListener(this));
    }

    @Override
    public void disable() {

    }

    public ChatHandler getChatHandler() {
        return chatHandler;
    }
}
