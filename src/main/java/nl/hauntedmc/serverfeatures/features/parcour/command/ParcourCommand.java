// File: nl/hauntedmc/serverfeatures/features/parcour/command/ParcourCommand.java
package nl.hauntedmc.serverfeatures.features.parcour.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.features.parcour.Parcour;
import nl.hauntedmc.serverfeatures.features.parcour.internal.ParcourHandler;
import nl.hauntedmc.serverfeatures.features.parcour.model.ParcourDefinition;
import nl.hauntedmc.serverfeatures.features.parcour.model.ParcourRegion;
import nl.hauntedmc.serverfeatures.features.parcour.model.ParcourRegionType;
import nl.hauntedmc.serverfeatures.features.parcour.model.Region;
import nl.hauntedmc.serverfeatures.features.parcour.registry.ParcourRegistry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class ParcourCommand implements BrigadierCommand {

    // Permissions
    private static final String BASE      = "serverfeatures.feature.parcour.use";              // root gate for players
    private static final String P_START   = "serverfeatures.feature.parcour.command.start";
    private static final String P_LEAVE   = "serverfeatures.feature.parcour.command.leave";
    private static final String P_CKPT    = "serverfeatures.feature.parcour.command.checkpoint";
    private static final String P_ADMIN   = "serverfeatures.feature.parcour.admin";            // root gate for admin subtree

    private final Parcour feature;
    private final ParcourRegistry registry;
    private final ParcourHandler handler;

    public ParcourCommand(Parcour feature, ParcourHandler handler) {
        this.feature = feature;
        this.registry = feature.getRegistry();
        this.handler = handler;
    }

    @Override
    public @NotNull String name() {
        return "parcour";
    }

    @Override
    public String description() {
        return "Play and manage parcours. Player: start/leave/checkpoint. Admin: create, add regions, etc.";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        // Root: allow if player-use OR admin; individual subcommands narrow further.
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name())
                .requires(src -> {
                    CommandSender s = src.getSender();
                    return s.hasPermission(BASE) || s.hasPermission(P_ADMIN);
                })

                // =========================
                // Player subcommands
                // =========================
                .then(Commands.literal("start")
                        .requires(src -> src.getSender().hasPermission(P_START))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .executes(ctx -> {
                                    CommandSender s = ctx.getSource().getSender();
                                    if (!(s instanceof Player p)) {
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(s).build());
                                        return 1;
                                    }
                                    String id = StringArgumentType.getString(ctx, "id");
                                    handler.startParcourByCommand(p, id);
                                    return 1;
                                })
                        )
                        .executes(ctx -> {
                            // usage
                            CommandSender s = ctx.getSource().getSender();
                            s.sendMessage("§7Gebruik: §f/parcour start <naam>");
                            return 1;
                        })
                )

                .then(Commands.literal("leave")
                        .requires(src -> src.getSender().hasPermission(P_LEAVE))
                        .executes(ctx -> {
                            CommandSender s = ctx.getSource().getSender();
                            if (!(s instanceof Player p)) {
                                s.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(s).build());
                                return 1;
                            }
                            handler.leaveParcour(p);
                            return 1;
                        })
                )

                .then(Commands.literal("checkpoint")
                        .requires(src -> src.getSender().hasPermission(P_CKPT))
                        .executes(ctx -> {
                            CommandSender s = ctx.getSource().getSender();
                            if (!(s instanceof Player p)) {
                                s.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(s).build());
                                return 1;
                            }
                            handler.teleportToCheckpoint(p);
                            return 1;
                        })
                )

                // =========================
                // Admin subcommands (merged)
                // =========================
                .then(Commands.literal("create")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> {
                                    CommandSender s = ctx.getSource().getSender();
                                    String id = StringArgumentType.getString(ctx, "id");
                                    if (handler.createParcour(id)) {
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.created")
                                                .with("id", id).forAudience(s).build());
                                    } else {
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.exists")
                                                .with("id", id).forAudience(s).build());
                                    }
                                    return 1;
                                }))
                )

                .then(Commands.literal("delete")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .executes(ctx -> {
                                    CommandSender s = ctx.getSource().getSender();
                                    String id = StringArgumentType.getString(ctx, "id");
                                    if (handler.deleteParcour(id)) {
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.deleted")
                                                .with("id", id).forAudience(s).build());
                                    } else {
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                .with("name", id).forAudience(s).build());
                                    }
                                    return 1;
                                }))
                )

                .then(Commands.literal("select")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .executes(ctx -> {
                                    CommandSender s = ctx.getSource().getSender();
                                    if (!(s instanceof Player p)) {
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(s).build());
                                        return 1;
                                    }
                                    String id = StringArgumentType.getString(ctx, "id");
                                    if (registry.get(id).isPresent()) {
                                        handler.selectParcour(p, id);
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.select.current")
                                                .with("id", id).forAudience(s).build());
                                    } else {
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                .with("name", id).forAudience(s).build());
                                    }
                                    return 1;
                                }))
                )

                .then(Commands.literal("wand")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .executes(ctx -> {
                            CommandSender s = ctx.getSource().getSender();
                            if (!(s instanceof Player p)) {
                                s.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(s).build());
                                return 1;
                            }
                            handler.giveWand(p);
                            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.wand.given").forAudience(s).build());
                            return 1;
                        })
                )

                .then(Commands.literal("add")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(this::suggestRegionTypes)
                                .then(Commands.argument("order", IntegerArgumentType.integer(0, 10000))
                                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                                .suggests(this::suggestParcourIds)
                                                // optional: restore flag as bool
                                                .then(Commands.argument("restore", BoolArgumentType.bool())
                                                        .executes(ctx -> execAdd(ctx, true)))
                                                .executes(ctx -> execAdd(ctx, false))
                                        )))
                )

                .then(Commands.literal("saveregion")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("order", IntegerArgumentType.integer(0, 10000))
                                .executes(ctx -> {
                                    CommandSender s = ctx.getSource().getSender();
                                    if (!(s instanceof Player p)) {
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(s).build());
                                        return 1;
                                    }
                                    var sel = handler.selection(p);
                                    if (sel.selectedParcourId == null) {
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.select.none").forAudience(s).build());
                                        return 1;
                                    }
                                    int order = IntegerArgumentType.getInteger(ctx, "order");
                                    if (sel.world1 == null || sel.world2 == null
                                            || !Objects.equals(sel.world1, sel.world2)
                                            || sel.pos1x == null || sel.pos1y == null || sel.pos1z == null
                                            || sel.pos2x == null || sel.pos2y == null || sel.pos2z == null) {
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.missing").forAudience(s).build());
                                        return 1;
                                    }
                                    boolean ok = handler.saveRegion(sel.selectedParcourId, order);
                                    if (ok) {
                                        registry.get(sel.selectedParcourId).flatMap(d -> d.regionByOrder(order)).ifPresent(pr ->
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.saved")
                                                        .with("type", pr.type().name())
                                                        .with("order", String.valueOf(order))
                                                        .forAudience(s).build()));
                                    } else {
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.not_found")
                                                .with("order", String.valueOf(order)).forAudience(s).build());
                                    }
                                    return 1;
                                }))
                )

                .then(Commands.literal("deleteregion")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .then(Commands.argument("order", IntegerArgumentType.integer(0, 10000))
                                        .suggests(this::suggestOrdersForParcour)
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getSender();
                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                            int order = IntegerArgumentType.getInteger(ctx, "order");
                                            if (handler.removeRegion(id, order)) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.deleted")
                                                        .with("type", "order")
                                                        .with("order", String.valueOf(order)).forAudience(s).build());
                                            } else {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.not_found")
                                                        .with("order", String.valueOf(order)).forAudience(s).build());
                                            }
                                            return 1;
                                        })))
                )

                .then(Commands.literal("setrestore")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .then(Commands.argument("order", IntegerArgumentType.integer(0, 10000))
                                        .suggests(this::suggestOrdersForParcour)
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> {
                                                    CommandSender s = ctx.getSource().getSender();
                                                    String id = StringArgumentType.getString(ctx, "parcourId");
                                                    int order = IntegerArgumentType.getInteger(ctx, "order");
                                                    boolean value = BoolArgumentType.getBool(ctx, "value");
                                                    if (handler.setRegionRestore(id, order, value)) {
                                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.restore.set")
                                                                .with("order", String.valueOf(order))
                                                                .with("restore", String.valueOf(value))
                                                                .forAudience(s).build());
                                                    } else {
                                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.not_found")
                                                                .with("order", String.valueOf(order)).forAudience(s).build());
                                                    }
                                                    return 1;
                                                }))))
                )

                .then(Commands.literal("addcmd")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .then(Commands.argument("order", IntegerArgumentType.integer(0, 10000))
                                        .suggests(this::suggestOrdersForParcour)
                                        .then(Commands.argument("command", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    CommandSender s = ctx.getSource().getSender();
                                                    String id = StringArgumentType.getString(ctx, "parcourId");
                                                    int order = IntegerArgumentType.getInteger(ctx, "order");
                                                    String cmd = StringArgumentType.getString(ctx, "command");
                                                    if (handler.addRegionCommand(id, order, cmd)) {
                                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.cmd.added")
                                                                .with("order", String.valueOf(order))
                                                                .with("cmd", cmd)
                                                                .forAudience(s).build());
                                                    } else {
                                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.not_found")
                                                                .with("order", String.valueOf(order)).forAudience(s).build());
                                                    }
                                                    return 1;
                                                }))))
                )

                .then(Commands.literal("clearcmds")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .then(Commands.argument("order", IntegerArgumentType.integer(0, 10000))
                                        .suggests(this::suggestOrdersForParcour)
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getSender();
                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                            int order = IntegerArgumentType.getInteger(ctx, "order");
                                            if (handler.clearRegionCommands(id, order)) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.cmd.cleared")
                                                        .with("order", String.valueOf(order)).forAudience(s).build());
                                            } else {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.not_found")
                                                        .with("order", String.valueOf(order)).forAudience(s).build());
                                            }
                                            return 1;
                                        })))
                )

                .then(Commands.literal("setexitspawn")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .executes(ctx -> {
                                    CommandSender s = ctx.getSource().getSender();
                                    if (!(s instanceof Player p)) {
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(s).build());
                                        return 1;
                                    }
                                    String id = StringArgumentType.getString(ctx, "parcourId");
                                    if (handler.setExitSpawn(id, p.getLocation())) {
                                        var l = p.getLocation();
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.exitspawn.set")
                                                .with("world", l.getWorld().getName())
                                                .with("x", fmt(l.getX()))
                                                .with("y", fmt(l.getY()))
                                                .with("z", fmt(l.getZ()))
                                                .with("yaw", fmt(l.getYaw()))
                                                .with("pitch", fmt(l.getPitch()))
                                                .forAudience(s).build());
                                    } else {
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                .with("name", id).forAudience(s).build());
                                    }
                                    return 1;
                                }))
                )

                .then(Commands.literal("info")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .executes(ctx -> {
                                    CommandSender s = ctx.getSource().getSender();
                                    String id = StringArgumentType.getString(ctx, "parcourId");
                                    registry.get(id).ifPresentOrElse(def -> sendInfo(s, def),
                                            () -> s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                    .with("name", id).forAudience(s).build()));
                                    return 1;
                                }))
                )

                .then(Commands.literal("list")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .executes(ctx -> {
                            CommandSender s = ctx.getSource().getSender();
                            var all = registry.all();
                            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.list.header")
                                    .with("count", String.valueOf(all.size())).forAudience(s).build());
                            for (ParcourDefinition d : all) {
                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.list.entry")
                                        .with("id", d.id())
                                        .with("regions", String.valueOf(d.totalOrders()))
                                        .forAudience(s).build());
                            }
                            return 1;
                        })
                )

                // Root usage fallback
                .executes(ctx -> {
                    CommandSender s = ctx.getSource().getSender();
                    if (s.hasPermission(BASE)) {
                        s.sendMessage("§7Speler: §f/parcour start <naam>§7, §f/parcour leave§7, §f/parcour checkpoint");
                    }
                    if (s.hasPermission(P_ADMIN)) {
                        s.sendMessage("§7Admin: §f/parcour create|delete|select|wand|add|saveregion|deleteregion|setrestore|addcmd|clearcmds|setexitspawn|info|list");
                    }
                    return 1;
                });

        return root.build();
    }

    // =========================
    // Helpers (exec & suggests)
    // =========================

    private int execAdd(CommandContext<CommandSourceStack> ctx, boolean hasRestoreArg) {
        CommandSender s = ctx.getSource().getSender();
        String typeStr = StringArgumentType.getString(ctx, "type").toUpperCase(Locale.ROOT);
        ParcourRegionType type;
        try {
            type = ParcourRegionType.valueOf(typeStr);
        } catch (IllegalArgumentException ex) {
            s.sendMessage("§cType moet één van: start, checkpoint, end.");
            return 1;
        }
        int order = IntegerArgumentType.getInteger(ctx, "order");
        String id = StringArgumentType.getString(ctx, "parcourId");
        boolean restore = hasRestoreArg && BoolArgumentType.getBool(ctx, "restore");

        if (handler.addRegion(id, type, order, restore)) {
            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.added")
                    .with("type", type.name())
                    .with("order", String.valueOf(order))
                    .with("restore", String.valueOf(restore))
                    .forAudience(s).build());
        } else {
            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                    .with("name", id).forAudience(s).build());
        }
        return 1;
    }

    private CompletableFuture<Suggestions> suggestParcourIds(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
        String rem = b.getRemaining().toLowerCase(Locale.ROOT);
        for (ParcourDefinition d : registry.all()) {
            String id = d.id();
            if (id.toLowerCase(Locale.ROOT).startsWith(rem)) b.suggest(id);
        }
        return b.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestRegionTypes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
        for (String t : new String[]{"start", "checkpoint", "end"}) {
            if (t.startsWith(b.getRemainingLowerCase())) b.suggest(t);
        }
        return b.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOrdersForParcour(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
        String id = null;
        try {
            id = StringArgumentType.getString(ctx, "parcourId");
        } catch (IllegalArgumentException ignored) {
            // argument not present in this branch
        }

        String rem = b.getRemaining(); // what user has typed so far
        List<String> toSuggest = new ArrayList<>();

        if (id != null) {
            registry.get(id).ifPresent(def -> {
                for (Integer ord : def.orders()) {
                    String s = String.valueOf(ord);
                    if (s.startsWith(rem)) {
                        toSuggest.add(s);
                    }
                }
            });
        }

        // Fallback suggestions if none found for the given parcours
        if (toSuggest.isEmpty()) {
            for (int i = 0; i <= 10; i++) {
                String s = String.valueOf(i);
                if (s.startsWith(rem)) {
                    toSuggest.add(s);
                }
            }
        }

        toSuggest.forEach(b::suggest);
        return b.buildFuture();
    }


    private void sendInfo(CommandSender sender, ParcourDefinition def) {
        var lh = feature.getLocalizationHandler();
        sender.sendMessage(lh.getMessage("parcour.admin.info.header")
                .with("id", def.id()).forAudience(sender).build());

        sendProp(sender, "Exit Spawn", def.exitSpawn().map(l ->
                l.getWorld().getName() + " " + fmt(l.getX()) + " " + fmt(l.getY()) + " " + fmt(l.getZ()) + " " + fmt(l.getYaw()) + " " + fmt(l.getPitch())
        ).orElse("-"));

        // Regions sorted by order
        for (ParcourRegion pr : def.regions().stream().sorted(Comparator.comparingInt(ParcourRegion::order)).collect(Collectors.toList())) {
            Region r = pr.region().orElse(null);
            String regionStr = (r == null) ? "-" :
                    r.worldName() + " [" + r.minX() + "," + r.minY() + "," + r.minZ() + "] -> [" + r.maxX() + "," + r.maxY() + "," + r.maxZ() + "]";
            sendProp(sender, "Order " + pr.order(), pr.type().name() + " / restore=" + pr.restoreCheckpoint());
            sendProp(sender, "Region " + pr.order(), regionStr);
            if (!pr.commands().isEmpty()) {
                sendProp(sender, "Commands " + pr.order(), String.join(" || ", pr.commands()));
            }
        }
    }

    private void sendProp(CommandSender sender, String key, String value) {
        sender.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.info.prop")
                .with("key", key)
                .with("value", value)
                .forAudience(sender).build());
    }

    private String fmt(double d) {
        return (Math.abs(d - Math.rint(d)) < 1e-9) ? String.valueOf((long) Math.rint(d)) : String.format(Locale.ROOT, "%.3f", d);
    }

    private String fmt(float f) {
        return String.format(Locale.ROOT, "%.1f", f);
    }
}
