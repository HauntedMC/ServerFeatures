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

    public ChatplaceholdersCommand(ChatLayout feature) {
        this.feature = feature;
    }

    @Override
    public @NotNull String name() { return "chatplaceholders"; }

    @Override
    public String description() { return "Toon alle chatplaceholders."; }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name())
                .requires(src -> src.getSender().hasPermission(P_LIST))
                .executes(ctx -> {
                    CommandSender s = ctx.getSource().getSender();

                    ChatPlaceholderRegistry reg = feature.getChatHandler().getPlaceholderRegistry();
                    var entries = reg.getAll();

                    if (entries.isEmpty()) {
                        s.sendMessage(feature.getLocalizationHandler()
                                .getMessage("chatlayout.command.placeholders.empty")
                                .forAudience(s).build());
                        return 1;
                    }

                    // Header
                    s.sendMessage(feature.getLocalizationHandler()
                            .getMessage("chatlayout.command.placeholders.header")
                            .forAudience(s).build());

                    // Pre-render the entry template to MiniMessage text, then substitute tokens.
                    String entryTemplateMini = ComponentFormatter.serialize(
                                    feature.getLocalizationHandler()
                                            .getMessage("chatlayout.command.placeholders.entry")
                                            .forAudience(s).build())
                            .format(ComponentFormatter.Serializer.Format.MINIMESSAGE)
                            .build();

                    int i = 1;
                    for (ChatPlaceholderRegistry.PlaceholderInfo info : entries) {
                        // Localized description -> MiniMessage text
                        String descMini = ComponentFormatter.serialize(
                                        feature.getLocalizationHandler()
                                                .getMessage("chatlayout.placeholders." + info.key() + ".description")
                                                .forAudience(s).build())
                                .format(ComponentFormatter.Serializer.Format.MINIMESSAGE)
                                .build();

                        // Fill the template
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
                    return 1;
                });

        return root.build();
    }
}
