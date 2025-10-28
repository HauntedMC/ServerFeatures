package nl.hauntedmc.serverfeatures.features.nametags.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class NametagCommand implements BrigadierCommand {
    private static final String BASE = "serverfeatures.feature.nametags.use";
    private static final String P_SELFVIEW = "serverfeatures.feature.nametags.command.selfview";

    private final Nametags feature;

    public NametagCommand(Nametags feature) {
        this.feature = feature;
    }

    @Override
    public @NotNull String name() {
        return "nametag";
    }

    @Override
    public String description() {
        return "Toggle whether you see your own nametag.";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name())
                .requires(src -> src.getSender().hasPermission(BASE))

                .then(Commands.literal("selfview")
                        .requires(src -> src.getSender().hasPermission(P_SELFVIEW))

                        .then(Commands.literal("on")
                                .executes(ctx -> {
                                    CommandSender s = ctx.getSource().getSender();
                                    if (!(s instanceof Player p)) {
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(s).build());
                                        return 1;
                                    }
                                    feature.getNametagManager().setSelfViewEnabled(p, true);
                                    s.sendMessage(feature.getLocalizationHandler().getMessage("nametags.selfview.enabled").forAudience(s).build());
                                    return 1;
                                }))

                        .then(Commands.literal("off")
                                .executes(ctx -> {
                                    CommandSender s = ctx.getSource().getSender();
                                    if (!(s instanceof Player p)) {
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(s).build());
                                        return 1;
                                    }
                                    feature.getNametagManager().setSelfViewEnabled(p, false);
                                    s.sendMessage(feature.getLocalizationHandler().getMessage("nametags.selfview.disabled").forAudience(s).build());
                                    return 1;
                                }))

                        .then(Commands.literal("toggle")
                                .executes(ctx -> {
                                    CommandSender s = ctx.getSource().getSender();
                                    if (!(s instanceof Player p)) {
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(s).build());
                                        return 1;
                                    }
                                    boolean current = feature.getNametagManager().isSelfViewEnabled(p);
                                    boolean next = !current;
                                    feature.getNametagManager().setSelfViewEnabled(p, next);
                                    String key = next ? "nametags.selfview.enabled" : "nametags.selfview.disabled";
                                    s.sendMessage(feature.getLocalizationHandler().getMessage(key).forAudience(s).build());
                                    return 1;
                                }))

                        .then(Commands.literal("status")
                                .executes(ctx -> {
                                    CommandSender s = ctx.getSource().getSender();
                                    if (!(s instanceof Player p)) {
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(s).build());
                                        return 1;
                                    }
                                    boolean enabled = feature.getNametagManager().isSelfViewEnabled(p);
                                    s.sendMessage(feature.getLocalizationHandler()
                                            .getMessage(enabled ? "nametags.selfview.status_on" : "nametags.selfview.status_off")
                                            .forAudience(s).build());
                                    return 1;
                                }))

                        .executes(ctx -> {
                            CommandSender s = ctx.getSource().getSender();
                            if (s instanceof Player p) {
                                boolean enabled = feature.getNametagManager().isSelfViewEnabled(p);
                                s.sendMessage(feature.getLocalizationHandler()
                                        .getMessage(enabled ? "nametags.selfview.status_on" : "nametags.selfview.status_off")
                                        .forAudience(s).build());
                            }
                            s.sendMessage(feature.getLocalizationHandler().getMessage("nametags.selfview.usage").forAudience(s).build());
                            return 1;
                        })
                );

        return root.build();
    }
}
