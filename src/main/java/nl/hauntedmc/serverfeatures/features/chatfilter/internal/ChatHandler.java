package nl.hauntedmc.serverfeatures.features.chatfilter.internal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.common.util.CastUtils;
import nl.hauntedmc.serverfeatures.features.chatfilter.ChatFilter;
import nl.hauntedmc.serverfeatures.features.chatfilter.internal.services.DiscordService;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatHandler {

    private final ChatFilter feature;
    private final List<String> disallowedWords;
    private final List<String> whitelistedDomains;
    private final Map<UUID, List<String>> recentMessages = new ConcurrentHashMap<>();
    private final LevenshteinDistance levenshtein = new LevenshteinDistance();
    private final DiscordService discordService;

    private final Component prefix = Component.text("[", NamedTextColor.GRAY)
            .append(Component.text("ChatFilter", NamedTextColor.RED))
            .append(Component.text("] ", NamedTextColor.GRAY));

    public ChatHandler(ChatFilter feature) {
        this.feature = feature;
        this.disallowedWords = CastUtils.safeCastToList(feature.getConfigHandler().getSetting("disallowedWords"), String.class);
        this.whitelistedDomains = CastUtils.safeCastToList(feature.getConfigHandler().getSetting("whitelistedDomains"), String.class);
        this.discordService = new DiscordService(feature);
    }

    /**
     * Applies all configured chat filters to the incoming event.
     * If a filter rule matches, the event result is set to "denied" and notifications are sent.
     */
    public boolean applyFilters(Player player, String message) {
        // Bypass filter if the player has permission
        if (player.hasPermission("serverfeatures.feature.chatfilter.bypass")) {
            return false;
        }

        // Apply anti-caps filter
        message = applyAntiCapsFilter(message);

        String normalizedMessage = normalizeMessage(message);

        // Check for disallowed words
        if (containsDisallowedWords(normalizedMessage)) {
            notifySender(player, feature.getLocalizationHandler().getMessage("chatfilter.blocked_word", player));
            notifyStaff(feature.getLocalizationHandler().getMessage("chatfilter.notify_blocked_word", player, Map.of("name", player.getName(), "message", message)));
            logBlockedMessage("[FILTERED] ", message, player);
            discordService.sendFilterNotification(player.getName(), "Taalgebruik", message);
            return true;
        }

        // Check for IP addresses in the message
        if (containsIP(message)) {
            notifySender(player, feature.getLocalizationHandler().getMessage("chatfilter.blocked_ip", player));
            notifyStaff(feature.getLocalizationHandler().getMessage("chatfilter.notify_blocked_ip", player, Map.of("name", player.getName(), "message", message)));
            logBlockedMessage("[IP FILTERED] ", message, player);
            discordService.sendFilterNotification(player.getName(), "Reclame [IP]", message);
            return true;
        }

        // Check for blocked links
        if (containsBlockedLink(message)) {
            notifySender(player, feature.getLocalizationHandler().getMessage("chatfilter.blocked_link", player));
            notifyStaff(feature.getLocalizationHandler().getMessage("chatfilter.notify_blocked_link", player, Map.of("name", player.getName(), "message", message)));
            logBlockedMessage("[LINK FILTERED] ", message, player);
            discordService.sendFilterNotification(player.getName(), "Reclame [Link]", message);
            return true;
        }

        // Check for spam (excessively similar recent messages)
        if (isSpam(normalizedMessage, player)) {
            notifySender(player, feature.getLocalizationHandler().getMessage("chatfilter.blocked_spam", player));
            notifyStaff(feature.getLocalizationHandler().getMessage("chatfilter.notify_blocked_spam", player, Map.of("name", player.getName(), "message", message)));
            logBlockedMessage("[SPAM] ", message, player);
            return true;
        }

        return false;
    }

    private String applyAntiCapsFilter(String message) {
        int minCapsLength = (int) feature.getConfigHandler().getSetting("minCapsLength");
        double maxCapsPercentage = (double) feature.getConfigHandler().getSetting("maxCapsPercentage");

        if (message.length() >= minCapsLength) {
            long capsCount = message.chars().filter(Character::isUpperCase).count();
            double capsPercentage = (capsCount / (double) message.length()) * 100.0;
            if (capsPercentage > maxCapsPercentage) {
                // Convert the entire message to lowercase if it exceeds the caps threshold
                return message.toLowerCase();
            }
        }
        return message;
    }

    private boolean containsBlockedLink(String message) {
        // Detect URLs (e.g., http, https, www, domain.extension)
        String weblinkRegex = "\\b(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}\\b";
        Pattern pattern = Pattern.compile(weblinkRegex);
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String url = matcher.group();
            if (!isWhitelistedDomain(url)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWhitelistedDomain(String url) {
        for (String domain : whitelistedDomains) {
            if (url.contains(domain)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsIP(String message) {
        // Matches IPv4 and IPv6 addresses
        String ipRegex = "(?i)\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b|(?:[a-f0-9]{1,4}:){7}[a-f0-9]{1,4}\\b";
        Pattern pattern = Pattern.compile(ipRegex);
        Matcher matcher = pattern.matcher(message);
        return matcher.find();
    }

    private boolean isSpam(String normalizedMessage, Player player) {
        UUID playerId = player.getUniqueId();
        int maxRecentMessages = (int) feature.getConfigHandler().getSetting("maxRecentMessages");
        double similarityThreshold = (double) feature.getConfigHandler().getSetting("similarityThreshold");

        List<String> recent = recentMessages.computeIfAbsent(playerId, k -> new ArrayList<>());
        if (normalizedMessage.length() <= 6) {
            return false;
        }
        if (!recent.isEmpty()) {
            long similarCount = recent.stream()
                    .filter(m -> calculateSimilarity(normalizedMessage, m) >= similarityThreshold)
                    .count();
            if (similarCount >= 1) {
                return true;
            }
        }
        if (recent.size() >= maxRecentMessages) {
            recent.removeFirst();
        }
        recent.add(normalizedMessage);
        return false;
    }

    private double calculateSimilarity(String s1, String s2) {
        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) {
            return 1.0;
        }
        return 1.0 - (double) levenshtein.apply(s1, s2) / maxLength;
    }

    private boolean containsDisallowedWords(String normalizedMessage) {
        for (String word : disallowedWords) {
            if (matchesPattern(normalizedMessage, word)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPattern(String message, String word) {
        String regex = word.replaceAll("([a-z])", "$1+");
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(message);
        return matcher.find();
    }

    private String normalizeMessage(String message) {
        return message.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private void notifySender(Player sender, Component message) {
        Component notifyMessage = prefix.append(message);
        sender.sendMessage(notifyMessage);
    }

    private void notifyStaff(Component message) {
        Component notifyMessage = prefix.append(message);
        // TODO: Send to other servers as well
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("serverfeatures.feature.chatfilter.notify"))
                .forEach(staff -> staff.sendMessage(notifyMessage));
    }

    private void logBlockedMessage(String tag, String message, Player player) {
        feature.getPlugin().getLogger().info(tag + player.getName()+": "+ message);
    }
}
