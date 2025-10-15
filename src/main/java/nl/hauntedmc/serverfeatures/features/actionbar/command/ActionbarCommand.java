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
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.api.util.text.format.ComponentFormatter;
import nl.hauntedmc.serverfeatures.api.util.text.format.TextFormatter;
import nl.hauntedmc.serverfeatures.features.actionbar.Actionbar;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class ActionbarCommand implements BrigadierCommand {
    private static final String BASE = "serverfeatures.feature.actionbar.use";
    private static final String P_START = "serverfeatures.feature.actionbar.command.start";
    private static final String P_STOP = "serverfeatures.feature.actionbar.command.stop";
    private static final String P_SEND = "serverfeatures.feature.actionbar.command.send";

    private final Actionbar feature;

    public ActionbarCommand(Actionbar feature) {
        this.feature = feature;
    }

    @Override
    public @NotNull String name() {
        return "actionbar";
    }

    @Override
    public String description() {
        return "Control the actionbar cycle and send messages.";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name())
                .requires(src -> src.getSender().hasPermission(BASE))

                .then(Commands.literal("start")
                        .requires(src -> src.getSender().hasPermission(P_START))
                        .executes(ctx -> {
                            CommandSender s = ctx.getSource().getSender();
                            if (feature.service().isCycleRunning()) {
                                s.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.already_running").forAudience(s).build());
                                return 1;
                            }
                            feature.service().startCycle();
                            s.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.started").forAudience(s).build());
                            return 1;
                        }))

                .then(Commands.literal("stop")
                        .requires(src -> src.getSender().hasPermission(P_STOP))
                        .executes(ctx -> {
                            CommandSender s = ctx.getSource().getSender();
                            if (!feature.service().isCycleRunning()) {
                                s.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.not_running").forAudience(s).build());
                                return 1;
                            }
                            feature.service().stopCycle();
                            s.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.stopped").forAudience(s).build());
                            return 1;
                        }))

                .then(Commands.literal("send")
                        .requires(src -> src.getSender().hasPermission(P_SEND))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                                .suggests((c, b) -> {
                                    suggestSeconds(b, 0, 3, 5, 10, 30, 60, 120, 300);
                                    return b.buildFuture();
                                })
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .suggests((c, b) -> {
                                            if (b.getRemaining().isEmpty()) {
                                                b.suggest("<message...>", ComponentFormatter.deserialize("<gray>Type the actionbar text</gray>")
                                                        .expect(TextFormatter.InputFormat.MINIMESSAGE)
                                                        .features(ComponentFormatter.ALL_DEFAULTS()).toBrigadierMessage());
                                            }
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            int secs = IntegerArgumentType.getInteger(ctx, "seconds");
                                            String msg = StringArgumentType.getString(ctx, "message");
                                            feature.service().sendManual(msg, secs);

                                            CommandSender s = ctx.getSource().getSender();
                                            if (secs > 0) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.sent_timer")
                                                        .forAudience(s).with("time", secs).with("message", msg).build());
                                            } else {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.sent_once")
                                                        .forAudience(s).with("message", msg).build());
                                            }
                                            return 1;
                                        }))));

        return root.build();
    }

    private static void suggestSeconds(SuggestionsBuilder b, int... vals) {
        String prefix = b.getRemainingLowerCase();
        for (int v : vals) {
            String s = String.valueOf(v);
            if (!s.startsWith(prefix)) continue;
            String tip = v == 0 ? "<gray>Show once (no timer)</gray>"
                    : "<gray>Show for <green>" + v + "</green> seconds</gray>";
            b.suggest(v, ComponentFormatter.deserialize(tip)
                    .expect(TextFormatter.InputFormat.MINIMESSAGE)
                    .features(ComponentFormatter.ALL_DEFAULTS()).toBrigadierMessage());
        }
    }
}
