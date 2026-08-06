package nl.hauntedmc.serverfeatures.features.lottery;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.api.util.text.placeholder.MessagePlaceholders;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.lottery.command.LotteryCommand;
import nl.hauntedmc.serverfeatures.features.lottery.config.LotterySettings;
import nl.hauntedmc.serverfeatures.features.lottery.draw.LotteryDrawEngine;
import nl.hauntedmc.serverfeatures.features.lottery.economy.LotteryEconomy;
import nl.hauntedmc.serverfeatures.features.lottery.entity.LotteryEntryEntity;
import nl.hauntedmc.serverfeatures.features.lottery.entity.LotteryPayoutEntity;
import nl.hauntedmc.serverfeatures.features.lottery.entity.LotteryPlayerStatsEntity;
import nl.hauntedmc.serverfeatures.features.lottery.entity.LotteryRoundEntity;
import nl.hauntedmc.serverfeatures.features.lottery.listener.LotteryPlayerListener;
import nl.hauntedmc.serverfeatures.features.lottery.meta.Meta;
import nl.hauntedmc.serverfeatures.features.lottery.persistence.LotteryRepository;
import nl.hauntedmc.serverfeatures.features.lottery.placeholder.LotteryPlaceholder;
import nl.hauntedmc.serverfeatures.features.lottery.service.LotteryService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

/** Simple persistent Lottery feature using normal ServerFeatures ORM entities. */
public final class Lottery extends BukkitBaseFeature<Meta> {

    public static final String USE_PERMISSION = "serverfeatures.feature.lottery.use";
    public static final String BUY_PERMISSION = "serverfeatures.feature.lottery.buy";
    public static final String DONATE_PERMISSION = "serverfeatures.feature.lottery.donate";
    public static final String CLAIM_PERMISSION = "serverfeatures.feature.lottery.claim";
    public static final String ADMIN_PERMISSION = "serverfeatures.feature.lottery.admin";
    public static final String ADMIN_INSPECT_PERMISSION = "serverfeatures.feature.lottery.admin.inspect";
    public static final String ADMIN_DRAW_PERMISSION = "serverfeatures.feature.lottery.admin.draw";
    public static final String ADMIN_ADDPOT_PERMISSION = "serverfeatures.feature.lottery.admin.addpot";
    public static final String ADMIN_PAUSE_PERMISSION = "serverfeatures.feature.lottery.admin.pause";
    public static final String ADMIN_CANCEL_PERMISSION = "serverfeatures.feature.lottery.admin.cancel";

    private static final String ORM_CONNECTION = "lotteryOrmConnection";

    private LotterySettings settings;
    private LotteryService service;
    private LotteryPlaceholder placeholder;

    public Lottery(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("lottery_key", "$server");
        defaults.put("schedule.mode", "INTERVAL");
        defaults.put("schedule.interval", "12h");
        defaults.put("schedule.timezone", "Europe/Amsterdam");
        defaults.put("schedule.fixed_times", List.of());
        defaults.put("tickets.price", "500.00");
        defaults.put("tickets.maximum_per_player", 1);
        defaults.put("tickets.maximum_per_round", 0);
        defaults.put("tickets.maximum_per_command", 100);
        defaults.put("pot.base_amount", "1000.00");
        defaults.put("pot.payout_percentage", "100.00");
        defaults.put("pot.donations_enabled", true);
        defaults.put("pot.minimum_donation", "1.00");
        defaults.put("prizes.shares", List.of("100.00"));
        defaults.put("prizes.allow_same_player_multiple_prizes", false);
        defaults.put("anti_snipe.enabled", true);
        defaults.put("anti_snipe.trigger_remaining", "30s");
        defaults.put("anti_snipe.extension", "15s");
        defaults.put("anti_snipe.maximum_total_extension", "5m");
        defaults.put("broadcasts.enabled", true);
        defaults.put("broadcasts.remaining_times", List.of("1h", "30m", "10m", "5m", "1m", "30s", "10s"));
        defaults.put("payouts.automatic_on_join", true);
        defaults.put("payouts.claim_command_enabled", true);
        defaults.put("history.page_size", 10);
        defaults.put("history.leaderboard_size", 10);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("lottery.unavailable", "<red>De loterij is momenteel niet beschikbaar.</red>");
        messages.add("lottery.paused", "<yellow>De loterij is tijdelijk gepauzeerd.</yellow>");
        messages.add("lottery.closed", "<yellow>Deze trekking accepteert geen nieuwe transacties meer.</yellow>");
        messages.add("lottery.processing_existing", "<yellow>Je hebt al een loterijactie die wordt verwerkt.</yellow>");
        messages.add("lottery.identity_unavailable", "<red>Je spelersprofiel is nog niet beschikbaar. Probeer het zo opnieuw.</red>");
        messages.add("lottery.query_failed", "<red>De loterijgegevens konden niet worden geladen.</red>");
        messages.add("lottery.transaction.refunded", "<yellow>De actie kon niet worden opgeslagen. {amount} is terugbetaald.</yellow>");
        messages.add("lottery.transaction.uncertain", "<red>De economische transactie gaf geen zekere uitkomst. Neem contact op met staff.</red>");

        messages.add("lottery.buy.invalid_amount", "<red>Kies een aantal tussen 1 en {maximum}, of gebruik /lottery buy max.</red>");
        messages.add("lottery.buy.insufficient", "<red>Deze aankoop kost {cost}; je saldo is {balance}.</red>");
        messages.add("lottery.buy.player_limit", "<yellow>Je hebt {current} lot(en) en mag er maximaal {limit} hebben.</yellow>");
        messages.add("lottery.buy.round_limit", "<yellow>Er zijn nog {remaining} lot(en) beschikbaar in deze trekking.</yellow>");
        messages.add("lottery.buy.withdraw_failed", "<red>De betaling is geweigerd: {reason}</red>");
        messages.add("lottery.buy.success", "<green>Je kocht {tickets} lot(en) voor {cost}.</green> <gray>Je hebt nu {player_tickets} lot(en); pot {pot}.</gray>");

        messages.add("lottery.donate.disabled", "<yellow>Donaties zijn uitgeschakeld.</yellow>");
        messages.add("lottery.donate.minimum", "<yellow>De minimale donatie is {minimum}.</yellow>");
        messages.add("lottery.donate.insufficient", "<red>Je wilt {amount} doneren, maar je saldo is {balance}.</red>");
        messages.add("lottery.donate.invalid", "<red>Ongeldig bedrag: {reason}</red>");
        messages.add("lottery.donate.withdraw_failed", "<red>De donatie is geweigerd: {reason}</red>");
        messages.add("lottery.donate.success", "<green>Je doneerde {amount}.</green> <gray>De pot is nu {pot}.</gray>");

        messages.add("lottery.claim.disabled", "<yellow>Handmatig prijzen ophalen is uitgeschakeld.</yellow>");
        messages.add("lottery.claim.none", "<gray>Je hebt geen openstaande prijzen of terugbetalingen.</gray>");
        messages.add("lottery.claim.failed", "<red>Je uitbetaling kon niet worden geladen.</red>");
        messages.add("lottery.claim.payout_failed", "<red>De uitbetaling is mislukt: {reason}</red>");
        messages.add("lottery.claim.success", "<green>{amount} is aan je saldo toegevoegd.</green>");

        messages.add("lottery.ui.header", "<dark_gray>────────────</dark_gray> <gold><bold>Loterij</bold></gold> <dark_gray>────────────</dark_gray>");
        messages.add("lottery.ui.footer", "<dark_gray>──────────────────────────────</dark_gray>");
        messages.add("lottery.ui.pot", "<yellow>Jackpot:</yellow> <aqua>{pot}</aqua>");
        messages.add("lottery.ui.draw", "<yellow>Trekking:</yellow> <white>over {remaining}</white> <dark_gray>({status})</dark_gray>");
        messages.add("lottery.ui.sales", "<yellow>Loten:</yellow> <white>{tickets}</white> <gray>aan {participants} spelers · {price} per lot</gray>");
        messages.add("lottery.ui.player", "<yellow>Jij:</yellow> <white>{tickets} lot(en)</white> <gray>· {odds}% kans · {pending} openstaand</gray>");

        messages.add("lottery.history.header", "<gold><bold>Loterijgeschiedenis</bold></gold> <gray>pagina {page}</gray>");
        messages.add("lottery.history.empty", "<gray>Er zijn geen trekkingen op deze pagina.</gray>");
        messages.add("lottery.history.entry", "<aqua>{round}</aqua> <gray>· {winners} · {payout} · {tickets} loten / {participants} spelers</gray>");
        messages.add("lottery.leaderboard.wins_header", "<gold><bold>Totale winst</bold></gold> <gray>pagina {page}</gray>");
        messages.add("lottery.leaderboard.donations_header", "<gold><bold>Totale donaties</bold></gold> <gray>pagina {page}</gray>");
        messages.add("lottery.leaderboard.empty", "<gray>Er staan nog geen spelers in deze ranglijst.</gray>");
        messages.add("lottery.leaderboard.entry", "<aqua>#{rank}</aqua> <yellow>{player}</yellow> <gray>· {amount} · {count} keer</gray>");

        messages.add("lottery.broadcast.remaining", "<gold>[Loterij]</gold> <yellow>De trekking van {pot} is over {remaining}.</yellow>");
        messages.add("lottery.broadcast.draw_header", "<gold>[Loterij]</gold> <yellow>De trekking van {pot} is voltooid met {tickets} loten van {participants} spelers.</yellow>");
        messages.add("lottery.broadcast.winner", "<gold>[Loterij]</gold> <yellow>#{position} {player} wint {amount}!</yellow>");
        messages.add("lottery.broadcast.proof", "<dark_gray>Trekking {round}: commitment {commitment}, seed {seed}, entries {entry_digest}</dark_gray>");
        messages.add("lottery.broadcast.no_tickets", "<gold>[Loterij]</gold> <yellow>Er waren geen loten verkocht. {carry} gaat door naar de volgende trekking.</yellow>");
        messages.add("lottery.broadcast.no_payout", "<gold>[Loterij]</gold> <yellow>Er kon geen prijs worden uitgekeerd. {carry} gaat door.</yellow>");
        messages.add("lottery.broadcast.draw_failed", "<gold>[Loterij]</gold> <red>De trekking is mislukt en wordt later opnieuw geprobeerd.</red>");
        messages.add("lottery.broadcast.cancelled", "<gold>[Loterij]</gold> <yellow>De trekking is geannuleerd; {refunds} wordt terugbetaald.</yellow>");

        messages.add("lottery.admin.status", "<gold>Loterij {lottery}</gold> <gray>· ronde {round} · {status} · pot {pot} · {tickets} loten / {participants} spelers · {remaining}</gray>");
        messages.add("lottery.admin.action_failed", "<red>De beheeractie is mislukt: {reason}</red>");
        messages.add("lottery.admin.pot_added", "<green>{amount} is toegevoegd; de pot is nu {pot}.</green>");
        messages.add("lottery.admin.paused", "<yellow>De loterij is gepauzeerd.</yellow>");
        messages.add("lottery.admin.resumed", "<green>De loterij is hervat.</green>");
        messages.add("lottery.admin.draw_started", "<yellow>De trekking is gestart.</yellow>");
        messages.add("lottery.help", "<gold>/lottery</gold><newline><gold>/lottery buy [aantal|max]</gold><newline><gold>/lottery donate <bedrag></gold><newline><gold>/lottery claim</gold><newline><gold>/lottery history [pagina]</gold><newline><gold>/lottery leaderboard <wins|donations> [pagina]</gold>");
        return messages;
    }

    @Override
    public void initialize() {
        String serverName = getConfigHandler().getGlobalSetting("server_name", String.class, "server");
        settings = LotterySettings.load(getConfigHandler(), serverName);

        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());
        getLifecycleManager().getDataManager().registerConnection(
                ORM_CONNECTION,
                DatabaseType.MYSQL,
                "system_data_rw"
        );
        ORMContext ormContext = getLifecycleManager().getDataManager().createORMContext(
                ORM_CONNECTION,
                LotteryRoundEntity.class,
                LotteryEntryEntity.class,
                LotteryPayoutEntity.class,
                LotteryPlayerStatsEntity.class
        ).orElseThrow(() -> new IllegalStateException(
                "Lottery requires MYSQL/system_data_rw and could not create its ORM context."
        ));

        LotteryEconomy economy = LotteryEconomy.discover();
        service = new LotteryService(
                this,
                settings,
                new LotteryRepository(ormContext),
                economy,
                new LotteryDrawEngine()
        );
        getLifecycleManager().getCommandManager().registerBrigadierCommand(new LotteryCommand(this));
        getLifecycleManager().getListenerManager().registerListener(new LotteryPlayerListener(this));
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            LotteryPlaceholder candidate = new LotteryPlaceholder(this);
            if (candidate.register()) {
                placeholder = candidate;
            }
        }
        service.start();
    }

    @Override
    public void disable() {
        if (placeholder != null) {
            placeholder.unregister();
            placeholder = null;
        }
        if (service != null) {
            service.close();
            service = null;
        }
        settings = null;
    }

    public LotterySettings settings() {
        return settings;
    }

    public LotteryService service() {
        return service;
    }

    public void send(CommandSender audience, String key) {
        send(audience, key, Map.of());
    }

    public void send(CommandSender audience, String key, Map<String, String> values) {
        audience.sendMessage(component(audience, key, values));
    }

    public Component component(CommandSender audience, String key, Map<String, String> values) {
        var placeholders = MessagePlaceholders.builder();
        values.forEach(placeholders::addString);
        return getLocalizationHandler().getMessage(key)
                .withPlaceholders(placeholders.build())
                .forAudience(audience)
                .build();
    }

    public void broadcast(String key, Map<String, String> values) {
        for (var player : Bukkit.getOnlinePlayers()) {
            send(player, key, values);
        }
        Bukkit.getConsoleSender().sendMessage(component(Bukkit.getConsoleSender(), key, values));
    }
}
