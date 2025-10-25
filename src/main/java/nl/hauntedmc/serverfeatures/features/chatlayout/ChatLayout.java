package nl.hauntedmc.serverfeatures.features.chatlayout;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.api.token.TokenService;
import nl.hauntedmc.serverfeatures.api.ui.inventory.preview.inv.InventorySnapshot;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.chatlayout.command.ChatplaceholdersCommand;
import nl.hauntedmc.serverfeatures.features.chatlayout.command.InvPreviewCommand;
import nl.hauntedmc.serverfeatures.features.chatlayout.command.ItemPreviewCommand;
import nl.hauntedmc.serverfeatures.features.chatlayout.internal.ChatFormatRegistry;
import nl.hauntedmc.serverfeatures.features.chatlayout.internal.ChatHandler;
import nl.hauntedmc.serverfeatures.features.chatlayout.internal.ChatPlaceholderRegistry;
import nl.hauntedmc.serverfeatures.features.chatlayout.listener.PreviewListener;
import nl.hauntedmc.serverfeatures.features.chatlayout.listener.SignedChatListener;
import nl.hauntedmc.serverfeatures.features.chatlayout.meta.Meta;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatLayout extends BukkitBaseFeature<Meta> {

    private ChatHandler chatHandler;

    public ChatLayout(ServerFeatures plugin) {
        super(plugin, new Meta());
    }
    private TokenService<ItemStack> itemPreviewTokens;
    private TokenService<InventorySnapshot> inventoryPreviewTokens;

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);

        defaults.put("mention.enabled", true);
        defaults.put("mention.cooldown_seconds", 60);
        defaults.put("command_suggest.enabled", true);

        // Item preview
        defaults.put("item_preview.enabled", true);
        defaults.put("item_preview.token.max_uses", 100);
        defaults.put("item_preview.token.expire_seconds", 300);

        // Inventory preview
        defaults.put("inventory_preview.enabled", true);
        defaults.put("inventory_preview.token.max_uses", 100);
        defaults.put("inventory_preview.token.expire_seconds", 300);


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

        m.add("chatlayout.placeholders.item.description", "Maak een publieke weergave van een item");
        m.add("chatlayout.placeholders.inv.description", "Maak een publieke weergave van je inventory.");
        m.add("chatlayout.placeholders.command.description", "Maak een klikbare command suggestie.");

        m.add("chatlayout.command.placeholders.header", "&eLijst van alle Chatplaceholders:");
        m.add("chatlayout.command.placeholders.empty", "&7Er zijn geen chatplaceholders geconfigureerd.");
        m.add("chatlayout.command.placeholders.entry", "&f{pos}. &b{placeholder} &f- &7{desc}");
        m.add("chatlayout.command_suggest.hover", "&eKlik om dit commando over te nemen.");
        // Item preview
        m.add("chatlayout.item_preview.hover", "&eKlik om het item te bekijken.");
        m.add("chatlayout.item_preview.title", "&8Item Weergave");
        m.add("chatlayout.item_preview.expired", "&cDeze itemlink is verlopen.");
        m.add("chatlayout.item_preview.loading", "&7Deze itemlink is nog niet klaar... probeer het zo weer.");
        m.add("chatlayout.item_preview.no_item", "&7Er is geen item beschikbaar om te tonen.");

        // Inventory preview
        m.add("chatlayout.inventory_preview.hover", "&eKlik om de inventory te bekijken.");
        m.add("chatlayout.inventory_preview.title", "&8{player} Inventory");
        m.add("chatlayout.inventory_preview.expired", "&cDeze inventorylink is verlopen.");
        m.add("chatlayout.inventory_preview.loading", "&7Deze inventorylink is nog niet klaar... probeer het zo weer.");
        m.add("chatlayout.inventory_preview.no_inv", "&7Er is geen inventory beschikbaar om te tonen.");

        return m;
    }

    @Override
    public void initialize() {
        this.itemPreviewTokens = new TokenService<>("chat.itempreview");
        this.inventoryPreviewTokens = new TokenService<>("chat.inventorypreview");

        ChatFormatRegistry chatFormatRegistry = new ChatFormatRegistry(this);
        ChatPlaceholderRegistry placeholderRegistry = new ChatPlaceholderRegistry(this);
        this.chatHandler = new ChatHandler(this, chatFormatRegistry, placeholderRegistry, itemPreviewTokens, inventoryPreviewTokens);

        getLifecycleManager().getListenerManager().registerListener(new SignedChatListener(this));
        getLifecycleManager().getListenerManager().registerListener(new PreviewListener());

        getLifecycleManager().getCommandManager().registerBrigadierCommand(new ChatplaceholdersCommand(this));
        getLifecycleManager().getCommandManager().registerBrigadierCommand(new ItemPreviewCommand(this));
        getLifecycleManager().getCommandManager().registerBrigadierCommand(new InvPreviewCommand(this));
    }

    @Override
    public void disable() {
    }

    public ChatHandler getChatHandler() {
        return chatHandler;
    }


    public TokenService<ItemStack> getItemPreviewTokenService() {
        return itemPreviewTokens;
    }

    public TokenService<InventorySnapshot> getInventoryPreviewTokenService() {
        return inventoryPreviewTokens;
    }

}
