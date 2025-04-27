package nl.hauntedmc.serverfeatures.features.commandrelay.internal.messaging;

import nl.hauntedmc.dataprovider.database.messaging.api.AbstractEventMessage;

public final class CommandRelayMessage extends AbstractEventMessage {
    private final String command;
    private final String originServer;

    // No-arg ctor for Gson
    @SuppressWarnings("unused")
    private CommandRelayMessage() {
        super("commandrelay");
        this.command      = null;
        this.originServer = null;
    }

    /**
     * @param command      the full command string to execute (including leading slash and args)
     * @param originServer the name of the server that published this message
     */
    public CommandRelayMessage(String command, String originServer) {
        super("commandrelay");
        this.command      = command;
        this.originServer = originServer;
    }

    public String getCommand() {
        return command;
    }

    public String getOriginServer() {
        return originServer;
    }
}
