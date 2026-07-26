package nl.hauntedmc.serverfeatures.features.chatlayout.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.api.util.text.format.ComponentFormatter;
import nl.hauntedmc.serverfeatures.api.util.text.format.TextFormatter;
import nl.hauntedmc.serverfeatures.features.chatlayout.ChatLayout;
import nl.hauntedmc.serverfeatures.features.chatlayout.internal.ChatPlaceholderRegistry;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class ChatplaceholdersCommand implements BrigadierCommand {

    private static final String P_LIST = "serverfeatures.feature.chatlayout.command.list";
    private final ChatLayout feature;

    // Cached config flags (read once)
    private final boolean itemEnabled;
    private final boolean invEnabled;
    private final boolean cmdSuggestEnabled;

    public ChatplaceholdersCommand(ChatLayout feature) {
        this.feature = feature;

        // Read once from config
        this.itemEnabled = feature.getConfigHandler().get("item_preview.enabled", Boolean.class);
        this.invEnabled = feature.getConfigHandler().get("inventory_preview.enabled", Boolean.class);
        this.cmdSuggestEnabled = feature.getConfigHandler().get("command_suggest.enabled", Boolean.class);
    }

    @Override
    public @NotNull String name() {
        return "chatplaceholders";
    }

    @Override
    public String description() {
        return "Toon alle chatplaceholders.";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name())
                .requires(src -> src.getSender().hasPermission(P_LIST))
                .executes(ctx -> {
                    CommandSender s = ctx.getSource().getSender();

                    ChatPlaceholderRegistry reg = feature.getChatHandler().getPlaceholderRegistry();
                    var entries = reg.getAll();

                    boolean hasAnything = !entries.isEmpty() || itemEnabled || invEnabled || cmdSuggestEnabled;
                    if (!hasAnything) {
                        s.sendMessage(feature.getLocalizationHandler()
                                .getMessage("chatlayout.command.placeholders.empty")
                                .forAudience(s).build());
                        return 1;
                    }

                    // Header
                    s.sendMessage(feature.getLocalizationHandler()
                            .getMessage("chatlayout.command.placeholders.header")
                            .forAudience(s).build());

                    // Template to MiniMessage text
                    String entryTemplateMini = ComponentFormatter.serialize(
                                    feature.getLocalizationHandler()
                                            .getMessage("chatlayout.command.placeholders.entry")
                                            .forAudience(s).build())
                            .format(ComponentFormatter.Serializer.Format.MINIMESSAGE)
                            .build();

                    int i = 1;

                    // 1) Config-defined placeholders
                    for (ChatPlaceholderRegistry.PlaceholderInfo info : entries) {
                        String descMini = ComponentFormatter.serialize(
                                        feature.getLocalizationHandler()
                                                .getMessage("chatlayout.placeholders." + info.key() + ".description")
                                                .forAudience(s).build())
                                .format(ComponentFormatter.Serializer.Format.MINIMESSAGE)
                                .build();

                        String lineMini = entryTemplateMini
                                .replace("{pos}", String.valueOf(i))
                                .replace("{placeholder}", info.token())
                                .replace("{desc}", descMini);

                        s.sendMessage(ComponentFormatter.deserialize(lineMini)
                                .expect(TextFormatter.InputFormat.MINIMESSAGE)
                                .features(ComponentFormatter.ALL_DEFAULTS())
                                .toComponent());
                        i++;
                    }

                    // 2) Built-in special tokens (shown only if enabled; flags cached)
                    if (itemEnabled) {
                        sendEntry(s, entryTemplateMini, i++, "[item]", "chatlayout.placeholders.item.description");
                    }
                    if (invEnabled) {
                        sendEntry(s, entryTemplateMini, i++, "[inv]", "chatlayout.placeholders.inv.description");
                    }
                    if (cmdSuggestEnabled) {
                        sendEntry(s, entryTemplateMini, i++, "[/command]", "chatlayout.placeholders.command.description");
                    }

                    return 1;
                });

        return root.build();
    }

    private void sendEntry(CommandSender s, String templateMini, int pos, String token, String descKey) {
        String descMini = ComponentFormatter.serialize(
                        feature.getLocalizationHandler()
                                .getMessage(descKey)
                                .forAudience(s).build())
                .format(ComponentFormatter.Serializer.Format.MINIMESSAGE)
                .build();

        String lineMini = templateMini
                .replace("{pos}", String.valueOf(pos))
                .replace("{placeholder}", token)
                .replace("{desc}", descMini);

        s.sendMessage(ComponentFormatter.deserialize(lineMini)
                .expect(TextFormatter.InputFormat.MINIMESSAGE)
                .features(ComponentFormatter.ALL_DEFAULTS())
                .toComponent());
    }
}
