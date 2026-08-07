package nl.hauntedmc.serverfeatures.features.economy.vault;

import nl.hauntedmc.serverfeatures.features.economy.Economy;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings.VaultConflictPolicy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

import java.util.Objects;

/**
 * Registers and cleanly unregisters the optional Vault provider.
 *
 * <p>The {@code REPLACE} policy registers at Vault's highest priority and verifies that this
 * provider became active. It deliberately leaves the previous provider registered, allowing
 * Bukkit to expose it again automatically when this feature shuts down.</p>
 */
public final class VaultProviderRegistration implements EconomyVaultIntegration {
    private final Economy feature;
    private volatile VaultEconomyProvider provider;
    private volatile String status = "disabled";

    public VaultProviderRegistration(Economy feature) {
        this.feature = Objects.requireNonNull(feature, "feature");
    }

    @Override
    public synchronized void register() {
        if (provider != null || status.startsWith("skipped:")) {
            return;
        }
        if (!feature.settings().vault().enabled()) {
            status = "disabled";
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            status = "vault-missing";
            feature.getLogger().warning("Vault integration is enabled, but Vault is not installed.");
            return;
        }
        ServicesManager services = Bukkit.getServicesManager();
        RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> existing =
                services.getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (existing != null) {
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
        String primaryCurrency = feature.settings().vault().primaryCurrency();
        VaultEconomyProvider.validateDoubleCompatibility(feature.service().primaryCurrency());
        VaultEconomyProvider candidate = new VaultEconomyProvider(feature.service(), primaryCurrency);
        try {
            services.register(
                    net.milkbowl.vault.economy.Economy.class,
                    candidate,
                    feature.getPlugin(),
                    ServicePriority.Highest
            );
            RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> active =
                    services.getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (active == null || active.getProvider() != candidate) {
                throw new IllegalStateException(
                        "Vault did not select ServerFeatures as its active economy provider"
                );
            }
        } catch (RuntimeException | Error failure) {
            candidate.disable();
            try {
                services.unregister(net.milkbowl.vault.economy.Economy.class, candidate);
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        provider = candidate;
        status = "registered:" + primaryCurrency;
    }

    @Override
    public String status() {
        VaultEconomyProvider current = provider;
        if (current != null) {
            RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> active =
                    Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (active == null) {
                return "inactive:no-provider";
            }
            if (active.getProvider() != current) {
                return "shadowed:" + active.getProvider().getName();
            }
        }
        return status;
    }

    @Override
    public synchronized void close() {
        VaultEconomyProvider current = provider;
        provider = null;
        if (current == null) {
            status = "disabled";
            return;
        }
        current.disable();
        try {
            Bukkit.getServicesManager().unregister(net.milkbowl.vault.economy.Economy.class, current);
        } finally {
            status = "disabled";
        }
    }
}
