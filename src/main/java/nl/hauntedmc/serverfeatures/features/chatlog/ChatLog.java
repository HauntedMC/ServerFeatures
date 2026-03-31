package nl.hauntedmc.serverfeatures.features.chatlog;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.chatlog.command.ChatReportCommand;
import nl.hauntedmc.serverfeatures.features.chatlog.entities.ChatMessageEntity;
import nl.hauntedmc.serverfeatures.features.chatlog.entities.ReportedChatMessageEntity;
import nl.hauntedmc.serverfeatures.features.chatlog.internal.ReportHandler;
import nl.hauntedmc.serverfeatures.features.chatlog.listener.ChatListener;
import nl.hauntedmc.serverfeatures.features.chatlog.meta.Meta;

public class ChatLog extends BukkitBaseFeature<Meta> {


    private ReportHandler reportHandler;
    private ORMContext ormContext;

    public ChatLog(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("URL", "https://hauntedmc.nl/chatlog/?report="); // Base URL for report links
        defaults.put("reportTimeFrameMinutes", 15); // Timeframe (in minutes) to include messages in a report
        defaults.put("discordWebhookURL", "https://discordhook.url");
        return defaults;
    }

    /**
     * If you have any default messages you want to provide, you can add them here.
     * For example:
     */
    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messageMap = new MessageMap();
        messageMap.add("chatlog.help", "&e/chatreport <playername> &7- &aom een chatreport te maken.");
        messageMap.add("chatlog.error", "&cGeen berichten gevonden van {name}");
        messageMap.add("chatlog.url", "&eChatreport link: &a{url}. &eZet de bovenstaande link in een support ticket (/support) om een speler te rapporteren.");
        messageMap.add("chatlog.errorNotSaved", "&cReport kon niet worden opgeslagen.");
        return messageMap;
    }

    /**
     * Called when the feature is enabled.
     */
    @Override
    public void initialize() {
        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());
        getLifecycleManager().getDataManager().registerConnection("ormConnection", DatabaseType.MYSQL, "player_data_rw");
        ormContext = getLifecycleManager().getDataManager().createORMContext("ormConnection",
                PlayerEntity.class,
                ChatMessageEntity.class,
                ReportedChatMessageEntity.class).orElseThrow();

        reportHandler = new ReportHandler(this);
        getLifecycleManager().getListenerManager().registerListener(new ChatListener(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new ChatReportCommand(this));
    }

    @Override
    public void disable() {
    }

    public ORMContext getOrmContext() {
        return ormContext;
    }

    public ReportHandler getReportHandler() {
        return reportHandler;
    }
}
