package nl.hauntedmc.serverfeatures.features.repairnpc;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.trait.TraitInfo;
import net.milkbowl.vault.economy.Economy;
import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.repairnpc.hook.RepairTrait;
import nl.hauntedmc.serverfeatures.features.repairnpc.meta.Meta;

import java.util.HashMap;
import java.util.Map;

public class RepairNPC extends BukkitBaseFeature<Meta> {

    private static Economy economy;
    private static RepairNPC instance;

    public RepairNPC(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();

        // base-prices
        Map<String, Object> bp = new HashMap<>();
        bp.put("default", 100);
        bp.put("trident", 400);
        bp.put("elytra", 400);
        defaults.put("base-prices", bp);

        // price-per-durability-point
        Map<String, Object> pdp = new HashMap<>();
        pdp.put("default", 0.3);
        pdp.put("trident", 0.6);
        pdp.put("elytra", 0.6);
        defaults.put("price-per-durability-point", pdp);

        // enchantment-modifiers
        Map<String, Object> em = new HashMap<>();
        em.put("default", 25);
        em.put("trident", 50);
        em.put("elytra", 50);
        defaults.put("enchantment-modifiers", em);

        // operational flags & numbers
        defaults.put("dropitem", false);
        defaults.put("disablecooldown", false);
        defaults.put("disabledelay", false);

        Map<String, Object> delays = new HashMap<>();
        delays.put("minimum", 4);
        delays.put("maximum", 8);
        delays.put("reforge-cooldown", 8);
        defaults.put("delays-in-seconds", delays);

        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("repairnpc.auw", "&7[&3Blacksmith&7] &7Frederik&f: Auw! In welke wereld was het een slim idee om mij met een wapen te slaan?!");
        messages.add("repairnpc.busy-with-player", "&7[&3Blacksmith&7] &7Frederik&f: Op dit moment heb ik al een taak, kom straks terug alsjeblieft!");
        messages.add("repairnpc.busy-with-reforge", "&7[&3Blacksmith&7] &7Frederik&f: Rustig, ik ben hard aan het werk, nog even geduld.");
        messages.add("repairnpc.cooldown-not-expired", "&7[&3Blacksmith&7] &7Frederik&f: Ik heb even pauze, tot zo!");
        messages.add("repairnpc.cost", "&7[&3Blacksmith&7] &7Frederik&f: Het kost je &b{price} Tokens &fom deze &a{item}&f te repareren. Klik opnieuw om te bevestigen.");
        messages.add("repairnpc.invalid-item", "&7[&3Blacksmith&7] &7Frederik&f: Sorry, maar dat kan ik niet repareren.");
        messages.add("repairnpc.item-changed-during-reforge", "&7[&3Blacksmith&7] &7Frederik&f: Hey, dit is niet het item dat je wilde repareren.");
        messages.add("repairnpc.start-reforge", "&7[&3Blacksmith&7] &7Frederik&f: Oke, ik ga aan de slag.");
        messages.add("repairnpc.successful-reforge", "&7[&3Blacksmith&7] &7Frederik&f: Kijk eens aan, weer zo goed als nieuw!");
        messages.add("repairnpc.fail-reforge", "&7[&3Blacksmith&7] &7Frederik&f: Oh nee he, het is niet gelukt, volgende keer beter!");
        messages.add("repairnpc.insufficient-funds", "&7[&3Blacksmith&7] &7Frederik&f: Je hebt niet genoeg Tokens om de reparatie te betalen.");
        return messages;
    }

    @Override
    public void initialize() {
        setupVault();
        instance = this;
        CitizensAPI.getTraitFactory()
                .registerTrait(TraitInfo.create(RepairTrait.class).withName("repair"));
    }

    private void setupVault() {
        var registration = getPlugin()
                .getServer()
                .getServicesManager()
                .getRegistration(Economy.class);

        if (registration != null) {
            economy = registration.getProvider();
        } else {
            getPlugin().getLogger()
                    .severe("Failed to load Vault economy; RepairNPC will not function.");
        }
    }

    @Override
    public void disable() {
        CitizensAPI.getTraitFactory()
                .deregisterTrait(TraitInfo.create(RepairTrait.class).withName("repair"));
    }

    public static RepairNPC getInstance() { return instance; }
    public static Economy getEconomy()    { return economy;    }
}
