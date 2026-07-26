package nl.hauntedmc.serverfeatures.test;

import nl.hauntedmc.dataregistry.api.service.FeatureServiceDirectory;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceHandle;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceInfo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** API-only in-memory service directory for consumer tests. */
public final class TestFeatureServiceDirectory implements FeatureServiceDirectory {
    private final Map<Class<?>, Entry<?>> entries = new ConcurrentHashMap<>();

    @Override public <T> FeatureServiceHandle register(String plugin, String feature, Class<T> type, T service) {
        FeatureServiceInfo info = new FeatureServiceInfo(plugin, feature, type, service.getClass().getName());
        Entry<T> entry = new Entry<>(info, service);
        entries.compute(type, (ignored, existing) -> {
            if (existing != null && (!existing.info().ownerPlugin().equals(info.ownerPlugin())
                    || !existing.info().ownerFeature().equals(info.ownerFeature()))) {
                throw new IllegalStateException("Feature service is already registered by another owner: " + type.getName());
            }
            return entry;
        });
        return new FeatureServiceHandle() {
            @Override public FeatureServiceInfo info() { return info; }
            @Override public void close() { entries.remove(type, entry); }
        };
    }
    @Override public <T> Optional<T> find(Class<T> type) { Entry<?> entry = entries.get(type); return entry == null ? Optional.empty() : Optional.of(type.cast(entry.service())); }
    @Override public <T> T require(Class<T> type) { return find(type).orElseThrow(); }
    @Override public boolean contains(Class<?> type) { return entries.containsKey(type); }
    @Override public Optional<FeatureServiceInfo> describe(Class<?> type) { Entry<?> entry = entries.get(type); return entry == null ? Optional.empty() : Optional.of(entry.info()); }
    @Override public List<FeatureServiceInfo> list() { return entries.values().stream().map(Entry::info).toList(); }
    @Override public boolean unregister(Class<?> type, Object service) { Entry<?> entry = entries.get(type); return entry != null && entry.service() == service && entries.remove(type, entry); }
    @Override public int unregisterOwner(String plugin, String feature) { int before = entries.size(); entries.entrySet().removeIf(entry -> entry.getValue().info().ownerPlugin().equals(plugin) && entry.getValue().info().ownerFeature().equals(feature)); return before - entries.size(); }
    @Override public void clear() { entries.clear(); }
    private record Entry<T>(FeatureServiceInfo info, T service) { }
}
