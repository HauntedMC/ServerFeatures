package nl.hauntedmc.serverfeatures.features.nickname;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.nickname.command.NickCommand;
import nl.hauntedmc.serverfeatures.features.nickname.entity.NicknameEntity;
import nl.hauntedmc.serverfeatures.features.nickname.internal.NicknameHandler;
import nl.hauntedmc.serverfeatures.features.nickname.internal.NicknamePlaceholder;
import nl.hauntedmc.serverfeatures.features.nickname.listener.PlayerJoinListener;
import nl.hauntedmc.serverfeatures.features.nickname.meta.Meta;

import java.util.List;

public class Nickname extends BukkitBaseFeature<Meta> {

    private ORMContext ormContext;
    private NicknameHandler nicknameHandler;

    public Nickname(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("minNicknameLength", 3);
        defaults.put("maxNicknameLength", 16);
        defaults.put("allowedCharacters", List.of(
                "❤", "★", "☆", "♪", "♬", "✔", "✘",
                "◆", "◇", "▲", "△", "▼", "▽", "▶",
                "▷", "◀", "◁", "♠", "♣", "♦", "❣",
                "❥", "✿", "❀", "❁", "✾", "✽", "❋",
                "✵", "✶", "✷", "✸", "✹", "✺", "❂",
                "☼", "☮", "☯", "♻", "✉", "➤", "➥",
                "➦", "❖", "❊", "☾", "☽", "❦", "❧",
                "✧", "✦", "✩", "✪", "✰", "❇", "❃",
                "❄", "❅", "❆", "✻", "✼", "➰", "❈",
                "❉", "♕", "♥", "♡"
        ));
        defaults.put("disallowedFormatting", List.of(
                "&r", "§r", "<reset>",
                "&n", "§n", "<underline>",
                "&m", "§m", "<strikethrough>",
                "&k", "§k", "<obfuscated>"
                ));
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("nickname.set", "&eJouw nickname is veranderd naar {nickname}.");
        messages.add("nickname.removed", "&eJouw nickname is verwijderd.");
        messages.add("nickname.one_word", "&cJe nickname mag geen spaties bevatten.");
        messages.add("nickname.disallowed_formatting", "&cDeze nickname bevat formatting die niet is toegestaand: {format}");
        messages.add("nickname.max_length_exceeded", "&cDeze nickname is te lang, kies een kortere nickname.");
        messages.add("nickname.invalid_characters", "&cDeze nickname bevat characters die niet zijn toegestaan.");
        messages.add("nickname.set_other", "&eDe nickname van {player} is veranderd naar {nickname}.");
        messages.add("nickname.player_not_found", "&cDeze speler is niet online.");
        messages.add("nickname.other_removed", "&eDe nickname van {player} is verwijderd.");
        return messages;
    }

    @Override
    public void initialize() {
        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());
        getLifecycleManager().getDataManager().registerConnection("ormConnection", DatabaseType.MYSQL, "player_data_rw");
        ormContext = getLifecycleManager().getDataManager().createORMContext("ormConnection", NicknameEntity.class, PlayerEntity.class).orElseThrow();

        nicknameHandler = new NicknameHandler(this);

        getLifecycleManager().getListenerManager().registerListener(new PlayerJoinListener(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new NickCommand(this));

        // Register PlaceholderAPI expansion
        if (getPlugin().getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new NicknamePlaceholder(this).register();
        }
    }

    @Override
    public void disable() {}

    public ORMContext getOrmContext() {
        return ormContext;
    }

    public NicknameHandler getNicknameHandler() {
        return nicknameHandler;
    }
}
