package nl.hauntedmc.serverfeatures.features.portals.command;

import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.api.command.meta.CommandMeta;
import nl.hauntedmc.serverfeatures.features.portals.Portals;
import nl.hauntedmc.serverfeatures.features.portals.internal.PortalsHandler;
import nl.hauntedmc.serverfeatures.features.portals.model.CommandExecutor;
import nl.hauntedmc.serverfeatures.features.portals.model.PortalDefinition;
import nl.hauntedmc.serverfeatures.features.portals.registry.PortalRegistry;
import nl.hauntedmc.serverfeatures.features.portals.util.RegistryUtil;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Admin command for portals. Uses RegistryUtil for sound/particle parsing & completions.
 */
public class PortalsCommand extends FeatureCommand {

    private static final String ADMIN_PERM = "serverfeatures.feature.portals.admin";

    private final Portals feature;
    private final PortalRegistry registry;
    private final PortalsHandler handler;

    // Cached list of placeable blocks for tab-complete
    private static final List<String> PLACEABLE_BLOCKS = computePlaceableBlocks();

    public PortalsCommand(Portals feature, PortalsHandler handler) {
        super(new CommandMeta.Builder("portals").permission(ADMIN_PERM).build());
        this.feature = feature;
        this.registry = feature.getRegistry();
        this.handler = handler;
    }

    private static List<String> computePlaceableBlocks() {
        List<String> list = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.isBlock()) list.add(m.name().toLowerCase(Locale.ROOT));
        }
        if (!list.contains("nether_portal")) list.add("nether_portal");
        return Collections.unmodifiableList(list);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission(ADMIN_PERM)) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission").forAudience(sender).build());
            return true;
        }
        if (args.length < 1) return true;

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (args.length < 2) {
                    usage(sender, "create <id>");
                    return true;
                }
                String id = args[1];
                if (handler.createPortal(id)) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.created")
                            .with("id", id)
                            .forAudience(sender)
                            .build());
                } else {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.already_exists")
                            .with("id", id)
                            .forAudience(sender)
                            .build());
                }
                return true;
            }
            case "delete" -> {
                if (args.length < 2) {
                    usage(sender, "delete <id>");
                    return true;
                }
                String id = args[1];
                if (handler.delete(id)) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.deleted")
                            .with("id", id)
                            .forAudience(sender)
                            .build());
                } else {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.not_found")
                            .with("id", id)
                            .forAudience(sender)
                            .build());
                }
                return true;
            }
            case "select" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(sender).build());
                    return true;
                }
                if (args.length < 2) {
                    usage(sender, "select <id>");
                    return true;
                }
                String id = args[1];
                if (handler.selectPortal(p, id)) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.select.current").with("id", id).forAudience(sender).build());
                } else {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.not_found").with("id", id).forAudience(sender).build());
                }
                return true;
            }
            case "wand" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(sender).build());
                    return true;
                }
                handler.giveWand(p);
                sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.wand.given").forAudience(sender).build());
                return true;
            }
            case "saveregion" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(sender).build());
                    return true;
                }
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
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.region.saved").with("id", sel.selectedPortalId).forAudience(sender).build());
                } else {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.not_found").with("id", sel.selectedPortalId).forAudience(sender).build());
                }
                return true;
            }
            case "setmode" -> {
                if (args.length < 3) {
                    usage(sender, "setmode <id> <teleport|command|server>");
                    return true;
                }
                String id = args[1];
                String mode = args[2];
                if (handler.setMode(id, mode)) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.mode.set")
                            .with("id", id)
                            .with("mode", mode.toUpperCase(Locale.ROOT))
                            .forAudience(sender).build());
                } else {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.not_found").with("id", id).forAudience(sender).build());
                }
                return true;
            }
            case "setteleport" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(sender).build());
                    return true;
                }
                if (args.length < 2) {
                    usage(sender, "setteleport <id>");
                    return true;
                }
                String id = args[1];
                boolean ok = handler.setTeleportFromPlayer(id, p);
                if (ok) {
                    var l = p.getLocation();
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.teleport.set")
                            .with("world", l.getWorld().getName())
                            .with("x", format(l.getX()))
                            .with("y", format(l.getY()))
                            .with("z", format(l.getZ()))
                            .with("yaw", format(l.getYaw()))
                            .with("pitch", format(l.getPitch()))
                            .forAudience(sender).build());
                } else {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.not_found").with("id", id).forAudience(sender).build());
                }
                return true;
            }
            case "setcommand" -> {
                if (args.length < 4) {
                    usage(sender, "setcommand <id> <player|console> <command...>");
                    return true;
                }
                String id = args[1];
                CommandExecutor ex = CommandExecutor.fromString(args[2], CommandExecutor.CONSOLE);
                String cmd = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                boolean ok = handler.setCommand(id, stripSlash(cmd), ex);
                if (ok) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.command.set")
                            .with("command", cmd)
                            .with("executor", ex.name().toLowerCase())
                            .forAudience(sender).build());
                } else {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.not_found").with("id", id).forAudience(sender).build());
                }
                return true;
            }
            case "setserver" -> {
                if (args.length < 3) {
                    usage(sender, "setserver <id> <serverName>");
                    return true;
                }
                String id = args[1];
                String serverName = args[2];
                boolean ok = handler.setServer(id, serverName);
                if (ok) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.server.set")
                            .with("id", id)
                            .with("server", serverName)
                            .forAudience(sender).build());
                } else {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.not_found").with("id", id).forAudience(sender).build());
                }
                return true;
            }
            case "setblock" -> {
                if (args.length < 3) {
                    usage(sender, "setblock <id> <material|none>");
                    return true;
                }
                String id = args[1];
                String blockName = args[2];
                PortalsHandler.ExclusiveBlockResult res = handler.setExclusiveBlock(id, blockName);
                switch (res) {
                    case NOT_FOUND ->
                            sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.not_found")
                                    .with("id", id).forAudience(sender).build());
                    case INVALID ->
                            sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.block.invalid")
                                    .with("block", blockName).forAudience(sender).build());
                    case CLEARED ->
                            sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.block.cleared")
                                    .with("id", id).forAudience(sender).build());
                    case SET -> sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.block.set")
                            .with("id", id)
                            .with("block", blockName.toUpperCase(Locale.ROOT))
                            .forAudience(sender)
                            .build());
                }
                return true;
            }
            case "setsound" -> {
                if (args.length < 3) {
                    usage(sender, "setsound <id> <minecraft:key|none> [delayTicks]");
                    return true;
                }
                String id = args[1];
                Optional<PortalDefinition> defOpt = registry.get(id);
                if (defOpt.isEmpty()) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.not_found")
                            .with("id", id).forAudience(sender).build());
                    return true;
                }
                PortalDefinition def = defOpt.get();
                String soundArg = args[2];

                if (soundArg.equalsIgnoreCase("none") || soundArg.equalsIgnoreCase("clear")) {
                    def.clearSound();
                    registry.savePortal(def);
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.sound.cleared")
                            .with("id", id).forAudience(sender).build());
                    return true;
                }

                Optional<Sound> resolved = RegistryUtil.resolveSound(soundArg);
                if (resolved.isEmpty()) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.sound.invalid")
                            .with("sound", soundArg).forAudience(sender).build());
                    return true;
                }

                int delay = 0;
                if (args.length >= 4) {
                    try {
                        delay = Math.max(0, Integer.parseInt(args[3]));
                    } catch (NumberFormatException ignored) {
                    }
                }

                def.setSound(resolved.get(), delay);
                registry.savePortal(def);

                sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.sound.set")
                        .with("id", id)
                        .with("sound", RegistryUtil.keyString(resolved.get()))
                        .with("delay", String.valueOf(delay))
                        .forAudience(sender).build());
                return true;
            }
            case "setparticle" -> {
                if (args.length < 3) {
                    usage(sender, "setparticle <id> <minecraft:key|none> [delayTicks]");
                    return true;
                }
                String id = args[1];
                Optional<PortalDefinition> defOpt = registry.get(id);
                if (defOpt.isEmpty()) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.not_found")
                            .with("id", id).forAudience(sender).build());
                    return true;
                }
                PortalDefinition def = defOpt.get();
                String particleArg = args[2];

                if (particleArg.equalsIgnoreCase("none") || particleArg.equalsIgnoreCase("clear")) {
                    def.clearParticle();
                    registry.savePortal(def);
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.particle.cleared")
                            .with("id", id).forAudience(sender).build());
                    return true;
                }

                Optional<Particle> resolved = RegistryUtil.resolveParticle(particleArg);
                if (resolved.isEmpty()) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.particle.invalid")
                            .with("particle", particleArg).forAudience(sender).build());
                    return true;
                }

                int delay = 0;
                if (args.length >= 4) {
                    try {
                        delay = Math.max(0, Integer.parseInt(args[3]));
                    } catch (NumberFormatException ignored) {
                    }
                }

                def.setParticle(resolved.get(), delay);
                registry.savePortal(def);

                sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.particle.set")
                        .with("id", id)
                        .with("particle", RegistryUtil.keyString(resolved.get()))
                        .with("delay", String.valueOf(delay))
                        .forAudience(sender).build());
                return true;
            }

            case "info" -> {
                if (args.length < 2) {
                    usage(sender, "info <id>");
                    return true;
                }
                String id = args[1];
                Optional<PortalDefinition> defOpt = registry.get(id);
                if (defOpt.isEmpty()) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.not_found")
                            .with("id", id).forAudience(sender).build());
                    return true;
                }
                sendPortalInfo(sender, defOpt.get());
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
        return (Math.abs(d - Math.rint(d)) < 1e-9) ? String.valueOf((long) Math.rint(d)) : String.format(Locale.ROOT, "%.2f", d);
    }

    private static String format(float f) {
        return String.format(Locale.ROOT, "%.1f", f);
    }

    private static String stripSlash(String s) {
        return s.startsWith("/") ? s.substring(1) : s;
    }

    private String dash() {
        return "-";
    }

    /**
     * Sends a compact info sheet for a single portal.
     */
    private void sendPortalInfo(CommandSender sender, PortalDefinition def) {
        var lh = feature.getLocalizationHandler();

        sender.sendMessage(lh.getMessage("portals.info.header")
                .with("id", def.id())
                .forAudience(sender)
                .build());

        // Mode
        sendProp(sender, "Mode", def.mode().name());

        // Region
        String regionStr = def.region()
                .map(r -> r.worldName() + " [" + r.minX() + "," + r.minY() + "," + r.minZ() + "] -> [" + r.maxX() + "," + r.maxY() + "," + r.maxZ() + "]")
                .orElse(dash());
        sendProp(sender, "Region", regionStr);

        // Teleport target
        String tpStr = def.targetWorld()
                .map(w -> w + " " + format(def.tx()) + " " + format(def.ty()) + " " + format(def.tz()) +
                        " " + format(def.tyaw()) + " " + format(def.tpitch()))
                .orElse(dash());
        sendProp(sender, "Teleport", tpStr);

        // Command + executor
        sendProp(sender, "Command", def.command().orElse(dash()));
        sendProp(sender, "Executor", def.command().isPresent() ? def.executor().name().toLowerCase(Locale.ROOT) : dash());

        // Server
        sendProp(sender, "Server", def.serverTarget().orElse(dash()));

        // Exclusive block
        sendProp(sender, "Exclusive Block", def.exclusiveBlock().map(m -> m.name().toLowerCase(Locale.ROOT)).orElse(dash()));

        // Sound + delay
        String soundStr = def.sound().map(RegistryUtil::keyString).orElse(dash());
        String soundDelay = def.sound().isPresent() ? String.valueOf(def.soundDelay()) : dash();
        sendProp(sender, "Sound", soundStr);
        sendProp(sender, "Sound Delay", soundDelay);

        // Particle + delay
        String particleStr = def.particle().map(RegistryUtil::keyString).orElse(dash());
        String particleDelay = def.particle().isPresent() ? String.valueOf(def.particleDelay()) : dash();
        sendProp(sender, "Particle", particleStr);
        sendProp(sender, "Particle Delay", particleDelay);
    }

    private void sendProp(CommandSender sender, String key, String value) {
        sender.sendMessage(feature.getLocalizationHandler().getMessage("portals.info.prop")
                .with("key", key)
                .with("value", value)
                .forAudience(sender).build());
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String @NotNull [] args) {
        if (!sender.hasPermission(ADMIN_PERM)) return Collections.emptyList();

        if (args.length == 1) {
            return Stream.of("create", "delete", "select", "wand", "saveregion", "setmode", "setteleport", "setcommand", "setserver", "setblock", "setsound", "setparticle", "info", "list")
                    .filter(opt -> opt.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && Stream.of("delete", "select", "setmode", "setteleport", "setcommand", "setserver", "setblock", "setsound", "setparticle", "info").anyMatch(args[0]::equalsIgnoreCase)) {
            return registry.all().stream().map(PortalDefinition::id).filter(id -> id.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setmode")) {
            return Stream.of("teleport", "command", "server").filter(opt -> opt.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setcommand")) {
            return Stream.of("player", "console").filter(opt -> opt.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setblock")) {
            String partial = args[2].toLowerCase(Locale.ROOT);
            Stream<String> base = PLACEABLE_BLOCKS.stream().filter(n -> n.startsWith(partial));
            if ("none".startsWith(partial)) return Stream.concat(Stream.of("none"), base).toList();
            return base.toList();
        }

        // Sound/Particle completions via registry keys (supports datapacks)
        if (args.length == 3 && args[0].equalsIgnoreCase("setsound")) {
            return RegistryUtil.soundKeysStartingWith(args[2], true);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setparticle")) {
            return RegistryUtil.particleKeysStartingWith(args[2], true);
        }

        return Collections.emptyList();
    }
}