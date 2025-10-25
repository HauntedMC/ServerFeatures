package nl.hauntedmc.serverfeatures.features.chatlayout.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.api.token.TokenResult;
import nl.hauntedmc.serverfeatures.api.token.TokenService;
import nl.hauntedmc.serverfeatures.api.ui.inventory.preview.ItemPreviewAPI;
import nl.hauntedmc.serverfeatures.features.chatlayout.ChatLayout;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Hidden utility command for chat [item] previews using API TokenService.
 * - Not declared in plugin.yml (so not shown in /help).
 * - Always invoked via ClickEvent.runCommand from chat.
 */
public final class ItemPreviewCommand implements BrigadierCommand {

    private static final String NAME = "__sfip";
    private final ChatLayout feature;

    public ItemPreviewCommand(ChatLayout feature) {
        this.feature = feature;
    }

    @Override
    public @NotNull String name() { return NAME; }

    @Override
    public String description() { return ""; }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        TokenService<ItemStack> tokens = feature.getItemPreviewTokenService();

        return Commands.literal(name())
            .then(Commands.argument("token", StringArgumentType.word())
                .executes(ctx -> {
                    CommandSender s = ctx.getSource().getSender();
                    if (!(s instanceof Player p)) return 0;

                    String token = StringArgumentType.getString(ctx, "token");
                    TokenResult<ItemStack> res = tokens.consume(token);

                    switch (res.state()) {
                        case INVALID, EXPIRED -> {
                            p.sendMessage(feature.getLocalizationHandler()
                                    .getMessage("chatlayout.item_preview.expired")
                                    .forAudience(p)
                                    .build());
                            return 1;
                        }
                        case LOADING -> {
                            p.sendMessage(feature.getLocalizationHandler()
                                    .getMessage("chatlayout.item_preview.loading")
                                    .forAudience(p)
                                    .build());
                            return 1;
                        }
                        case EMPTY -> {
                            p.sendMessage(feature.getLocalizationHandler()
                                    .getMessage("chatlayout.item_preview.no_item")
                                    .forAudience(p)
                                    .build());
                            return 1;
                        }
                        case OK -> {
                            ItemStack snapshot = res.payload();
                            if (ItemPreviewAPI.isShulkerBoxItem(snapshot)) {
                                ItemPreviewAPI.openShulkerPreview(
                                        feature.getPlugin(),
                                        p,
                                        snapshot,
                                        feature.getLocalizationHandler()
                                                .getMessage("chatlayout.item_preview.title")
                                                .forAudience(p)
                                                .build()
                                );
                            } else {
                                ItemPreviewAPI.open3x3Preview(
                                        feature.getPlugin(),
                                        p,
                                        snapshot,
                                        feature.getLocalizationHandler()
                                                .getMessage("chatlayout.item_preview.title")
                                                .forAudience(p)
                                                .build()
                                );
                            }
                            return 1;
                        }
                    }
                    return 1;
                }))
            .build();
    }
}
