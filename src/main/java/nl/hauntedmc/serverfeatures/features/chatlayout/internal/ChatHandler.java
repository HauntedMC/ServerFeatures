package nl.hauntedmc.serverfeatures.features.chatlayout.internal;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.api.effect.sound.SoundProfile;
import nl.hauntedmc.serverfeatures.api.hook.PlaceholderAPIHook;
import nl.hauntedmc.serverfeatures.api.token.TokenOptions;
import nl.hauntedmc.serverfeatures.api.token.TokenService;
import nl.hauntedmc.serverfeatures.api.ui.hud.toast.ToastAPI;
import nl.hauntedmc.serverfeatures.api.ui.inventory.preview.inv.InventorySnapshot;
import nl.hauntedmc.serverfeatures.api.util.text.format.ComponentFormatter;
import nl.hauntedmc.serverfeatures.api.util.text.format.TextFormatter;
import nl.hauntedmc.serverfeatures.features.chatlayout.ChatLayout;
import nl.hauntedmc.serverfeatures.features.chatlayout.internal.util.StarTierModifier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class ChatHandler {

    private final ChatFormatRegistry registry;
    private final Map<Player, Long> mentionCooldownMap = new HashMap<>();
    private final ChatPlaceholderRegistry placeholderRegistry;
    private final Boolean mentionsEnabled;
    private final Boolean commandSuggestEnabled;
    private final Boolean itemPreviewEnabled;
    private final Boolean inventoryPreviewEnabled;
    private final Long mentionsCooldown;
    private final ChatLayout feature;

    private final TokenService<ItemStack> itemTokenService;
    private final int itemTokenMaxUses;
    private final long itemTokenExpireSeconds;

    private final TokenService<InventorySnapshot> invTokenService;
    private final int invTokenMaxUses;
    private final long invTokenExpireSeconds;

    private static final Pattern COMMAND_BRACKET_PATTERN = Pattern.compile("\\[\\s*(/\\S[^]]*)\\s*]");
    private static final Pattern MENTION_PATTERN = Pattern.compile("(?<!\\S)@([A-Za-z0-9_]{3,16})\\b");
    private static final Pattern ITEM_TOKEN_RAW_PATTERN = Pattern.compile("\\[item]", Pattern.CASE_INSENSITIVE);
    private static final Pattern INV_TOKEN_RAW_PATTERN = Pattern.compile("\\[inv]", Pattern.CASE_INSENSITIVE);

    private static final String HIDDEN_PREVIEW_COMMAND = "__sfip";
    private static final String HIDDEN_INV_PREVIEW_COMMAND = "__sfiv";


    public ChatHandler(
            ChatLayout feature,
            ChatFormatRegistry registry,
            ChatPlaceholderRegistry placeholderRegistry,
            TokenService<ItemStack> itemTokenService,
            TokenService<InventorySnapshot> invTokenService
    ) {

        this.feature = feature;
        this.registry = registry;
        this.placeholderRegistry = placeholderRegistry;

        this.itemTokenService = itemTokenService;
        this.invTokenService = invTokenService;

        this.itemPreviewEnabled = feature.getConfigHandler().getSetting("item_preview.enabled", Boolean.class);
        this.inventoryPreviewEnabled = feature.getConfigHandler().getSetting("inventory_preview.enabled", Boolean.class);
        this.mentionsEnabled = feature.getConfigHandler().getSetting("mention.enabled", Boolean.class);
        this.mentionsCooldown = feature.getConfigHandler().getSetting("mention.cooldown_seconds", Long.class);
        this.commandSuggestEnabled = feature.getConfigHandler().getSetting("command_suggest.enabled", Boolean.class);

        this.itemTokenMaxUses = feature.getConfigHandler().getSetting("item_preview.token.max_uses", Integer.class);
        this.itemTokenExpireSeconds = feature.getConfigHandler().getSetting("item_preview.token.expire_seconds", Long.class);

        this.invTokenMaxUses = feature.getConfigHandler().getSetting("inventory_preview.token.max_uses", Integer.class);
        this.invTokenExpireSeconds = feature.getConfigHandler().getSetting("inventory_preview.token.expire_seconds", Long.class);
    }

    /**
     * Builds the full rendered message:
     * [prefix + name + suffix] + [formatted chat message]
     */
    public Component renderBaseMessage(Player sender, Audience viewer, Component messageComponent) {
        // 1) Get the raw plain text (so we can re-interpret formatting per perms)
        String raw = ComponentFormatter.serialize(messageComponent)
                .format(ComponentFormatter.Serializer.Format.PLAIN)
                .build();

        // 2) Parse UNTRUSTED user formatting -> Component (NO click/hover allowed)
        Component chatBase = buildChatComponent(sender, raw);

        // 3) Inject TRUSTED features at Component-
        chatBase = placeholderRegistry.applyPlaceholders(sender, viewer, chatBase);
        if (mentionsEnabled) chatBase = applyMentions(sender, chatBase);
        if (commandSuggestEnabled) chatBase = applyCommandSuggestPlaceholders(viewer, chatBase);
        if (itemPreviewEnabled) chatBase = applyItemPreviewPlaceholder(sender, viewer, chatBase);
        if (inventoryPreviewEnabled) chatBase = applyInventoryPreviewPlaceholder(sender, viewer, chatBase);

        // 4) Prefix (server-controlled; you can keep using MiniMessage here)
        Component prefix = buildPrefixComponent(sender);

        return prefix.append(chatBase);
    }

    private Component applyCommandSuggestPlaceholders(Audience viewer, Component base) {
        return base.replaceText(builder -> builder
                .match(COMMAND_BRACKET_PATTERN)
                .replacement((match, unused) -> {
                    String cmd = match.group(1).trim(); // e.g., "/test arg1 arg2"
                    Component hover = feature.getLocalizationHandler()
                            .getMessage("chatlayout.command_suggest.hover")
                            .forAudience(viewer)
                            .build();

                    return Component.text("[")
                            .color(NamedTextColor.WHITE)
                            .append(Component.text(cmd)
                                    .color(NamedTextColor.YELLOW)
                                    .hoverEvent(HoverEvent.showText(hover))
                                    .clickEvent(ClickEvent.suggestCommand(cmd)))
                            .append(Component.text("]")
                                    .color(NamedTextColor.WHITE));
                })
        );
    }


    private Component applyMentions(Player sender, Component base) {
        return base.replaceText(builder -> builder
                .match(MENTION_PATTERN)
                .replacement((match, unused) -> {
                    String full = match.group(0);
                    String name = match.group(1);
                    Player mentioned = Bukkit.getPlayerExact(name);

                    if (mentioned != null && mentioned.isOnline()) {
                        // toast + cooldown (same logic as before)
                        handleMention(sender, mentioned);

                        return Component.text(full).color(net.kyori.adventure.text.format.NamedTextColor.AQUA);
                    }

                    // Not online? Just return the plain text mention.
                    return Component.text(full);
                })
        );
    }

    private Component applyItemPreviewPlaceholder(Player sender, Audience viewer, Component base) {
        final var hover = feature.getLocalizationHandler()
                .getMessage("chatlayout.item_preview.hover")
                .forAudience(viewer)
                .build();

        // Snapshot taken immediately (like your original code)
        ItemStack inHand = sender.getInventory().getItemInMainHand();
        final ItemStack snapshot = inHand.getType().isAir() ? null : inHand.clone();

        // Token options (-1 => infinite)
        TokenOptions opts = TokenOptions.builder()
                .maxUses(itemTokenMaxUses)
                .expireAfter(itemTokenExpireSeconds < 0 ? null : Duration.ofSeconds(itemTokenExpireSeconds))
                .consumeOnEmpty(true)
                .build();

        // Token payload is exactly this snapshot (no loading state)
        final String token = itemTokenService.create(
                () -> CompletableFuture.completedFuture(snapshot == null ? null : snapshot.clone()),
                opts
        );

        return base.replaceText(cfg -> cfg
                .match(ITEM_TOKEN_RAW_PATTERN)
                .replacement((match, unused) -> {
                    if (snapshot == null) {
                        // Show a clear label; still clickable to emit "no_item" message from the command
                        return Component.text("[Air x1]")
                                .hoverEvent(HoverEvent.showText(hover))
                                .clickEvent(ClickEvent.runCommand("/" + HIDDEN_PREVIEW_COMMAND + " " + token));
                    }

                    int amount = Math.max(1, snapshot.getAmount());
                    Component itemName = nl.hauntedmc.serverfeatures.api.util.bukkit.ItemStacks.bestDisplayName(snapshot);

                    return Component.text("[")
                            .append(itemName)
                            .append(Component.text(" x" + amount))
                            .append(Component.text("]"))
                            .hoverEvent(HoverEvent.showText(hover))
                            .clickEvent(ClickEvent.runCommand("/" + HIDDEN_PREVIEW_COMMAND + " " + token));
                })
        );
    }

    /**
     * Send a toast message to the mentioned player with cooldown.
     */
    private void handleMention(Player sender, Player mentionedPlayer) {
        // Check cooldown for the mentioned player
        long lastMentionTime = mentionCooldownMap.getOrDefault(mentionedPlayer, 0L);
        long currentTime = System.currentTimeMillis();
        long cooldownInMillis = this.mentionsCooldown * 1000;

        if (currentTime - lastMentionTime >= cooldownInMillis) {
            Component toastMessage = feature.getLocalizationHandler().getMessage("chatlayout.mention.toast_title")
                    .with("player", sender.getName())
                    .forAudience(mentionedPlayer)
                    .build();

            ToastAPI.showToast(
                    feature.getPlugin(),
                    mentionedPlayer,
                    ComponentFormatter.serialize(toastMessage).format(ComponentFormatter.Serializer.Format.JSON).build(),
                    Material.BELL,
                    ToastAPI.Frame.GOAL,
                    40L,
                    SoundProfile.of(Sound.BLOCK_NOTE_BLOCK_PLING,
                            SoundCategory.UI,
                            0.8f,
                            2.2f)
            );

            mentionCooldownMap.put(mentionedPlayer, currentTime);
        }
    }

    private Component applyInventoryPreviewPlaceholder(Player sender, Audience viewer, Component base) {
        final var hover = feature.getLocalizationHandler()
                .getMessage("chatlayout.inventory_preview.hover")
                .forAudience(viewer)
                .build();

        // Snapshot of the entire inventory
        final InventorySnapshot snap = InventorySnapshot.from(sender);

        TokenOptions opts = TokenOptions.builder()
                .maxUses(invTokenMaxUses)
                .expireAfter(invTokenExpireSeconds < 0 ? null : Duration.ofSeconds(invTokenExpireSeconds))
                .consumeOnEmpty(true)
                .build();

        final String token = invTokenService.create(
                () -> CompletableFuture.completedFuture(snap),
                opts
        );

        // Possessive label: "Alex's inventory" vs "Lucas' inventory"
        final String name = sender.getName();
        final String possessive = (name.endsWith("s") || name.endsWith("S")) ? (name + "'") : (name + "'s");

        // [ darker gray ] + gray text
        final Component label = Component.empty()
                .append(Component.text("[").color(NamedTextColor.WHITE))
                .append(Component.text(possessive + " inventory").color(NamedTextColor.GOLD))
                .append(Component.text("]").color(NamedTextColor.WHITE));

        return base.replaceText(cfg -> cfg
                .match(INV_TOKEN_RAW_PATTERN)
                .replacement((match, unused) ->
                        label.hoverEvent(HoverEvent.showText(hover))
                                .clickEvent(ClickEvent.runCommand("/" + HIDDEN_INV_PREVIEW_COMMAND + " " + token)))
        );
    }


    private Component buildPrefixComponent(Player player) {
        ChatFormat playerFormat = registry.getPlayerFormat(player);

        // Stars
        int starTier = StarTierModifier.getStarTier(player);
        String starTierFormat = StarTierModifier.getStarTierFormat(starTier);

        // Prefix
        String rank = playerFormat.getPrefix();
        String prefix = starTierFormat + rank;
        String prefixCommand = playerFormat.getPreClickCmd();
        String prefixTooltip = listToString(playerFormat.getPrefixTooltip()).replace("<star_tier>", String.valueOf(starTier));
        String processed_prefix = "<click:run_command:'" + prefixCommand + "'><hover:show_text:'" + prefixTooltip + "'>" + prefix + "</hover></click>";

        // Name
        String name = playerFormat.getName();
        String nameCommand = playerFormat.getNameClickCmd();
        String nameTooltip = listToString(playerFormat.getNameTooltip());
        String processed_name = "<click:suggest_command:'" + nameCommand + "'><hover:show_text:'" + nameTooltip + "'>" + name + "</hover></click>";

        // Suffix
        String suffix = playerFormat.getSuffix();

        // Combine
        String chatLayout = processed_prefix + processed_name + suffix;

        // Parse
        chatLayout = TextFormatter.convert(chatLayout)
                .expect(TextFormatter.InputFormat.ANY)
                .preprocess(s -> {
                    s = PlaceholderAPIHook.applyPlaceholders(s, player);
                    return s;
                })
                .toMiniMessage();

        return ComponentFormatter.deserialize(chatLayout)
                .expect(TextFormatter.InputFormat.MINIMESSAGE)
                .features(ComponentFormatter.Feature.CLICK,
                        ComponentFormatter.Feature.HOVER,
                        ComponentFormatter.Feature.COLORS,
                        ComponentFormatter.Feature.DECORATIONS,
                        ComponentFormatter.Feature.RESET,
                        ComponentFormatter.Feature.GRADIENT)
                .toComponent();
    }

    /**
     * Chat message component (colors/formatting/url handling gated by perms).
     */
    private Component buildChatComponent(Player player, String rawMessage) {

        // Admin permissions for chat formatting
        if (player.hasPermission("deluxechat.interactive")) {
            return ComponentFormatter.deserialize(rawMessage)
                    .expect(TextFormatter.InputFormat.MIXED_INPUT)
                    .features(ComponentFormatter.Feature.CLICK,
                            ComponentFormatter.Feature.HOVER,
                            ComponentFormatter.Feature.COLORS,
                            ComponentFormatter.Feature.DECORATIONS,
                            ComponentFormatter.Feature.RESET,
                            ComponentFormatter.Feature.GRADIENT,
                            ComponentFormatter.Feature.RAINBOW)
                    .autoLinkUrls(true)
                    .toComponent();
            // Formatting permissions for chat formatting with colors and decorations
        } else if (player.hasPermission("deluxechat.formatting")) {
            return ComponentFormatter.deserialize(rawMessage)
                    .expect(TextFormatter.InputFormat.MIXED_INPUT)
                    .features(ComponentFormatter.Feature.COLORS,
                            ComponentFormatter.Feature.DECORATIONS,
                            ComponentFormatter.Feature.RESET,
                            ComponentFormatter.Feature.GRADIENT,
                            ComponentFormatter.Feature.RAINBOW)
                    .autoLinkUrls(true)
                    .toComponent();
            // Formatting permissions for chat formatting with colors only
        } else if (player.hasPermission("deluxechat.color")) {
            return ComponentFormatter.deserialize(rawMessage)
                    .expect(TextFormatter.InputFormat.MIXED_INPUT)
                    .features(ComponentFormatter.Feature.COLORS,
                            ComponentFormatter.Feature.RESET,
                            ComponentFormatter.Feature.GRADIENT,
                            ComponentFormatter.Feature.RAINBOW)
                    .autoLinkUrls(true)
                    .toComponent();
            // No formatting permissions, only url auto-linking
        } else {
            return ComponentFormatter.deserialize(rawMessage)
                    .expect(TextFormatter.InputFormat.MIXED_INPUT)
                    .autoLinkUrls(true)
                    .toComponent();
        }
    }

    private String listToString(List<String> strings) {
        StringBuilder sb = new StringBuilder();
        int length = strings.size();
        int index = 0;
        for (String str : strings) {
            sb.append(str);
            if (index < length - 1) {
                sb.append("\n<reset>");
            }
            index++;
        }
        return sb.toString();
    }

    public ChatPlaceholderRegistry getPlaceholderRegistry() {
        return placeholderRegistry;
    }
}
