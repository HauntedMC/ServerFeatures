package nl.hauntedmc.serverfeatures.features.parcour.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.api.util.BukkitRegistry;
import nl.hauntedmc.serverfeatures.features.parcour.Parcour;
import nl.hauntedmc.serverfeatures.features.parcour.internal.ParcourHandler;
import nl.hauntedmc.serverfeatures.features.parcour.model.ParcourDefinition;
import nl.hauntedmc.serverfeatures.features.parcour.model.ParcourRegion;
import nl.hauntedmc.serverfeatures.features.parcour.model.Region;
import nl.hauntedmc.serverfeatures.features.parcour.registry.ParcourRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class ParcourCommand implements BrigadierCommand {

    private static final String BASE = "serverfeatures.feature.parcour.use";
    private static final String P_START = "serverfeatures.feature.parcour.command.start";
    private static final String P_LEAVE = "serverfeatures.feature.parcour.command.leave";
    private static final String P_CKPT = "serverfeatures.feature.parcour.command.checkpoint";
    private static final String P_ADMIN = "serverfeatures.feature.parcour.admin";

    private final Parcour feature;
    private final ParcourRegistry registry;
    private final ParcourHandler handler;

    public ParcourCommand(Parcour feature, ParcourHandler handler) {
        this.feature = feature;
        this.registry = feature.getRegistry();
        this.handler = handler;
    }

    @Override
    public @NotNull String name() { return "parcour"; }

    @Override
    public String description() {
        return "Play and manage parcours. Player: start/leave/checkpoint. Admin: manage maps, regions, and settings.";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name())
                .requires(src -> {
                    CommandSender s = src.getSender();
                    return s.hasPermission(BASE) || s.hasPermission(P_ADMIN);
                })

                // ===== Player commands =====
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
                            CommandSender s = ctx.getSource().getSender();
                            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.usage.start").forAudience(s).build());
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
                            handler.teleportToCheckpoint(p, true);
                            return 1;
                        })
                )

                // ===== Admin: map lifecycle =====
                .then(Commands.literal("createmap")
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

                .then(Commands.literal("deletemap")
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

                // ===== Admin: regions =====
                .then(Commands.literal("addregion")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.literal("start")
                                .then(Commands.argument("parcourId", StringArgumentType.word())
                                        .suggests(this::suggestParcourIds)
                                        .then(Commands.argument("restore", BoolArgumentType.bool())
                                                .executes(ctx -> execAddStart(ctx, true)))
                                        .executes(ctx -> execAddStart(ctx, false))))
                        .then(Commands.literal("end")
                                .then(Commands.argument("parcourId", StringArgumentType.word())
                                        .suggests(this::suggestParcourIds)
                                        .executes(this::execAddEnd)))
                        .then(Commands.literal("checkpoint")
                                .then(Commands.argument("order", IntegerArgumentType.integer(0, 10000))
                                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                                .suggests(this::suggestParcourIds)
                                                .then(Commands.argument("restore", BoolArgumentType.bool())
                                                        .executes(ctx -> execAddCheckpoint(ctx, true)))
                                                .executes(ctx -> execAddCheckpoint(ctx, false))))))

                .then(Commands.literal("deleteregion")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(this::suggestRegionKeysForParcour)
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getSender();
                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                            String key = StringArgumentType.getString(ctx, "key");
                                            if (handler.removeRegion(id, key)) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.deleted")
                                                        .with("type", key.toUpperCase(java.util.Locale.ROOT))
                                                        .with("order", key).forAudience(s).build());
                                            } else {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.not_found")
                                                        .with("order", key).forAudience(s).build());
                                            }
                                            return 1;
                                        })))
                )

                .then(Commands.literal("setrestore")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(this::suggestRegionKeysForParcour)
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> {
                                                    CommandSender s = ctx.getSource().getSender();
                                                    String id = StringArgumentType.getString(ctx, "parcourId");
                                                    String key = StringArgumentType.getString(ctx, "key");
                                                    boolean value = BoolArgumentType.getBool(ctx, "value");
                                                    if (handler.setRegionRestore(id, key, value)) {
                                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.restore.set")
                                                                .with("order", key)
                                                                .with("restore", String.valueOf(value))
                                                                .forAudience(s).build());
                                                    } else {
                                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.not_found")
                                                                .with("order", key).forAudience(s).build());
                                                    }
                                                    return 1;
                                                }))))
                )

                // ===== Admin: commands on regions (consolidated) =====
                .then(Commands.literal("setcmd")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        // add
                        .then(Commands.literal("add")
                                .then(Commands.argument("parcourId", StringArgumentType.word())
                                        .suggests(this::suggestParcourIds)
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .suggests(this::suggestRegionKeysForParcour)
                                                .then(Commands.argument("command", StringArgumentType.greedyString())
                                                        .executes(ctx -> {
                                                            CommandSender s = ctx.getSource().getSender();
                                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                                            String key = StringArgumentType.getString(ctx, "key");
                                                            String cmd = StringArgumentType.getString(ctx, "command");
                                                            if (handler.addRegionCommand(id, key, cmd)) {
                                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.cmd.added")
                                                                        .with("order", key)
                                                                        .with("cmd", cmd)
                                                                        .forAudience(s).build());
                                                            } else {
                                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.not_found")
                                                                        .with("order", key).forAudience(s).build());
                                                            }
                                                            return 1;
                                                        })))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("parcourId", StringArgumentType.word())
                                        .suggests(this::suggestParcourIds)
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .suggests(this::suggestRegionKeysForParcour)
                                                .executes(ctx -> {
                                                    CommandSender s = ctx.getSource().getSender();
                                                    String id = StringArgumentType.getString(ctx, "parcourId");
                                                    String key = StringArgumentType.getString(ctx, "key");
                                                    if (handler.clearRegionCommands(id, key)) {
                                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.cmd.cleared")
                                                                .with("order", key).forAudience(s).build());
                                                    } else {
                                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.not_found")
                                                                .with("order", key).forAudience(s).build());
                                                    }
                                                    return 1;
                                                }))))
                        // remove (by index, 1-based)
                        .then(Commands.literal("remove")
                                .then(Commands.argument("parcourId", StringArgumentType.word())
                                        .suggests(this::suggestParcourIds)
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .suggests(this::suggestRegionKeysForParcour)
                                                .then(Commands.argument("index", IntegerArgumentType.integer(1, 1000))
                                                        .executes(ctx -> {
                                                            CommandSender s = ctx.getSource().getSender();
                                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                                            String key = StringArgumentType.getString(ctx, "key");
                                                            int idx = IntegerArgumentType.getInteger(ctx, "index");
                                                            if (handler.removeRegionCommandIndex(id, key, idx)) {
                                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.cmd.removed")
                                                                        .with("order", key)
                                                                        .with("index", String.valueOf(idx))
                                                                        .forAudience(s).build());
                                                            } else {
                                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.cmd.remove_failed")
                                                                        .with("order", key)
                                                                        .with("index", String.valueOf(idx))
                                                                        .forAudience(s).build());
                                                            }
                                                            return 1;
                                                        }))))))

                // clear all
                // list
                .then(Commands.literal("list")
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(this::suggestRegionKeysForParcour)
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getSender();
                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                            String key = StringArgumentType.getString(ctx, "key");
                                            Optional<ParcourDefinition> defOpt = registry.get(id);
                                            if (defOpt.isEmpty()) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                        .with("name", id).forAudience(s).build());
                                                return 1;
                                            }
                                            ParcourDefinition def = defOpt.get();
                                            ParcourRegion pr = "START".equalsIgnoreCase(key) ? def.startRegion().orElse(null)
                                                    : "END".equalsIgnoreCase(key) ? def.endRegion().orElse(null)
                                                    : def.checkpoint(Integer.parseInt(key)).orElse(null);
                                            if (pr == null) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.not_found")
                                                        .with("order", key).forAudience(s).build());
                                                return 1;
                                            }
                                            var list = pr.commands();
                                            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.cmd.list.header")
                                                    .with("key", key).forAudience(s).build());
                                            if (list.isEmpty()) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.list.empty").forAudience(s).build());
                                            } else {
                                                for (int i = 0; i < list.size(); i++) {
                                                    s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.cmd.list.entry")
                                                            .with("index", String.valueOf(i + 1))
                                                            .with("cmd", list.get(i))
                                                            .forAudience(s).build());
                                                }
                                            }
                                            return 1;
                                        })))
                )

                // ===== Admin: locations with unified clear =====
                .then(Commands.literal("setleavelocation")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .then(Commands.literal("clear")
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getSender();
                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                            if (handler.clearLeaveLocation(id)) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.leave.cleared").forAudience(s).build());
                                            } else {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                        .with("name", id).forAudience(s).build());
                                            }
                                            return 1;
                                        }))
                                .executes(ctx -> {
                                    CommandSender s = ctx.getSource().getSender();
                                    if (!(s instanceof Player p)) {
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(s).build());
                                        return 1;
                                    }
                                    String id = StringArgumentType.getString(ctx, "parcourId");
                                    if (handler.setLeaveLocation(id, p.getLocation())) {
                                        var l = p.getLocation();
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.leave.set")
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

                .then(Commands.literal("setfinishlocation")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .then(Commands.literal("clear")
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getSender();
                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                            if (handler.clearFinishLocation(id)) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.finish.cleared").forAudience(s).build());
                                            } else {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                        .with("name", id).forAudience(s).build());
                                            }
                                            return 1;
                                        }))
                                .executes(ctx -> {
                                    CommandSender s = ctx.getSource().getSender();
                                    if (!(s instanceof Player p)) {
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(s).build());
                                        return 1;
                                    }
                                    String id = StringArgumentType.getString(ctx, "parcourId");
                                    if (handler.setFinishLocation(id, p.getLocation())) {
                                        var l = p.getLocation();
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.finish.set")
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

                .then(Commands.literal("setrestorelocation")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(this::suggestStartOrNumbersForParcour)
                                        .then(Commands.literal("clear")
                                                .executes(ctx -> {
                                                    CommandSender s = ctx.getSource().getSender();
                                                    String id = StringArgumentType.getString(ctx, "parcourId");
                                                    String key = StringArgumentType.getString(ctx, "key");
                                                    if ("END".equalsIgnoreCase(key)) {
                                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.restoreloc.not_applicable")
                                                                .with("order", key).forAudience(s).build());
                                                        return 1;
                                                    }
                                                    if (handler.clearRegionRestoreLocation(id, key)) {
                                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.restoreloc.cleared")
                                                                .with("order", key).forAudience(s).build());
                                                    } else {
                                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.not_found")
                                                                .with("order", key).forAudience(s).build());
                                                    }
                                                    return 1;
                                                }))
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getSender();
                                            if (!(s instanceof Player p)) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(s).build());
                                                return 1;
                                            }
                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                            String key = StringArgumentType.getString(ctx, "key");
                                            if ("END".equalsIgnoreCase(key)) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.restoreloc.not_applicable")
                                                        .with("order", key).forAudience(s).build());
                                                return 1;
                                            }
                                            if (handler.setRegionRestoreLocation(id, key, p.getLocation())) {
                                                var l = p.getLocation();
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.restoreloc.set")
                                                        .with("order", key)
                                                        .with("world", l.getWorld().getName())
                                                        .with("x", fmt(l.getX()))
                                                        .with("y", fmt(l.getY()))
                                                        .with("z", fmt(l.getZ()))
                                                        .with("yaw", fmt(l.getYaw()))
                                                        .with("pitch", fmt(l.getPitch()))
                                                        .forAudience(s).build());
                                            } else {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.not_found")
                                                        .with("order", key).forAudience(s).build());
                                            }
                                            return 1;
                                        })))
                )

                // ===== Admin: booleans/particles/sounds/etc =====
                .then(Commands.literal("setprogressnotify")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getSender();
                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                            boolean v = BoolArgumentType.getBool(ctx, "value");
                                            if (handler.setProgressNotify(id, v)) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.progress.notify.set")
                                                        .with("id", id).with("value", String.valueOf(v)).forAudience(s).build());
                                            } else {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                        .with("name", id).forAudience(s).build());
                                            }
                                            return 1;
                                        })))
                )

                .then(Commands.literal("setsound")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(this::suggestSoundTypes)
                                        .then(Commands.argument("sound", StringArgumentType.word())
                                                .suggests(this::suggestSoundNames)
                                                .executes(ctx -> {
                                                    CommandSender s = ctx.getSource().getSender();
                                                    String id = StringArgumentType.getString(ctx, "parcourId");
                                                    String type = StringArgumentType.getString(ctx, "type").toUpperCase(Locale.ROOT);
                                                    String soundArg = StringArgumentType.getString(ctx, "sound");

                                                    boolean clear = isClearKeyword(soundArg);
                                                    final String valueToStore;

                                                    if (!clear) {
                                                        NamespacedKey key = BukkitRegistry.deserializeNamespacedKey(soundArg);
                                                        if (key == null || BukkitRegistry.soundRegistry().get(key) == null) {
                                                            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.sound.invalid")
                                                                    .with("sound", soundArg).forAudience(s).build());
                                                            return 1;
                                                        }
                                                        valueToStore = key.toString(); // canonical namespaced id
                                                    } else {
                                                        valueToStore = null;
                                                    }

                                                    boolean ok;
                                                    if ("CHECKPOINT".equals(type)) {
                                                        ok = handler.setCheckpointSound(id, valueToStore);
                                                    } else if ("END".equals(type)) {
                                                        ok = handler.setEndSound(id, valueToStore);
                                                    } else {
                                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.sound.invalid_type")
                                                                .with("type", type).forAudience(s).build());
                                                        return 1;
                                                    }

                                                    if (!ok) {
                                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                                .with("name", id).forAudience(s).build());
                                                        return 1;
                                                    }

                                                    if (clear) {
                                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.sound.cleared")
                                                                .with("type", type).forAudience(s).build());
                                                    } else {
                                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.sound.set")
                                                                .with("type", type).with("sound", valueToStore).forAudience(s).build());
                                                    }
                                                    return 1;
                                                }))))
                )

                .then(Commands.literal("setactionbar")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getSender();
                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                            boolean v = BoolArgumentType.getBool(ctx, "value");
                                            if (handler.setUseActionBar(id, v)) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.actionbar.set")
                                                        .with("id", id).with("value", String.valueOf(v)).forAudience(s).build());
                                            } else {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                        .with("name", id).forAudience(s).build());
                                            }
                                            return 1;
                                        })))
                )

                .then(Commands.literal("setfinishdelay")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getSender();
                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                            int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                            if (handler.setFinishTeleportDelay(id, seconds)) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.finishdelay.set")
                                                        .with("id", id).with("seconds", String.valueOf(seconds)).forAudience(s).build());
                                            } else {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                        .with("name", id).forAudience(s).build());
                                            }
                                            return 1;
                                        })))
                )

                .then(Commands.literal("setregionparticle")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .then(Commands.argument("particle", StringArgumentType.word())
                                        .suggests(this::suggestParticleNames)
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getSender();
                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                            String arg = StringArgumentType.getString(ctx, "particle");

                                            boolean clear = isClearKeyword(arg);
                                            String value = null;

                                            if (!clear) {
                                                NamespacedKey key = BukkitRegistry.deserializeNamespacedKey(arg);
                                                if (key == null || BukkitRegistry.particleRegistry().get(key) == null) {
                                                    s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.particle.invalid")
                                                            .with("particle", arg).forAudience(s).build());
                                                    return 1;
                                                }
                                                value = key.toString();
                                            }

                                            boolean ok = handler.setRegionParticle(id, value);
                                            if (!ok) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                        .with("name", id).forAudience(s).build());
                                                return 1;
                                            }

                                            if (clear) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.particle.cleared").forAudience(s).build());
                                            } else {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.particle.set")
                                                        .with("particle", value).forAudience(s).build());
                                            }
                                            return 1;
                                        })))
                )

                .then(Commands.literal("sethunger")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getSender();
                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                            boolean v = BoolArgumentType.getBool(ctx, "value");
                                            if (handler.setHungerEnabled(id, v)) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.hunger.set")
                                                        .with("id", id).with("value", String.valueOf(v)).forAudience(s).build());
                                            } else {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                        .with("name", id).forAudience(s).build());
                                            }
                                            return 1;
                                        })))
                )

                .then(Commands.literal("setdamage")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getSender();
                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                            boolean v = BoolArgumentType.getBool(ctx, "value");
                                            if (handler.setDamageEnabled(id, v)) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.damage.set")
                                                        .with("id", id).with("value", String.valueOf(v)).forAudience(s).build());
                                            } else {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                        .with("name", id).forAudience(s).build());
                                            }
                                            return 1;
                                        })))
                )

                .then(Commands.literal("setcheckpointcooldown")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getSender();
                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                            int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                            if (handler.setCheckpointCooldownSeconds(id, seconds)) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.checkpointcooldown.set")
                                                        .with("id", id).with("seconds", String.valueOf(seconds)).forAudience(s).build());
                                            } else {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                        .with("name", id).forAudience(s).build());
                                            }
                                            return 1;
                                        })))
                )

                // ===== Admin: start countdown & start location unified =====
                .then(Commands.literal("setstartcountdown")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getSender();
                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                            int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                            if (handler.setStartCountdownSeconds(id, seconds)) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.startcountdown.set")
                                                        .with("id", id).with("seconds", String.valueOf(seconds)).forAudience(s).build());
                                            } else {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                        .with("name", id).forAudience(s).build());
                                            }
                                            return 1;
                                        })))
                )

                .then(Commands.literal("setstartlocation")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .then(Commands.literal("clear")
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getSender();
                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                            if (handler.clearStartPosition(id)) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.startpos.cleared").forAudience(s).build());
                                            } else {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                        .with("name", id).forAudience(s).build());
                                            }
                                            return 1;
                                        }))
                                .executes(ctx -> {
                                    CommandSender s = ctx.getSource().getSender();
                                    if (!(s instanceof Player p)) {
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(s).build());
                                        return 1;
                                    }
                                    String id = StringArgumentType.getString(ctx, "parcourId");
                                    if (handler.setStartPosition(id, p.getLocation())) {
                                        var l = p.getLocation();
                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.startpos.set")
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

                // ===== Admin: kits (renamed to setkit) =====
                .then(Commands.literal("setkit")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.literal("addfromhand")
                                .then(Commands.argument("parcourId", StringArgumentType.word())
                                        .suggests(this::suggestParcourIds)
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getSender();
                                            if (!(s instanceof Player p)) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(s).build());
                                                return 1;
                                            }
                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                            if (handler.addStartKitFromHand(id, p)) {
                                                ItemStack is = p.getInventory().getItemInMainHand();
                                                String nice = is.getType().isAir() ? "minecraft:air" : is.getType().getKey().toString();
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.startkit.added")
                                                        .with("id", id).with("item", nice).forAudience(s).build());
                                            } else {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                        .with("name", id).forAudience(s).build());
                                            }
                                            return 1;
                                        })))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("parcourId", StringArgumentType.word())
                                        .suggests(this::suggestParcourIds)
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getSender();
                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                            if (handler.clearStartKit(id)) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.startkit.cleared")
                                                        .with("id", id).forAudience(s).build());
                                            } else {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                        .with("name", id).forAudience(s).build());
                                            }
                                            return 1;
                                        })))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("parcourId", StringArgumentType.word())
                                        .suggests(this::suggestParcourIds)
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1, 1000))
                                                .executes(ctx -> {
                                                    CommandSender s = ctx.getSource().getSender();
                                                    String id = StringArgumentType.getString(ctx, "parcourId");
                                                    int idx = IntegerArgumentType.getInteger(ctx, "index");
                                                    if (handler.removeStartKitIndex(id, idx)) {
                                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.startkit.removed")
                                                                .with("id", id).with("index", String.valueOf(idx)).forAudience(s).build());
                                                    } else {
                                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.startkit.remove_failed")
                                                                .with("id", id).with("index", String.valueOf(idx)).forAudience(s).build());
                                                    }
                                                    return 1;
                                                }))))
                        .then(Commands.literal("list")
                                .then(Commands.argument("parcourId", StringArgumentType.word())
                                        .suggests(this::suggestParcourIds)
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getSender();
                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                            Optional<List<ItemStack>> listOpt = handler.listStartKit(id);
                                            if (listOpt.isEmpty()) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                        .with("name", id).forAudience(s).build());
                                                return 1;
                                            }
                                            List<ItemStack> list = listOpt.get();
                                            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.startkit.list.header")
                                                    .with("id", id).forAudience(s).build());
                                            for (int i = 0; i < list.size(); i++) {
                                                ItemStack is = list.get(i);
                                                String nice = (is == null || is.getType().isAir())
                                                        ? "minecraft:air"
                                                        : is.getType().getKey() + " x" + is.getAmount();
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.startkit.list.entry")
                                                        .with("index", String.valueOf(i + 1))
                                                        .with("item", nice)
                                                        .forAudience(s).build());
                                            }
                                            if (list.isEmpty()) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.list.empty").forAudience(s).build());
                                            }
                                            return 1;
                                        })))
                )

                // ===== Admin: map info/list (renamed) =====
                .then(Commands.literal("mapinfo")
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

                .then(Commands.literal("maplist")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .executes(ctx -> {
                            CommandSender s = ctx.getSource().getSender();
                            var all = registry.all();
                            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.list.header")
                                    .with("count", String.valueOf(all.size())).forAudience(s).build());
                            for (ParcourDefinition d : all) {
                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.list.entry")
                                        .with("id", d.id())
                                        .with("regions", String.valueOf(d.totalRegions()))
                                        .forAudience(s).build());
                            }
                            return 1;
                        })
                )

                // ===== Admin: parcour-wide effect =====
                .then(Commands.literal("seteffect")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .then(Commands.literal("clear")
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getSender();
                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                            if (handler.setEffect(id, null, null)) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.effect.cleared")
                                                        .with("id", id).forAudience(s).build());
                                            } else {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                        .with("name", id).forAudience(s).build());
                                            }
                                            return 1;
                                        }))
                                .then(Commands.argument("effect", StringArgumentType.word())
                                        .suggests(this::suggestEffectNames)
                                        .then(Commands.argument("amplifier", IntegerArgumentType.integer(0, 255))
                                                .executes(ctx -> {
                                                    CommandSender s = ctx.getSource().getSender();
                                                    String id = StringArgumentType.getString(ctx, "parcourId");
                                                    String effArg = StringArgumentType.getString(ctx, "effect");
                                                    int amp = IntegerArgumentType.getInteger(ctx, "amplifier");

                                                    NamespacedKey key = BukkitRegistry.deserializeNamespacedKey(effArg);
                                                    if (key == null || BukkitRegistry.mobEffectRegistry().get(key) == null) {
                                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.effect.invalid")
                                                                .with("effect", effArg).forAudience(s).build());
                                                        return 1;
                                                    }

                                                    if (handler.setEffect(id, key.toString(), amp)) {
                                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.effect.set")
                                                                .with("id", id)
                                                                .with("effect", key.toString())
                                                                .with("amplifier", String.valueOf(amp))
                                                                .forAudience(s).build());
                                                    } else {
                                                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                                .with("name", id).forAudience(s).build());
                                                    }
                                                    return 1;
                                                }))
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getSender();
                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                            String effArg = StringArgumentType.getString(ctx, "effect");

                                            NamespacedKey key = BukkitRegistry.deserializeNamespacedKey(effArg);
                                            if (key == null || BukkitRegistry.mobEffectRegistry().get(key) == null) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.effect.invalid")
                                                        .with("effect", effArg).forAudience(s).build());
                                                return 1;
                                            }
                                            int amp = 0;
                                            if (handler.setEffect(id, key.toString(), amp)) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.effect.set")
                                                        .with("id", id)
                                                        .with("effect", key.toString())
                                                        .with("amplifier", String.valueOf(amp))
                                                        .forAudience(s).build());
                                            } else {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                        .with("name", id).forAudience(s).build());
                                            }
                                            return 1;
                                        })))
                )

                .executes(ctx -> {
                    CommandSender s = ctx.getSource().getSender();
                    if (s.hasPermission(BASE)) {
                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.help.player").forAudience(s).build());
                    }
                    if (s.hasPermission(P_ADMIN)) {
                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.help.admin.header").forAudience(s).build());
                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.help.admin.maps").forAudience(s).build());
                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.help.admin.regions").forAudience(s).build());
                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.help.admin.cmds").forAudience(s).build());
                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.help.admin.locs").forAudience(s).build());
                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.help.admin.settings").forAudience(s).build());
                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.help.admin.kit").forAudience(s).build());
                        s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.help.we").forAudience(s).build());
                    }
                    return 1;
                });

        return root.build();
    }

    private int execAddStart(CommandContext<CommandSourceStack> ctx, boolean hasRestoreArg) {
        CommandSender s = ctx.getSource().getSender();
        if (!(s instanceof Player p)) {
            s.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(s).build());
            return 1;
        }
        String id = StringArgumentType.getString(ctx, "parcourId");
        boolean restore = hasRestoreArg && BoolArgumentType.getBool(ctx, "restore");

        Region region = getWESelectedRegionOrNull(p);
        if (region == null) {
            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.missing").forAudience(s).build());
            return 1;
        }
        if (handler.addRegionStart(id, region, restore)) {
            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.added")
                    .with("type", "START").with("order", "START")
                    .with("restore", String.valueOf(restore)).forAudience(s).build());
        } else {
            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                    .with("name", id).forAudience(s).build());
        }
        return 1;
    }

    private int execAddEnd(CommandContext<CommandSourceStack> ctx) {
        CommandSender s = ctx.getSource().getSender();
        if (!(s instanceof Player p)) {
            s.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(s).build());
            return 1;
        }
        String id = StringArgumentType.getString(ctx, "parcourId");

        Region region = getWESelectedRegionOrNull(p);
        if (region == null) {
            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.missing").forAudience(s).build());
            return 1;
        }
        if (handler.addRegionEnd(id, region)) {
            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.added_no_restore")
                    .with("type", "END").with("order", "END").forAudience(s).build());
        } else {
            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                    .with("name", id).forAudience(s).build());
        }
        return 1;
    }

    private int execAddCheckpoint(CommandContext<CommandSourceStack> ctx, boolean hasRestoreArg) {
        CommandSender s = ctx.getSource().getSender();
        if (!(s instanceof Player p)) {
            s.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(s).build());
            return 1;
        }
        int order = IntegerArgumentType.getInteger(ctx, "order");
        String id = StringArgumentType.getString(ctx, "parcourId");
        boolean restore = hasRestoreArg && BoolArgumentType.getBool(ctx, "restore");

        Region region = getWESelectedRegionOrNull(p);
        if (region == null) {
            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.missing").forAudience(s).build());
            return 1;
        }
        if (handler.addRegionCheckpoint(id, order, region, restore)) {
            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.added")
                    .with("type", "CHECKPOINT")
                    .with("order", String.valueOf(order))
                    .with("restore", String.valueOf(restore))
                    .forAudience(s).build());
        } else {
            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.not_found")
                    .with("order", String.valueOf(order)).forAudience(s).build());
        }
        return 1;
    }

    private Region getWESelectedRegionOrNull(Player p) {
        try {
            var wePlayer = BukkitAdapter.adapt(p);
            LocalSession session = WorldEdit.getInstance().getSessionManager().get(wePlayer);
            if (session == null) return null;

            com.sk89q.worldedit.regions.Region sel = session.getSelection(wePlayer.getWorld());
            com.sk89q.worldedit.math.BlockVector3 min = sel.getMinimumPoint();
            com.sk89q.worldedit.math.BlockVector3 max = sel.getMaximumPoint();

            org.bukkit.World bw = BukkitAdapter.adapt(wePlayer.getWorld());
            return new Region(
                    bw.getName(),
                    min.x(), min.y(), min.z(),
                    max.x(), max.y(), max.z()
            );
        } catch (Throwable t) {
            return null;
        }
    }

    private CompletableFuture<Suggestions> suggestParcourIds(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
        String rem = b.getRemaining().toLowerCase(java.util.Locale.ROOT);
        for (ParcourDefinition d : registry.all()) {
            String id = d.id();
            if (id.toLowerCase(java.util.Locale.ROOT).startsWith(rem)) b.suggest(id);
        }
        return b.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestRegionKeysForParcour(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
        String id = null;
        try { id = StringArgumentType.getString(ctx, "parcourId"); } catch (IllegalArgumentException ignored) { }
        if ("start".startsWith(b.getRemaining().toLowerCase(java.util.Locale.ROOT))) b.suggest("START");
        if ("end".startsWith(b.getRemaining().toLowerCase(java.util.Locale.ROOT))) b.suggest("END");

        if (id != null) {
            registry.get(id).ifPresent(def -> {
                for (Integer ord : def.orders()) {
                    String s = String.valueOf(ord);
                    if (s.startsWith(b.getRemaining())) b.suggest(s);
                }
            });
        } else {
            for (int i = 0; i <= 10; i++) {
                String s = String.valueOf(i);
                if (s.startsWith(b.getRemaining())) b.suggest(s);
            }
        }
        return b.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestStartOrNumbersForParcour(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
        String id = null;
        try { id = StringArgumentType.getString(ctx, "parcourId"); } catch (IllegalArgumentException ignored) { }
        if ("start".startsWith(b.getRemaining().toLowerCase(java.util.Locale.ROOT))) b.suggest("START");

        if (id != null) {
            registry.get(id).ifPresent(def -> {
                for (Integer ord : def.orders()) {
                    String s = String.valueOf(ord);
                    if (s.startsWith(b.getRemaining())) b.suggest(s);
                }
            });
        } else {
            for (int i = 0; i <= 10; i++) {
                String s = String.valueOf(i);
                if (s.startsWith(b.getRemaining())) b.suggest(s);
            }
        }
        return b.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestSoundTypes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
        String rem = b.getRemaining().toLowerCase(java.util.Locale.ROOT);
        if ("checkpoint".startsWith(rem)) b.suggest("CHECKPOINT");
        if ("end".startsWith(rem)) b.suggest("END");
        return b.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestSoundNames(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
        String rem = b.getRemaining().toLowerCase(Locale.ROOT);
        if ("none".startsWith(rem)) b.suggest("NONE");
        if ("null".startsWith(rem)) b.suggest("NULL");
        if ("-".startsWith(rem)) b.suggest("-");

        var reg = BukkitRegistry.soundRegistry();
        reg.forEach(snd -> {
            var key = reg.getKey(snd);
            if (key == null) return; // sound may be anonymous
            String id = key.toString(); // e.g., minecraft:entity.player.levelup
            if (id.startsWith(rem) || id.contains(rem)) b.suggest(id);
        });
        return b.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestParticleNames(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
        String rem = b.getRemaining().toLowerCase(Locale.ROOT);
        if ("none".startsWith(rem)) b.suggest("NONE");
        if ("null".startsWith(rem)) b.suggest("NULL");
        if ("-".startsWith(rem)) b.suggest("-");

        var reg = BukkitRegistry.particleRegistry();
        reg.forEach(p -> {
            var key = reg.getKey(p);
            if (key == null) return;
            String id = key.toString(); // e.g., minecraft:dust
            if (id.startsWith(rem) || id.contains(rem)) b.suggest(id);
        });
        return b.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestEffectNames(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
        String rem = b.getRemaining().toLowerCase(Locale.ROOT);
        if ("none".startsWith(rem)) b.suggest("NONE");
        if ("null".startsWith(rem)) b.suggest("NULL");
        if ("-".startsWith(rem)) b.suggest("-");

        var reg = BukkitRegistry.mobEffectRegistry();
        reg.forEach(t -> {
            var key = reg.getKey(t);
            if (key == null) return;
            String id = key.toString(); // e.g., minecraft:speed
            if (id.startsWith(rem) || id.contains(rem)) b.suggest(id);
        });
        return b.buildFuture();
    }

    private void sendInfo(CommandSender sender, ParcourDefinition def) {
        var lh = feature.getLocalizationHandler();
        sender.sendMessage(lh.getMessage("parcour.admin.info.header")
                .with("id", def.id()).forAudience(sender).build());

        sendProp(sender, "Leave Location", def.leaveSpawn().map(l ->
                l.getWorld().getName() + " " + fmt(l.getX()) + " " + fmt(l.getY()) + " " + fmt(l.getZ()) + " " + fmt(l.getYaw()) + " " + fmt(l.getPitch())
        ).orElse("-"));
        sendProp(sender, "Finish Location", def.finishSpawn().map(l ->
                l.getWorld().getName() + " " + fmt(l.getX()) + " " + fmt(l.getY()) + " " + fmt(l.getZ()) + " " + fmt(l.getYaw()) + " " + fmt(l.getPitch())
        ).orElse("-"));

        sendProp(sender, "Notify Progress", String.valueOf(def.notifyProgress()));
        sendProp(sender, "Use ActionBar", String.valueOf(def.useActionBar()));
        sendProp(sender, "Sound CHECKPOINT", def.checkpointSoundName().orElse("-"));
        sendProp(sender, "Sound END", def.endSoundName().orElse("-"));
        sendProp(sender, "Region Highlight Particle", def.regionHighlightParticleName().orElse("-"));
        sendProp(sender, "Finish Teleport Delay (s)", def.finishTeleportDelaySeconds() > 0
                ? String.valueOf(def.finishTeleportDelaySeconds()) : "-");
        sendProp(sender, "Hunger Enabled", String.valueOf(def.hungerEnabled()));
        sendProp(sender, "Damage Enabled", String.valueOf(def.damageEnabled()));
        sendProp(sender, "Checkpoint Teleport Cooldown (s)", String.valueOf(def.checkpointCooldownSeconds()));
        sendProp(sender, "Start Countdown (s)", String.valueOf(def.startCountdownSeconds()));
        sendProp(sender, "Start Position", def.startPosition().map(l ->
                l.getWorld().getName() + " " + fmt(l.getX()) + " " + fmt(l.getY()) + " " + fmt(l.getZ()) + " " + fmt(l.getYaw()) + " " + fmt(l.getPitch())
        ).orElse("-"));
        sendProp(sender, "StartKit Items", String.valueOf(def.startKitEncoded().size()));

        sendProp(sender, "Effect", def.effectTypeName().map(n -> n + " amp=" + def.effectAmplifier()).orElse("-"));

        def.startRegion().ifPresentOrElse(pr -> {
            String regionStr = pr.region().map(r ->
                    r.worldName() + " [" + r.minX() + "," + r.minY() + "," + r.minZ() + "] -> [" + r.maxX() + "," + r.maxY() + "," + r.maxZ() + "]"
            ).orElse("-");
            sendProp(sender, "START", "restore=" + pr.restoreCheckpoint());
            sendProp(sender, "Region START", regionStr);
            pr.explicitRestore(feature.getPlugin().getServer()).ifPresentOrElse(l ->
                            sendProp(sender, "RestoreLoc START",
                                    l.getWorld().getName() + " " + fmt(l.getX()) + " " + fmt(l.getY()) + " " + fmt(l.getZ()) + " " + fmt(l.getYaw()) + " " + fmt(l.getPitch())),
                    () -> sendProp(sender, "RestoreLoc START", "-"));
            if (!pr.commands().isEmpty()) sendProp(sender, "Commands START", String.join(" || ", pr.commands()));
        }, () -> sendProp(sender, "START", "-"));

        for (Integer ord : def.orders()) {
            ParcourRegion pr = def.checkpoint(ord).orElse(null);
            if (pr == null) continue;
            String regionStr = pr.region().map(r ->
                    r.worldName() + " [" + r.minX() + "," + r.minY() + "," + r.minZ() + "] -> [" + r.maxX() + "," + r.maxY() + "," + r.maxZ() + "]"
            ).orElse("-");
            sendProp(sender, "Order " + ord, "CHECKPOINT / restore=" + pr.restoreCheckpoint());
            sendProp(sender, "Region " + ord, regionStr);
            pr.explicitRestore(feature.getPlugin().getServer()).ifPresentOrElse(l ->
                            sendProp(sender, "RestoreLoc " + ord,
                                    l.getWorld().getName() + " " + fmt(l.getX()) + " " + fmt(l.getY()) + " " + fmt(l.getZ()) + " " + fmt(l.getYaw()) + " " + fmt(l.getPitch())),
                    () -> sendProp(sender, "RestoreLoc " + ord, "-"));
            if (!pr.commands().isEmpty()) sendProp(sender, "Commands " + ord, String.join(" || ", pr.commands()));
        }

        def.endRegion().ifPresentOrElse(pr -> {
            String regionStr = pr.region().map(r ->
                    r.worldName() + " [" + r.minX() + "," + r.minY() + "," + r.minZ() + "] -> [" + r.maxX() + "," + r.maxY() + "," + r.maxZ() + "]"
            ).orElse("-");
            sendProp(sender, "END", "-");
            sendProp(sender, "Region END", regionStr);
            if (!pr.commands().isEmpty()) sendProp(sender, "Commands END", String.join(" || ", pr.commands()));
        }, () -> sendProp(sender, "END", "-"));
    }

    private void sendProp(CommandSender sender, String key, String value) {
        sender.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.info.prop")
                .with("key", key)
                .with("value", value)
                .forAudience(sender).build());
    }

    private String fmt(double d) {
        return (Math.abs(d - Math.rint(d)) < 1e-9) ? String.valueOf((long) Math.rint(d)) : String.format(java.util.Locale.ROOT, "%.3f", d);
    }

    private String fmt(float f) { return String.format(java.util.Locale.ROOT, "%.1f", f); }

    private static boolean isClearKeyword(String s) {
        String v = s.trim();
        return v.equalsIgnoreCase("NONE") || v.equalsIgnoreCase("NULL") || v.equals("-");
    }

}
