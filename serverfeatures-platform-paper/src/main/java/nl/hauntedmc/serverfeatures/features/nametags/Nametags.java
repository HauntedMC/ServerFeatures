package nl.hauntedmc.serverfeatures.features.nametags;

import com.github.retrooper.packetevents.PacketEvents;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.nametags.command.NametagCommand;
import nl.hauntedmc.serverfeatures.features.nametags.entities.PlayerNametagEntity;
import nl.hauntedmc.serverfeatures.features.nametags.internal.NametagDBService;
import nl.hauntedmc.serverfeatures.features.nametags.internal.NametagManager;
import nl.hauntedmc.serverfeatures.features.nametags.internal.hook.LuckPermsHook;
import nl.hauntedmc.serverfeatures.features.nametags.internal.hook.PlaceholderHook;
import nl.hauntedmc.serverfeatures.features.nametags.internal.packet.NametagPassengerPacketListener;
import nl.hauntedmc.serverfeatures.features.nametags.listener.NametagListener;
import nl.hauntedmc.serverfeatures.features.nametags.meta.Meta;
import org.bukkit.Bukkit;

public class Nametags extends BukkitBaseFeature<Meta> {
    private NametagManager nametagManager;
    private NametagDBService repository;
    private ORMContext ormContext;
    private PlaceholderHook placeholderHook;
    private LuckPermsHook luckPermsHook;
    private NametagPassengerPacketListener passengerPacketListener;

    public Nametags(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("max_distance", 45);

        defaults.put("lifecycle.join_settle_delay_ticks", 10);
        defaults.put("lifecycle.tracking_settle_delay_ticks", 2);
        defaults.put("lifecycle.transition_settle_delay_ticks", 10);
        defaults.put("lifecycle.teleport_rebuild_distance", 64);

        defaults.put("reconciliation.interval_ticks", 10);
        defaults.put("repair.remount_enabled", true);
        defaults.put("repair.remount_interval_ticks", 100);

        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("nametags.prefix", "%vault_prefix%");
        messages.add("nametags.playername", "&7%player_name%");
        messages.add("nametags.suffix", "%vault_suffix%");

        messages.add("nametags.selfview.enabled", "&7Eigen nametag weergave is nu &aingeschakeld&7.");
        messages.add("nametags.selfview.disabled", "&7Eigen nametag weergave is nu &cuitgeschakeld&7.");
        messages.add("nametags.selfview.status_on", "&7Eigen nametag weergave is &aingeschakeld&7.");
        messages.add("nametags.selfview.status_off", "&7Eigen nametag weergave is &cuitgeschakeld&7.");
        messages.add("nametags.selfview.usage", "&7Gebruik: /nametag selfview on|off|toggle|status");
        messages.add("nametags.selfview.already_enabled", "&7Eigen nametag weergave is al &aingeschakeld&7.");
        messages.add("nametags.selfview.already_disabled", "&7Eigen nametag weergave is al &cuitgeschakeld&7.");
        return messages;
    }

    @Override
    public void initialize() {
        this.placeholderHook = new PlaceholderHook(this);

        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());
        getLifecycleManager().getDataManager().registerConnection(
                "ormConnection",
                DatabaseType.MYSQL,
                "player_data_rw"
        );
        this.ormContext = getLifecycleManager().getDataManager()
                .createORMContext("ormConnection", PlayerNametagEntity.class)
                .orElseThrow(() -> new IllegalStateException(
                        "Nametags requires the MYSQL/player_data_rw connection and could not create its ORM context."
                ));
        this.repository = new NametagDBService(this);

        this.nametagManager = NametagManager.create(this);
        this.passengerPacketListener = new NametagPassengerPacketListener(
                nametagManager.getAttachmentIndex()
        );
        PacketEvents.getAPI().getEventManager().registerListener(passengerPacketListener);

        getLifecycleManager().getListenerManager().registerListener(new NametagListener(this));
        getLifecycleManager().getCommandManager().registerBrigadierCommand(new NametagCommand(this));

        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            this.luckPermsHook = LuckPermsHook.subscribe(this);
        }

        this.nametagManager.initializeOnlinePlayers();
    }

    @Override
    public void disable() {
        Throwable failure = null;

        LuckPermsHook currentLuckPermsHook = luckPermsHook;
        luckPermsHook = null;
        failure = cleanup(failure, () -> {
            if (currentLuckPermsHook != null) {
                currentLuckPermsHook.close();
            }
        });

        NametagPassengerPacketListener currentPacketListener = passengerPacketListener;
        passengerPacketListener = null;
        failure = cleanup(failure, () -> {
            if (currentPacketListener != null) {
                PacketEvents.getAPI().getEventManager().unregisterListener(currentPacketListener);
            }
        });

        failure = cleanup(failure, () -> {
            if (nametagManager != null) {
                nametagManager.removeAllNametags();
            }
        });

        PlaceholderHook currentPlaceholderHook = placeholderHook;
        placeholderHook = null;
        failure = cleanup(failure, () -> {
            if (currentPlaceholderHook != null) {
                currentPlaceholderHook.close();
            }
        });

        if (failure != null) {
            throwUnchecked(failure);
        }
    }

    public NametagManager getNametagManager() {
        return nametagManager;
    }

    public NametagDBService getRepository() {
        return repository;
    }

    public ORMContext getOrmContext() {
        return ormContext;
    }

    private static Throwable cleanup(Throwable existing, Runnable action) {
        try {
            action.run();
            return existing;
        } catch (Throwable throwable) {
            if (existing == null) {
                return throwable;
            }
            existing.addSuppressed(throwable);
            return existing;
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable throwable) throws E {
        throw (E) throwable;
    }
}
