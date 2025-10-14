package nl.hauntedmc.serverfeatures.features.actionbar.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import nl.hauntedmc.serverfeatures.api.command.brigadier.FeatureBrigadierCommand;
import nl.hauntedmc.serverfeatures.features.actionbar.Actionbar;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Brigadier version of /actionbar for side-by-side testing.
 * Root literal uses a non-conflicting name: /actionbar_brig
 * Subcommands:
 *   - /actionbar_brig start
 *   - /actionbar_brig stop
 *   - /actionbar_brig send <seconds:0..3600> <message...>
 *   - /actionbar_brig send <message...>   (seconds optional; defaults to 0)
 * Features:
 *   - .requires(...) to hide branches without permission
 *   - typed IntegerArgumentType with bounds for live validation
 *   - suggestion tooltips for seconds and for the message placeholder
 */
public final class ActionbarBrigadierCommand implements FeatureBrigadierCommand {
    private static final String BASE = "serverfeatures.feature.actionbar.use";
    private static final String P_START = "serverfeatures.feature.actionbar.command.start";
    private static final String P_STOP  = "serverfeatures.feature.actionbar.command.stop";
    private static final String P_SEND  = "serverfeatures.feature.actionbar.command.send";

    private final Actionbar feature;

    public ActionbarBrigadierCommand(Actionbar feature) {
        this.feature = feature;
    }

    @Override
    public @NotNull String name() { return "actionbarbrig"; } // distinct root while testing

    @Override
    public String description() { return "Actionbar (Brigadier test)"; }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        final var mm  = MiniMessage.miniMessage();
        final var ser = MessageComponentSerializer.message();

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name())
            .requires(src -> src.getSender().hasPermission(BASE))

            // /actionbar_brig start
            .then(Commands.literal("start")
                .requires(src -> src.getSender().hasPermission(P_START))
                .executes(ctx -> {
                    CommandSender s = ctx.getSource().getSender();
                    if (feature.getActionbarHandler().messageCycleRunning()) {
                        s.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.already_running").forAudience(s).build());
                        return 1;
                    }
                    feature.getActionbarHandler().startMessageCycle();
                    s.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.started").forAudience(s).build());
                    return 1;
                }))

            // /actionbar_brig stop
            .then(Commands.literal("stop")
                .requires(src -> src.getSender().hasPermission(P_STOP))
                .executes(ctx -> {
                    CommandSender s = ctx.getSource().getSender();
                    if (!feature.getActionbarHandler().messageCycleRunning()) {
                        s.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.not_running").forAudience(s).build());
                        return 1;
                    }
                    feature.getActionbarHandler().stopMessageCycle();
                    s.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.stopped").forAudience(s).build());
                    return 1;
                }))

            // /actionbar_brig send ...
            .then(Commands.literal("send")
                    .requires(src -> src.getSender().hasPermission(P_SEND))

                    // Variant A (primary): /actionbarbrig send <seconds 0..3600> <message...>
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                            .suggests((c, b) -> {
                                suggestSeconds(b, 0, 3, 5, 10, 30, 60, 120, 300);
                                return b.buildFuture();
                            })
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .suggests((c, b) -> {
                                        if (b.getRemaining().isEmpty()) {
                                            b.suggest("<message...>", MessageComponentSerializer.message()
                                                    .serialize(MiniMessage.miniMessage().deserialize("<gray>Type the actionbar text</gray>")));
                                        }
                                        return b.buildFuture();
                                    })
                                    .executes(ctx -> {
                                        int secs = IntegerArgumentType.getInteger(ctx, "seconds");
                                        String msg = StringArgumentType.getString(ctx, "message");
                                        feature.getActionbarHandler().sendManualActionbar(msg, secs);

                                        CommandSender s = ctx.getSource().getSender();
                                        if (secs > 0) {
                                            s.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.sent_timer")
                                                    .forAudience(s).with("message", msg).with("time", secs).build());
                                        } else {
                                            s.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.sent_once")
                                                    .forAudience(s).with("message", msg).build());
                                        }
                                        return 1;
                                    })
                            )
                    )
            );

        return root.build();
    }

    private static void suggestSeconds(SuggestionsBuilder b, int... vals) {
        final var mm  = MiniMessage.miniMessage();
        final var ser = MessageComponentSerializer.message();
        String prefix = b.getRemainingLowerCase();
        for (int v : vals) {
            String s = String.valueOf(v);
            if (!s.startsWith(prefix)) continue;
            String tip = v == 0 ? "<gray>Show once (no timer)</gray>"
                                : "<gray>Show for <green>"+v+"</green> seconds</gray>";
            b.suggest(v, ser.serialize(mm.deserialize(tip)));
        }
    }
}
