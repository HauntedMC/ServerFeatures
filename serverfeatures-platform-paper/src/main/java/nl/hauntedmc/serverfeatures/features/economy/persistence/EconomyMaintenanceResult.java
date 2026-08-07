package nl.hauntedmc.serverfeatures.features.economy.persistence;

/** Counts rows removed by one explicitly confirmed Economy maintenance operation. */
public record EconomyMaintenanceResult(
        int balances, int settings, int transactions, int entries, int dailyUsage, int workflows, int definitions
) {
    /** Returns the total number of durable rows removed, excluding the currency-family marker. */
    public int removedRows() {
        return balances + settings + transactions + entries + dailyUsage + workflows + definitions;
    }
}
