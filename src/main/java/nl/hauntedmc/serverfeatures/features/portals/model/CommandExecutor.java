package nl.hauntedmc.serverfeatures.features.portals.model;

public enum CommandExecutor {
    PLAYER,
    CONSOLE;

    public static CommandExecutor fromString(String s, CommandExecutor def) {
        if (s == null) return def;
        return switch (s.toLowerCase()) {
            case "player", "p" -> PLAYER;
            case "console", "c", "server" -> CONSOLE;
            default -> def;
        };
    }
}
