package nl.hauntedmc.serverfeatures.features.commandscheduler;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.commandscheduler.command.CommandSchedulerCommand;
import nl.hauntedmc.serverfeatures.features.commandscheduler.config.CommandScheduleRepository;
import nl.hauntedmc.serverfeatures.features.commandscheduler.internal.CommandSchedulerService;
import nl.hauntedmc.serverfeatures.features.commandscheduler.internal.ScheduledCommandExecutor;
import nl.hauntedmc.serverfeatures.features.commandscheduler.meta.Meta;

import java.time.DateTimeException;
import java.time.ZoneId;

public class CommandScheduler extends BukkitBaseFeature<Meta> {

    private CommandSchedulerService service;

    public CommandScheduler(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap config = new ConfigMap();
        config.put("enabled", false);
        config.put("time-zone", "system");
        config.put("command-delay-ticks", 2);
        config.put("logging.log-executions", true);
        config.put("logging.log-commands", false);
        return config;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add(
                "commandscheduler.usage",
                "&eGebruik: /commandscheduler <list|info|create|delete|enable|disable|set|command|run|reload>"
        );
        messages.add("commandscheduler.invalid_input", "&cOngeldige invoer: &f{reason}");
        messages.add("commandscheduler.persistence_error", "&cDe planning kon niet veilig worden opgeslagen.");
        messages.add("commandscheduler.not_found", "&cPlanning &f{id} &cbestaat niet.");
        messages.add("commandscheduler.already_exists", "&cPlanning &f{id} &cbestaat al.");
        messages.add("commandscheduler.no_commands", "&cPlanning &f{id} &cheeft nog geen commando's.");
        messages.add(
                "commandscheduler.enabled_empty",
                "&cVoeg eerst minimaal één commando toe voordat je &f{id} &cinschakelt."
        );
        messages.add("commandscheduler.invalid_index", "&cOngeldige commando-index voor &f{id}&c.");
        messages.add(
                "commandscheduler.last_command",
                "&cSchakel &f{id} &ceerst uit voordat je het laatste commando verwijdert."
        );
        messages.add(
                "commandscheduler.create_usage",
                "&eGebruik: /commandscheduler create <id> daily <HH:mm> <sequence|random> &7of &eweekly <dag> <HH:mm> <sequence|random>"
        );
        messages.add("commandscheduler.created", "&aPlanning &f{id} &aaangemaakt en uitgeschakeld opgeslagen.");
        messages.add("commandscheduler.deleted", "&aPlanning &f{id} &averwijderd.");
        messages.add("commandscheduler.enabled", "&aPlanning &f{id} &aingeschakeld.");
        messages.add("commandscheduler.disabled", "&aPlanning &f{id} &auitgeschakeld.");
        messages.add(
                "commandscheduler.set_usage",
                "&eGebruik: /commandscheduler set schedule <id> <daily HH:mm|weekly dag HH:mm> &7of &e/commandscheduler set mode <id> <sequence|random>"
        );
        messages.add("commandscheduler.schedule_set", "&aPlanningstijd voor &f{id} &aingesteld op &f{trigger}&a.");
        messages.add("commandscheduler.mode_set", "&aUitvoermodus voor &f{id} &aingesteld op &f{mode}&a.");
        messages.add(
                "commandscheduler.command_usage",
                "&eGebruik: /commandscheduler command <list|add|set|remove> <id> [...]"
        );
        messages.add("commandscheduler.command_added", "&aCommando toegevoegd aan &f{id}&a als nummer &f{index}&a.");
        messages.add("commandscheduler.command_set", "&aCommando &f{index} &avan &f{id} &abijgewerkt.");
        messages.add("commandscheduler.command_removed", "&aCommando &f{index} &avan &f{id} &averwijderd.");
        messages.add("commandscheduler.command_list_header", "&eCommando's voor &f{id} &7({mode})&e:");
        messages.add("commandscheduler.command_list_empty", "&7Deze planning heeft nog geen commando's.");
        messages.add("commandscheduler.command_list_entry", "&7{index}. &f{command}");
        messages.add("commandscheduler.run_started", "&aPlanning &f{id} &ahandmatig in de uitvoerwachtrij geplaatst.");
        messages.add(
                "commandscheduler.recursive_run",
                "&cEen gepland commando mag niet opnieuw /commandscheduler run uitvoeren."
        );
        messages.add(
                "commandscheduler.reloaded",
                "&aCommandScheduler herladen: &f{loaded} &ageldig, &f{invalid} &aongeldig, tijdzone &f{zone}&a."
        );
        messages.add("commandscheduler.list_empty", "&7Er zijn geen planningen ingesteld.");
        messages.add(
                "commandscheduler.list_header",
                "&eCommandScheduler-planningen &7(pagina {page}/{pages}, tijdzone {zone})&e:"
        );
        messages.add(
                "commandscheduler.list_entry",
                "&f{id} &7- {state} &7- &f{trigger} &7- &f{mode} &7- &f{commands} commando('s) &7- volgende: &f{next}"
        );
        messages.add("commandscheduler.info_header", "&ePlanning &f{id}&e:");
        messages.add("commandscheduler.info_state", "&7Status: &f{state}");
        messages.add("commandscheduler.info_trigger", "&7Trigger: &f{trigger}");
        messages.add("commandscheduler.info_mode", "&7Modus: &f{mode}");
        messages.add("commandscheduler.info_commands", "&7Commando's: &f{commands}");
        messages.add("commandscheduler.info_next", "&7Volgende uitvoering: &f{next}");
        return messages;
    }

    @Override
    public void initialize() {
        CommandScheduleRepository repository = new CommandScheduleRepository(this);
        ScheduledCommandExecutor executor = new ScheduledCommandExecutor(this);
        service = new CommandSchedulerService(this, repository, executor, resolveZone());
        service.reload(resolveZone());
        getLifecycleManager().getCommandManager().registerFeatureCommand(
                new CommandSchedulerCommand(this, service)
        );
    }

    @Override
    public void disable() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
    }

    public CommandSchedulerService service() {
        return service;
    }

    public CommandScheduleRepository.LoadResult reloadSchedules() {
        getConfigHandler().reloadConfig();
        return service.reload(resolveZone());
    }

    public ZoneId resolveZone() {
        String configured = getString("time-zone", "system").trim();
        if (configured.isEmpty() || configured.equalsIgnoreCase("system")) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(configured);
        } catch (DateTimeException exception) {
            getLogger().warning(
                    "Invalid CommandScheduler time-zone '" + configured
                            + "'; using system zone " + ZoneId.systemDefault() + "."
            );
            return ZoneId.systemDefault();
        }
    }

    public long getCommandDelayTicks() {
        return Math.max(0L, getLong("command-delay-ticks", 2L));
    }

    public boolean shouldLogExecutions() {
        return getBoolean("logging.log-executions", true);
    }

    public boolean shouldLogCommands() {
        return getBoolean("logging.log-commands", false);
    }

    private boolean getBoolean(String key, boolean defaultValue) {
        Object value = getConfigHandler().get(key);
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    private long getLong(String key, long defaultValue) {
        Object value = getConfigHandler().get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }

    private String getString(String key, String defaultValue) {
        Object value = getConfigHandler().get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }
}
