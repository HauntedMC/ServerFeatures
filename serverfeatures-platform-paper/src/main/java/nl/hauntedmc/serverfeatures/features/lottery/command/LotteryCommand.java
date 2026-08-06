package nl.hauntedmc.serverfeatures.features.lottery.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.features.lottery.Lottery;
import nl.hauntedmc.serverfeatures.features.lottery.model.Money;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/** Player and staff commands for the Lottery feature. */
public final class LotteryCommand implements BrigadierCommand {

    private static final int MAXIMUM_PAGE = 10_000;

    private final Lottery feature;

    public LotteryCommand(Lottery feature) {
        this.feature = feature;
    }

    @Override
    public @NotNull String name() {
        return "lottery";
    }

    @Override
    public String description() {
        return "View and participate in the HauntedMC lottery.";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name())
                .requires(source -> source.getSender().hasPermission(Lottery.USE_PERMISSION)
                        || source.getSender().hasPermission(Lottery.ADMIN_PERMISSION))
                .executes(context -> overview(context.getSource().getSender()));

        root.then(Commands.literal("buy")
                .requires(source -> source.getSender().hasPermission(Lottery.BUY_PERMISSION))
                .executes(context -> buy(context.getSource().getSender(), 1))
                .then(Commands.literal("max")
                        .executes(context -> buyMaximum(context.getSource().getSender())))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(context -> buy(
                                context.getSource().getSender(),
                                IntegerArgumentType.getInteger(context, "amount")
                        ))));
        root.then(Commands.literal("donate")
                .requires(source -> source.getSender().hasPermission(Lottery.DONATE_PERMISSION))
                .then(Commands.argument("amount", StringArgumentType.word())
                        .executes(context -> donate(
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "amount")
                        ))));
        root.then(Commands.literal("claim")
                .requires(source -> source.getSender().hasPermission(Lottery.CLAIM_PERMISSION))
                .executes(context -> claim(context.getSource().getSender())));
        root.then(pageCommand("history", (sender, page) -> feature.service().requestHistory(sender, page)));
        root.then(Commands.literal("leaderboard")
                .then(pageCommand("wins", (sender, page) -> feature.service().requestLeaderboard(sender, false, page)))
                .then(pageCommand(
                        "donations",
                        (sender, page) -> feature.service().requestLeaderboard(sender, true, page)
                )));
        root.then(Commands.literal("help")
                .executes(context -> help(context.getSource().getSender())));
        root.then(adminTree());
        return root.build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> adminTree() {
        LiteralArgumentBuilder<CommandSourceStack> admin = Commands.literal("admin")
                .requires(source -> source.getSender().hasPermission(Lottery.ADMIN_PERMISSION));
        admin.then(Commands.literal("status")
                .requires(source -> source.getSender().hasPermission(Lottery.ADMIN_INSPECT_PERMISSION))
                .executes(context -> {
                    feature.service().requestAdminStatus(context.getSource().getSender());
                    return 1;
                }));
        admin.then(Commands.literal("pause")
                .requires(source -> source.getSender().hasPermission(Lottery.ADMIN_PAUSE_PERMISSION))
                .executes(context -> pause(context.getSource().getSender(), true)));
        admin.then(Commands.literal("resume")
                .requires(source -> source.getSender().hasPermission(Lottery.ADMIN_PAUSE_PERMISSION))
                .executes(context -> pause(context.getSource().getSender(), false)));
        admin.then(Commands.literal("addpot")
                .requires(source -> source.getSender().hasPermission(Lottery.ADMIN_ADDPOT_PERMISSION))
                .then(Commands.argument("amount", StringArgumentType.word())
                        .executes(context -> addPot(
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "amount")
                        ))));
        admin.then(Commands.literal("draw")
                .requires(source -> source.getSender().hasPermission(Lottery.ADMIN_DRAW_PERMISSION))
                .executes(context -> {
                    feature.service().forceDraw(context.getSource().getSender());
                    return 1;
                }));
        admin.then(Commands.literal("cancel")
                .requires(source -> source.getSender().hasPermission(Lottery.ADMIN_CANCEL_PERMISSION))
                .executes(context -> {
                    feature.service().cancelRound(context.getSource().getSender());
                    return 1;
                }));
        return admin;
    }

    private static ArgumentBuilder<CommandSourceStack, ?> pageCommand(String literal, PageAction action) {
        return Commands.literal(literal)
                .executes(context -> {
                    action.execute(context.getSource().getSender(), 1);
                    return 1;
                })
                .then(Commands.argument("page", IntegerArgumentType.integer(1, MAXIMUM_PAGE))
                        .executes(context -> {
                            action.execute(
                                    context.getSource().getSender(),
                                    IntegerArgumentType.getInteger(context, "page")
                            );
                            return 1;
                        }));
    }

    private int overview(CommandSender sender) {
        feature.service().requestOverview(sender);
        return 1;
    }

    private int buy(CommandSender sender, int amount) {
        Player player = player(sender);
        if (player == null) {
            return 0;
        }
        feature.service().purchase(player, amount);
        return 1;
    }

    private int buyMaximum(CommandSender sender) {
        Player player = player(sender);
        if (player == null) {
            return 0;
        }
        int amount = feature.service().maximumAffordable(player);
        if (amount <= 0) {
            feature.service().explainCannotBuy(player);
            return 0;
        }
        feature.service().purchase(player, amount);
        return 1;
    }

    private int donate(CommandSender sender, String rawAmount) {
        Player player = player(sender);
        if (player == null) {
            return 0;
        }
        try {
            feature.service().donate(player, Money.parse(rawAmount));
            return 1;
        } catch (IllegalArgumentException exception) {
            feature.send(player, "lottery.donate.invalid", Map.of("reason", exception.getMessage()));
            return 0;
        }
    }

    private int claim(CommandSender sender) {
        Player player = player(sender);
        if (player == null) {
            return 0;
        }
        feature.service().claim(player, false);
        return 1;
    }

    private int pause(CommandSender sender, boolean paused) {
        feature.service().setPaused(sender, paused);
        return 1;
    }

    private int addPot(CommandSender sender, String rawAmount) {
        try {
            Money amount = Money.parse(rawAmount);
            if (!amount.isPositive()) {
                throw new IllegalArgumentException("amount must be positive");
            }
            feature.service().addToPot(sender, amount);
            return 1;
        } catch (IllegalArgumentException exception) {
            feature.send(sender, "lottery.admin.action_failed", Map.of("reason", exception.getMessage()));
            return 0;
        }
    }

    private int help(CommandSender sender) {
        feature.send(sender, "lottery.help");
        return 1;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command")
                .forAudience(sender)
                .build());
        return null;
    }

    @FunctionalInterface
    private interface PageAction {
        void execute(CommandSender sender, int page);
    }
}
