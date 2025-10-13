package nl.hauntedmc.serverfeatures.features.chatfilter.internal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.api.util.type.CastUtils;
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

        String finalMessage = message;

        // Check for disallowed words
        if (containsDisallowedWords(message)) {
            notifySender(player, feature.getLocalizationHandler().getMessage("chatfilter.blocked_word").forAudience(player).build());
            notifyStaff(feature.getLocalizationHandler().getMessage("chatfilter.notify_blocked_word")
                    .forAudience(player)
                    .with("name", player.getName())
                    .with("message", message)
                    .build());
            logBlockedMessage("[FILTERED] ", message, player);
            feature.getLifecycleManager().getTaskManager().scheduleAsyncTask(() -> discordService.sendFilterNotification(player.getName(), "Taalgebruik", finalMessage));
            return true;
        }

        // Check for IP addresses in the message
        if (containsIP(message)) {
            notifySender(player, feature.getLocalizationHandler().getMessage("chatfilter.blocked_ip").forAudience(player).build());
            notifyStaff(feature.getLocalizationHandler().getMessage("chatfilter.notify_blocked_ip")
                    .forAudience(player)
                    .with("name", player.getName())
                    .with("message", message)
                    .build());
            logBlockedMessage("[IP FILTERED] ", message, player);
            feature.getLifecycleManager().getTaskManager().scheduleAsyncTask(() -> discordService.sendFilterNotification(player.getName(), "Reclame [IP]", finalMessage));
            return true;
        }

        // Check for blocked links
        if (containsBlockedLink(message)) {
            notifySender(player, feature.getLocalizationHandler().getMessage("chatfilter.blocked_link").forAudience(player).build());
            notifyStaff(feature.getLocalizationHandler().getMessage("chatfilter.notify_blocked_link")
                    .forAudience(player)
                    .with("name", player.getName())
                    .with("message", message)
                    .build());
            logBlockedMessage("[LINK FILTERED] ", message, player);
            feature.getLifecycleManager().getTaskManager().scheduleAsyncTask(() -> discordService.sendFilterNotification(player.getName(), "Reclame [Link]", finalMessage));
            return true;
        }

        // Check for spam (excessively similar recent messages)
        if (isSpam(message, player)) {
            notifySender(player, feature.getLocalizationHandler().getMessage("chatfilter.blocked_spam").forAudience(player).build());
            notifyStaff(feature.getLocalizationHandler().getMessage("chatfilter.notify_blocked_spam")
                    .forAudience(player)
                    .with("name", player.getName())
                    .with("message", message)
                    .build());
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

    private boolean containsDisallowedWords(String message) {
        // Lower-case and replace anything that isn’t a letter or digit with a space
        String clean = message.toLowerCase().replaceAll("[^a-z0-9]", " ");
        String[] tokens = clean.trim().split("\\s+");

        for (String raw : disallowedWords) {
            // Normalize the banned word too
            String word = raw.toLowerCase().replaceAll("[^a-z0-9]", "");
            int wlen = word.length();

            // Span matching (standalone + split across tokens)
            for (int span = 1; span <= wlen; span++) {
                for (int i = 0; i + span <= tokens.length; i++) {
                    // Skip if any token in this span is longer than the banned word
                    boolean tooLong = false;
                    for (int j = 0; j < span; j++) {
                        if (tokens[i + j].length() > wlen) {
                            tooLong = true;
                            break;
                        }
                    }
                    if (tooLong) continue;

                    // Concatenate tokens[i]…tokens[i+span-1]
                    StringBuilder sb = new StringBuilder(wlen);
                    for (int j = 0; j < span; j++) {
                        sb.append(tokens[i + j]);
                    }

                    if (sb.toString().equals(word)) {
                        return true;
                    }
                }
            }

            // Prefix/suffix matching on individual tokens
            for (String token : tokens) {
                if (token.length() > wlen) {
                    if (token.startsWith(word) || token.endsWith(word)) {
                        return true;
                    }
                }
            }
        }

        return false;
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
        feature.getLogger().info(tag + player.getName() + ": " + message);
    }
}
