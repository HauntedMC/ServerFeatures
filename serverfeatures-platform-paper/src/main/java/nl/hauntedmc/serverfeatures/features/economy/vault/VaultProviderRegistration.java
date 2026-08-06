package nl.hauntedmc.serverfeatures.features.economy.vault;

import nl.hauntedmc.serverfeatures.features.economy.Economy;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings.VaultConflictPolicy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;

import java.util.Objects;

/** Registers and cleanly unregisters the optional Vault provider. */
public final class VaultProviderRegistration implements AutoCloseable {
    private final Economy feature;
    private VaultEconomyProvider provider;
    private String status = "disabled";

    public VaultProviderRegistration(Economy feature) {
        this.feature = Objects.requireNonNull(feature, "feature");
    }

    public void register() {
        if (!feature.settings().vault().enabled()) {
            status = "disabled";
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            status = "vault-missing";
            feature.getLogger().warning("Vault integration is enabled, but Vault is not installed.");
            return;
        }
        RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> existing = Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (existing != null && existing.getProvider() != null) {
            VaultConflictPolicy policy = feature.settings().vault().conflictPolicy();
            if (policy == VaultConflictPolicy.FAIL) {
                throw new IllegalStateException(
                        "Another Vault economy provider is already active: " + existing.getProvider().getName()
                );
            }
            if (policy == VaultConflictPolicy.SKIP) {
                status = "skipped:" + existing.getProvider().getName();
                feature.getLogger().warning("Keeping existing Vault economy provider: " + existing.getProvider().getName());
                return;
            }
            feature.getLogger().warning("Registering Economy above existing Vault provider: "
                    + existing.getProvider().getName());
        }
        provider = new VaultEconomyProvider(feature.service(), feature.settings().vault().primaryCurrency());
        Bukkit.getServicesManager().register(net.milkbowl.vault.economy.Economy.class, provider, feature.getPlugin(), ServicePriority.Highest);
        status = "registered:" + feature.settings().vault().primaryCurrency();
    }

    public String status() {
        return status;
    }

    @Override
    public void close() {
        VaultEconomyProvider current = provider;
        provider = null;
        if (current == null) {
            return;
        }
        current.disable();
        Bukkit.getServicesManager().unregister(net.milkbowl.vault.economy.Economy.class, current);
        status = "disabled";
    }
}
