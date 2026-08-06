package nl.hauntedmc.serverfeatures.features.economy;

/** Late-bound Vault lifecycle boundary that keeps Vault optional at class-load time. */
public interface EconomyVaultIntegration extends AutoCloseable {
    void register();

    String status();

    @Override
    void close();
}
