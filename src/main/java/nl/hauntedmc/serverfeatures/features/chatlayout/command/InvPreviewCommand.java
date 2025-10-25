package nl.hauntedmc.serverfeatures.features.chatlayout.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.api.token.TokenResult;
import nl.hauntedmc.serverfeatures.api.token.TokenService;
import nl.hauntedmc.serverfeatures.api.ui.inventory.preview.inv.InventoryPreviewAPI;
import nl.hauntedmc.serverfeatures.api.ui.inventory.preview.inv.InventorySnapshot;
import nl.hauntedmc.serverfeatures.features.chatlayout.ChatLayout;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Hidden utility command for chat [inv] previews using API TokenService.
 * - Not declared in plugin.yml (so not shown in /help).
 * - Always invoked via ClickEvent.runCommand from chat.
 */
public final class InvPreviewCommand implements BrigadierCommand {

    private static final String NAME = "__sfiv";
    private final ChatLayout feature;

    public InvPreviewCommand(ChatLayout feature) {
        this.feature = feature;
    }

    @Override
    public @NotNull String name() { return NAME; }

    @Override
    public String description() { return ""; }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        TokenService<InventorySnapshot> tokens = feature.getInventoryPreviewTokenService();

        return Commands.literal(name())
                .then(Commands.argument("token", StringArgumentType.word())
                        .executes(ctx -> {
                            CommandSender s = ctx.getSource().getSender();
                            if (!(s instanceof Player p)) return 0;

                            String token = StringArgumentType.getString(ctx, "token");
                            TokenResult<InventorySnapshot> res = tokens.consume(token);

                            switch (res.state()) {
                                case INVALID, EXPIRED -> {
                                    p.sendMessage(feature.getLocalizationHandler()
                                            .getMessage("chatlayout.inventory_preview.expired")
                                            .forAudience(p)
                                            .build());
                                    return 1;
                                }
                                case LOADING -> {
                                    p.sendMessage(feature.getLocalizationHandler()
                                            .getMessage("chatlayout.inventory_preview.loading")
                                            .forAudience(p)
                                            .build());
                                    return 1;
                                }
                                case EMPTY -> {
                                    p.sendMessage(feature.getLocalizationHandler()
                                            .getMessage("chatlayout.inventory_preview.no_inv")
                                            .forAudience(p)
                                            .build());
                                    return 1;
                                }
                                case OK -> {
                                    InventorySnapshot snap = res.payload();
                                    InventoryPreviewAPI.openInventoryPreview(
                                            feature.getPlugin(),
                                            p,
                                            snap,
                                            feature.getLocalizationHandler()
                                                    .getMessage("chatlayout.inventory_preview.title")
                                                    .forAudience(p)
                                                    .with("player", toPossessive(snap.inventoryOwner()))
                                                    .build()
                                    );
                                    return 1;
                                }

                            }
                            return 1;
                        }))
                .build();
    }

    private static String toPossessive(String name) {
        if (name == null || name.isEmpty()) return "";
        char last = name.charAt(name.length() - 1);
        return (last == 's' || last == 'S') ? (name + "'") : (name + "'s");
    }
}
