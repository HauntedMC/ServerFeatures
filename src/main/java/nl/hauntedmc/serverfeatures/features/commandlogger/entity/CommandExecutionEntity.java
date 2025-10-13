package nl.hauntedmc.serverfeatures.features.commandlogger.entity;

import jakarta.persistence.*;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;

@Entity
@Table(name = "player_command_executions")
public class CommandExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Naam van de server waar het commando is uitgevoerd.
     * Voor Velocity altijd "proxy".
     */
    @Column(name = "server", length = 100, nullable = false)
    private String server;

    /**
     * Optioneel: speler die het commando uitvoerde (null bij console e.d.).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id")
    private PlayerEntity player;

    /**
     * Alleen zetten voor niet-speler bronnen (console e.d.). Anders null.
     */
    @Column(name = "source", length = 150, nullable = false) // nullable by default
    private String source;

    /**
     * Volledige commandline zonder leading slash, bv. "velocity info".
     */
    @Column(name = "command", length = 400, nullable = false)
    private String command;

    @Column(name = "timestamp", nullable = false)
    private Long timestamp;

    public CommandExecutionEntity() {
    }

    public Long getId() {
        return id;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public PlayerEntity getPlayer() {
        return player;
    }

    public void setPlayer(PlayerEntity player) {
        this.player = player;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
