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
import nl.hauntedmc.serverfeatures.features.parcour.model.Region;
import nl.hauntedmc.serverfeatures.features.parcour.registry.ParcourRegistry;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class ParcourCommand implements BrigadierCommand {

    // Permissions
    private static final String BASE      = "serverfeatures.feature.parcour.use";
    private static final String P_START   = "serverfeatures.feature.parcour.command.start";
    private static final String P_LEAVE   = "serverfeatures.feature.parcour.command.leave";
    private static final String P_CKPT    = "serverfeatures.feature.parcour.command.checkpoint";
    private static final String P_ADMIN   = "serverfeatures.feature.parcour.admin";

    private final Parcour feature;
    private final ParcourRegistry registry;
    private final ParcourHandler handler;

    public ParcourCommand(Parcour feature, ParcourHandler handler) {
        this.feature = feature;
        this.registry = feature.getRegistry();
        this.handler = handler;
    }

    @Override public @NotNull String name() { return "parcour"; }
    @Override public String description() { return "Play and manage parcours. Player: start/leave/checkpoint. Admin: create, add regions, etc."; }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name())
                .requires(src -> {
                    CommandSender s = src.getSender();
                    return s.hasPermission(BASE) || s.hasPermission(P_ADMIN);
                })

                // ===== Player =====
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
                            // Enforce cooldown for command
                            handler.teleportToCheckpoint(p, true);
                            return 1;
                        })
                )

                // ===== Admin =====

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

                // ADD (END without restore)
                .then(Commands.literal("add")
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

                // delete/setrestore/addcmd/clearcmds
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
                                                        .with("type", key.toUpperCase(Locale.ROOT))
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

                .then(Commands.literal("addcmd")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
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
                                                }))))
                )

                .then(Commands.literal("clearcmds")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
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

                // setrestorelocation <parcourId> <START|number>
                .then(Commands.literal("setrestorelocation")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(this::suggestStartOrNumbersForParcour)
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

                // setprogressnotify <parcourId> <true|false>
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

                // setsound <parcourId> <CHECKPOINT|END> <SOUND_NAME|NONE>
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
                                                    String soundArg = StringArgumentType.getString(ctx, "sound").toUpperCase(Locale.ROOT);

                                                    boolean clear = soundArg.equals("NONE") || soundArg.equals("NULL") || soundArg.equals("-");
                                                    if (!clear) {
                                                        try {
                                                            Sound.valueOf(soundArg); // validate
                                                        } catch (IllegalArgumentException ex) {
                                                            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.sound.invalid")
                                                                    .with("sound", soundArg).forAudience(s).build());
                                                            return 1;
                                                        }
                                                    }

                                                    boolean ok;
                                                    if ("CHECKPOINT".equals(type)) {
                                                        ok = handler.setCheckpointSound(id, clear ? null : soundArg);
                                                    } else if ("END".equals(type)) {
                                                        ok = handler.setEndSound(id, clear ? null : soundArg);
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
                                                                .with("type", type).with("sound", soundArg).forAudience(s).build());
                                                    }
                                                    return 1;
                                                }))))
                )

                // setactionbar <parcourId> <true|false>
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

                // setfinishdelay <parcourId> <seconds>
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

                // NEW: setregionparticle <parcourId> <PARTICLE|NONE>
                .then(Commands.literal("setregionparticle")
                        .requires(src -> src.getSender().hasPermission(P_ADMIN))
                        .then(Commands.argument("parcourId", StringArgumentType.word())
                                .suggests(this::suggestParcourIds)
                                .then(Commands.argument("particle", StringArgumentType.word())
                                        .suggests(this::suggestParticleNames)
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getSender();
                                            String id = StringArgumentType.getString(ctx, "parcourId");
                                            String arg = StringArgumentType.getString(ctx, "particle").toUpperCase(Locale.ROOT);

                                            boolean clear = arg.equals("NONE") || arg.equals("NULL") || arg.equals("-");
                                            String value = null;

                                            if (!clear) {
                                                try {
                                                    Particle.valueOf(arg); // validate
                                                    value = arg;
                                                } catch (IllegalArgumentException ex) {
                                                    // Use a simple inline message to avoid requiring new localization keys
                                                    s.sendMessage("§cOngeldige particle: §f" + arg + "§c.");
                                                    return 1;
                                                }
                                            }

                                            boolean ok = handler.setRegionParticle(id, value);
                                            if (!ok) {
                                                s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.not_found")
                                                        .with("name", id).forAudience(s).build());
                                                return 1;
                                            }

                                            if (clear) {
                                                s.sendMessage("§aRegion-highlight particle verwijderd.");
                                            } else {
                                                s.sendMessage("§aRegion-highlight particle ingesteld op §f" + value + "§a.");
                                            }
                                            return 1;
                                        })))
                )

                // NEW: sethunger <parcourId> <true|false>
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

                // NEW: setdamage <parcourId> <true|false>
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

                // NEW: setcheckpointcooldown <parcourId> <seconds>
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
                                        .with("regions", String.valueOf(d.totalRegions()))
                                        .forAudience(s).build());
                            }
                            return 1;
                        })
                )

                .executes(ctx -> {
                    CommandSender s = ctx.getSource().getSender();
                    if (s.hasPermission(BASE)) {
                        s.sendMessage("§7Speler: §f/parcour start <naam>§7, §f/parcour leave§7, §f/parcour checkpoint");
                    }
                    if (s.hasPermission(P_ADMIN)) {
                        s.sendMessage("§7Admin: §f/parcour create|delete|select|wand|add <start|checkpoint|end> ...|deleteregion|setrestore|addcmd|clearcmds|setexitspawn|setrestorelocation|setprogressnotify|setsound|setactionbar|setfinishdelay|setregionparticle|sethunger|setdamage|setcheckpointcooldown|info|list");
                    }
                    return 1;
                });

        return root.build();
    }

    // ===== Exec helpers =====

    private int execAddStart(CommandContext<CommandSourceStack> ctx, boolean hasRestoreArg) {
        CommandSender s = ctx.getSource().getSender();
        if (!(s instanceof Player p)) {
            s.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(s).build());
            return 1;
        }
        String id = StringArgumentType.getString(ctx, "parcourId");
        boolean restore = hasRestoreArg && BoolArgumentType.getBool(ctx, "restore");

        ParcourHandler.Selection sel = handler.selection(p);
        if (!sel.hasBoth()) {
            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.missing").forAudience(s).build());
            return 1;
        }
        Region region = sel.toRegionOrNull();
        if (region == null || !Objects.equals(sel.world1, sel.world2)) {
            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.world_mismatch").forAudience(s).build());
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

        ParcourHandler.Selection sel = handler.selection(p);
        if (!sel.hasBoth()) {
            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.missing").forAudience(s).build());
            return 1;
        }
        Region region = sel.toRegionOrNull();
        if (region == null || !Objects.equals(sel.world1, sel.world2)) {
            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.world_mismatch").forAudience(s).build());
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

        ParcourHandler.Selection sel = handler.selection(p);
        if (!sel.hasBoth()) {
            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.missing").forAudience(s).build());
            return 1;
        }
        Region region = sel.toRegionOrNull();
        if (region == null || !Objects.equals(sel.world1, sel.world2)) {
            s.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.region.world_mismatch").forAudience(s).build());
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

    // ===== Suggestions =====

    private CompletableFuture<Suggestions> suggestParcourIds(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
        String rem = b.getRemaining().toLowerCase(Locale.ROOT);
        for (ParcourDefinition d : registry.all()) {
            String id = d.id();
            if (id.toLowerCase(Locale.ROOT).startsWith(rem)) b.suggest(id);
        }
        return b.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestRegionKeysForParcour(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
        String id = null;
        try { id = StringArgumentType.getString(ctx, "parcourId"); } catch (IllegalArgumentException ignored) {}
        String rem = b.getRemaining().toLowerCase(Locale.ROOT);

        if ("start".startsWith(rem)) b.suggest("START");
        if ("end".startsWith(rem)) b.suggest("END");

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

    // suggestions for setrestorelocation (no END)
    private CompletableFuture<Suggestions> suggestStartOrNumbersForParcour(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
        String id = null;
        try { id = StringArgumentType.getString(ctx, "parcourId"); } catch (IllegalArgumentException ignored) {}
        String rem = b.getRemaining().toLowerCase(Locale.ROOT);

        if ("start".startsWith(rem)) b.suggest("START");

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

    // suggestions for setsound type (CHECKPOINT|END)
    private CompletableFuture<Suggestions> suggestSoundTypes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
        String rem = b.getRemaining().toLowerCase(Locale.ROOT);
        if ("checkpoint".startsWith(rem)) b.suggest("CHECKPOINT");
        if ("end".startsWith(rem)) b.suggest("END");
        return b.buildFuture();
    }

    // suggestions for sound names (Bukkit enum names) + NONE/NULL/-
    private CompletableFuture<Suggestions> suggestSoundNames(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
        String rem = b.getRemaining().toUpperCase(Locale.ROOT);

        // Always offer clear options
        if ("NONE".startsWith(rem)) b.suggest("NONE");
        if ("NULL".startsWith(rem)) b.suggest("NULL");
        if ("-".startsWith(rem)) b.suggest("-");

        // Suggest enum constants
        for (Sound s : Sound.values()) {
            String name = s.name(); // already UPPER_CASE_WITH_UNDERSCORES
            if (name.startsWith(rem)) {
                b.suggest(name);
            }
        }
        return b.buildFuture();
    }

    // NEW: suggestions for particle names + NONE/NULL/-
    private CompletableFuture<Suggestions> suggestParticleNames(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
        String rem = b.getRemaining().toUpperCase(Locale.ROOT);

        if ("NONE".startsWith(rem)) b.suggest("NONE");
        if ("NULL".startsWith(rem)) b.suggest("NULL");
        if ("-".startsWith(rem)) b.suggest("-");

        for (Particle p : Particle.values()) {
            String name = p.name();
            if (name.startsWith(rem)) {
                b.suggest(name);
            }
        }
        return b.buildFuture();
    }

    private void sendInfo(CommandSender sender, ParcourDefinition def) {
        var lh = feature.getLocalizationHandler();
        sender.sendMessage(lh.getMessage("parcour.admin.info.header")
                .with("id", def.id()).forAudience(sender).build());

        sendProp(sender, "Exit Spawn", def.exitSpawn().map(l ->
                l.getWorld().getName() + " " + fmt(l.getX()) + " " + fmt(l.getY()) + " " + fmt(l.getZ()) + " " + fmt(l.getYaw()) + " " + fmt(l.getPitch())
        ).orElse("-"));

        // progress toggle
        sendProp(sender, "Notify Progress", String.valueOf(def.notifyProgress()));

        // actionbar toggle
        sendProp(sender, "Use ActionBar", String.valueOf(def.useActionBar()));

        // sounds
        sendProp(sender, "Sound CHECKPOINT", def.checkpointSoundName().orElse("-"));
        sendProp(sender, "Sound END", def.endSoundName().orElse("-"));

        // NEW: region highlight particle
        sendProp(sender, "Region Highlight Particle", def.regionHighlightParticleName().orElse("-"));

        // finish teleport delay
        sendProp(sender, "Finish Teleport Delay (s)", def.finishTeleportDelaySeconds() > 0
                ? String.valueOf(def.finishTeleportDelaySeconds()) : "-");

        // NEW: toggles
        sendProp(sender, "Hunger Enabled", String.valueOf(def.hungerEnabled()));
        sendProp(sender, "Damage Enabled", String.valueOf(def.damageEnabled()));

        // NEW: checkpoint cooldown
        sendProp(sender, "Checkpoint Teleport Cooldown (s)", String.valueOf(def.checkpointCooldownSeconds()));

        // START
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

        // Checkpoints by order
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

        // END
        def.endRegion().ifPresentOrElse(pr -> {
            String regionStr = pr.region().map(r ->
                    r.worldName() + " [" + r.minX() + "," + r.minY() + "," + r.minZ() + "] -> [" + r.maxX() + "," + r.maxY() + "," + r.maxZ() + "]"
            ).orElse("-");
            sendProp(sender, "END", "-"); // no restore on END
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
        return (Math.abs(d - Math.rint(d)) < 1e-9) ? String.valueOf((long) Math.rint(d)) : String.format(Locale.ROOT, "%.3f", d);
    }

    private String fmt(float f) {
        return String.format(Locale.ROOT, "%.1f", f);
    }
}
