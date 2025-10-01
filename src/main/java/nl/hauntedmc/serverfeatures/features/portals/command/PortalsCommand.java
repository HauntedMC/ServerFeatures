package nl.hauntedmc.serverfeatures.features.portals.command;

import nl.hauntedmc.serverfeatures.commands.CommandSpec;
import nl.hauntedmc.serverfeatures.commands.FeatureCommand;
import nl.hauntedmc.serverfeatures.features.portals.Portals;
import nl.hauntedmc.serverfeatures.features.portals.internal.PortalsHandler;
import nl.hauntedmc.serverfeatures.features.portals.model.CommandExecutor;
import nl.hauntedmc.serverfeatures.features.portals.model.PortalDefinition;
import nl.hauntedmc.serverfeatures.features.portals.model.Region;
import nl.hauntedmc.serverfeatures.features.portals.registry.PortalRegistry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PortalsCommand extends FeatureCommand {

    private static final String ADMIN_PERM = "serverfeatures.feature.portals.admin";

    private final Portals feature;
    private final PortalRegistry registry;
    private final PortalsHandler handler;

    public PortalsCommand(Portals feature, PortalsHandler handler) {
        super(new CommandSpec.Builder("portals")
                .permission(ADMIN_PERM)
                .build());
        this.feature = feature;
        this.registry = feature.getRegistry();
        this.handler = handler;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission(ADMIN_PERM)) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission").forAudience(sender).build());
            return true;
        }

        if (args.length < 1) {
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (args.length < 2) { usage(sender, "create <id>"); return true; }
                String id = args[1];
                if (handler.createPortal(id)) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.created").withPlaceholders(Map.of("id", id)).forAudience(sender).build());
                } else {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.already_exists").withPlaceholders(Map.of("id", id)).forAudience(sender).build());
                }
                return true;
            }
            case "delete" -> {
                if (args.length < 2) { usage(sender, "delete <id>"); return true; }
                String id = args[1];
                if (handler.delete(id)) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.deleted").withPlaceholders(Map.of("id", id)).forAudience(sender).build());
                } else {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.not_found").withPlaceholders(Map.of("id", id)).forAudience(sender).build());
                }
                return true;
            }
            case "select" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(sender).build()); return true; }
                if (args.length < 2) { usage(sender, "select <id>"); return true; }
                String id = args[1];
                if (handler.selectPortal(p, id)) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.select.current").withPlaceholders(Map.of("id", id)).forAudience(sender).build());
                } else {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.not_found").withPlaceholders(Map.of("id", id)).forAudience(sender).build());
                }
                return true;
            }
            case "wand" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(sender).build()); return true; }
                handler.giveWand(p);
                sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.wand.given").forAudience(sender).build());
                return true;
            }
            case "saveregion" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(sender).build()); return true; }
                var sel = handler.selection(p);
                if (sel.selectedPortalId == null) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.select.none").forAudience(sender).build());
                    return true;
                }
                if (!sel.hasBoth()) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.region.missing").forAudience(sender).build());
                    return true;
                }
                if (!Objects.equals(sel.world1, sel.world2)) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.region.world_mismatch").forAudience(sender).build());
                    return true;
                }
                boolean ok = handler.saveRegionToSelected(p);
                if (ok) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.region.saved").withPlaceholders(Map.of("id", sel.selectedPortalId)).forAudience(sender).build());
                } else {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.not_found").withPlaceholders(Map.of("id", sel.selectedPortalId)).forAudience(sender).build());
                }
                return true;
            }
            case "setmode" -> {
                if (args.length < 3) { usage(sender, "setmode <id> <teleport|command|server>"); return true; }
                String id = args[1];
                String mode = args[2];
                if (handler.setMode(id, mode)) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.mode.set")
                            .withPlaceholders(Map.of("id", id, "mode", mode.toUpperCase(Locale.ROOT)))
                            .forAudience(sender).build());
                } else {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.not_found").withPlaceholders(Map.of("id", id)).forAudience(sender).build());
                }
                return true;
            }
            case "setteleport" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(sender).build()); return true; }
                if (args.length < 2) { usage(sender, "setteleport <id>"); return true; }
                String id = args[1];

                boolean ok = handler.setTeleportFromPlayer(id, p);
                if (ok) {
                    var l = p.getLocation();
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.teleport.set")
                            .withPlaceholders(Map.of(
                                    "world", l.getWorld().getName(),
                                    "x", format(l.getX()), "y", format(l.getY()), "z", format(l.getZ()),
                                    "yaw", format(l.getYaw()), "pitch", format(l.getPitch())))
                            .forAudience(sender).build());
                } else {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.not_found").withPlaceholders(Map.of("id", id)).forAudience(sender).build());
                }
                return true;
            }
            case "setcommand" -> {
                if (args.length < 4) { usage(sender, "setcommand <id> <player|console> <command...>"); return true; }
                String id = args[1];
                CommandExecutor ex = CommandExecutor.fromString(args[2], CommandExecutor.CONSOLE);
                String cmd = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                boolean ok = handler.setCommand(id, stripSlash(cmd), ex);
                if (ok) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.command.set")
                            .withPlaceholders(Map.of("command", cmd, "executor", ex.name().toLowerCase()))
                            .forAudience(sender).build());
                } else {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.not_found").withPlaceholders(Map.of("id", id)).forAudience(sender).build());
                }
                return true;
            }
            case "setserver" -> {
                if (args.length < 3) { usage(sender, "setserver <id> <serverName>"); return true; }
                String id = args[1];
                String serverName = args[2];
                boolean ok = handler.setServer(id, serverName);
                if (ok) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.server.set")
                            .withPlaceholders(Map.of("id", id, "server", serverName))
                            .forAudience(sender).build());
                } else {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.not_found")
                            .withPlaceholders(Map.of("id", id))
                            .forAudience(sender).build());
                }
                return true;
            }
            case "list" -> {
                var all = registry.all();
                sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.list.header")
                        .withPlaceholders(Map.of("count", String.valueOf(all.size())))
                        .forAudience(sender).build());
                all.forEach(def -> {
                    String world = def.region().map(Region::worldName).orElse("-");
                    String mode = def.mode().name();
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.list.entry")
                            .withPlaceholders(Map.of("id", def.id(), "world", world, "mode", mode))
                            .forAudience(sender).build());
                });
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    private void usage(CommandSender s, String u) {
        s.sendMessage("§cGebruik: /portals " + u);
    }

    private static String format(double d) {
        return (Math.abs(d - Math.rint(d)) < 1e-9) ? String.valueOf((long)Math.rint(d)) : String.format(Locale.ROOT, "%.3f", d);
    }

    private static String format(float f) {
        return String.format(Locale.ROOT, "%.1f", f);
    }

    private static String stripSlash(String s) {
        return s.startsWith("/") ? s.substring(1) : s;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String @NotNull [] args) {
        if (!sender.hasPermission(ADMIN_PERM)) return Collections.emptyList();

        if (args.length == 1) {
            return Stream.of("create","delete","select","wand","saveregion","setmode","setteleport","setcommand","setserver","list")
                    .filter(opt -> opt.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && Stream.of("delete","select","setmode","setteleport","setcommand","setserver").anyMatch(args[0]::equalsIgnoreCase)) {
            return registry.all().stream().map(PortalDefinition::id).filter(id -> id.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("setmode")) {
            return Stream.of("teleport","command","server").filter(opt -> opt.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("setcommand")) {
            return Stream.of("player","console").filter(opt -> opt.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        }

        return Collections.emptyList();
    }
}
