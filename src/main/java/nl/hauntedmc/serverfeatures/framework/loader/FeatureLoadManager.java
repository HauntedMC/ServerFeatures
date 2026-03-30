package nl.hauntedmc.serverfeatures.framework.loader;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureFactory;
import nl.hauntedmc.serverfeatures.framework.command.sync.CommandSync;
import nl.hauntedmc.serverfeatures.framework.config.MainConfigHandler;
import nl.hauntedmc.serverfeatures.framework.loader.dependency.DependencyCheckResult;
import nl.hauntedmc.serverfeatures.framework.loader.dependency.FeatureDependencyManager;
import nl.hauntedmc.serverfeatures.framework.loader.disable.FeatureDisableResponse;
import nl.hauntedmc.serverfeatures.framework.loader.disable.FeatureDisableResult;
import nl.hauntedmc.serverfeatures.framework.loader.enable.FeatureEnableResponse;
import nl.hauntedmc.serverfeatures.framework.loader.enable.FeatureEnableResult;
import nl.hauntedmc.serverfeatures.framework.loader.reload.FeatureReloadResponse;
import nl.hauntedmc.serverfeatures.framework.loader.reload.FeatureReloadResult;
import nl.hauntedmc.serverfeatures.framework.loader.softreload.FeatureSoftReloadResponse;
import nl.hauntedmc.serverfeatures.framework.loader.softreload.FeatureSoftReloadResult;
import nl.hauntedmc.serverfeatures.framework.localization.LocalizationHandler;

import java.util.*;
import java.util.logging.Level;

public class FeatureLoadManager {

    private final ServerFeatures plugin;
    private final MainConfigHandler mainConfigHandler;
    private final FeatureRegistry featureRegistry;
    private final FeatureDependencyManager dependencyManager;
    private final LocalizationHandler localizationHandler;

    public FeatureLoadManager(ServerFeatures plugin) {
        this.plugin = plugin;
        this.mainConfigHandler = plugin.getConfigHandler();
        this.localizationHandler = plugin.getLocalizationHandler();
        this.featureRegistry = new FeatureRegistry();
        this.dependencyManager = new FeatureDependencyManager(this, plugin);
        discoverFeatures();
    }

    private void discoverFeatures() {
        plugin.getLogger().info("[FeatureScanner] Scanning for features...");
        try (var scanResult = new io.github.classgraph.ClassGraph()
                .enableClassInfo()
                .acceptPackages("nl.hauntedmc.serverfeatures.features")
                .scan()) {
            scanResult.getSubclasses(BukkitBaseFeature.class.getName()).forEach(classInfo -> {
                String registryName = classInfo.getSimpleName();
                String featureClassName = classInfo.getName();
                FeatureDescriptor descriptor = buildDescriptor(registryName, featureClassName);
                featureRegistry.registerAvailableFeature(descriptor);
            });
        }

        plugin.getLogger().info("Discovered features: " + featureRegistry.getAvailableFeatures().keySet());
        mainConfigHandler.cleanupUnusedFeatures(featureRegistry.getAvailableFeatures().keySet());
    }

    private FeatureDescriptor buildDescriptor(String registryName, String featureClassName) {
        Optional<BaseMeta> metaOptional = resolveMeta(featureClassName);
        if (metaOptional.isEmpty()) {
            return new FeatureDescriptor(
                    registryName,
                    featureClassName,
                    registryName,
                    "?",
                    Set.of(),
                    Set.of()
            );
        }

        BaseMeta meta = metaOptional.get();
        String featureName = (meta.getFeatureName() == null || meta.getFeatureName().isBlank())
                ? registryName
                : meta.getFeatureName();
        String featureVersion = (meta.getFeatureVersion() == null || meta.getFeatureVersion().isBlank())
                ? "?"
                : meta.getFeatureVersion();
        Set<String> featureDependencies = meta.getDependencies() == null
                ? Set.of()
                : new LinkedHashSet<>(meta.getDependencies());
        Set<String> pluginDependencies = meta.getPluginDependencies() == null
                ? Set.of()
                : new LinkedHashSet<>(meta.getPluginDependencies());

        return new FeatureDescriptor(
                registryName,
                featureClassName,
                featureName,
                featureVersion,
                featureDependencies,
                pluginDependencies
        );
    }

    private Optional<BaseMeta> resolveMeta(String featureClassName) {
        int lastDot = featureClassName.lastIndexOf('.');
        if (lastDot < 0) {
            return Optional.empty();
        }

        String packageName = featureClassName.substring(0, lastDot);
        String metaClassName = packageName + ".meta.Meta";

        try {
            Class<?> metaClass = Class.forName(metaClassName, true, plugin.getClass().getClassLoader());
            if (!BaseMeta.class.isAssignableFrom(metaClass)) {
                plugin.getLogger().warning("Meta class does not implement BaseMeta: " + metaClassName);
                return Optional.empty();
            }

            BaseMeta meta = (BaseMeta) metaClass.getDeclaredConstructor().newInstance();
            return Optional.of(meta);
        } catch (ReflectiveOperationException | LinkageError t) {
            plugin.getLogger().log(Level.WARNING, "Could not resolve meta for " + featureClassName, t);
            return Optional.empty();
        }
    }

    public void initializeFeatures() {
        Set<String> visited = new HashSet<>();
        List<String> loadOrder = new ArrayList<>();

        for (String featureName : featureRegistry.getAvailableFeatures().keySet()) {
            if (!visited.contains(featureName)) {
                if (!resolveFeatureLoadOrder(featureName, new HashSet<>(), visited, loadOrder)) {
                    plugin.getLogger().severe("Dependency cycle detected! Feature loading aborted.");
                    return;
                }
            }
        }

        for (String featureName : loadOrder) {
            loadFeature(featureName);
        }
        CommandSync.apply(plugin);
    }

    private boolean resolveFeatureLoadOrder(String featureName, Set<String> stack, Set<String> visited, List<String> loadOrder) {
        if (stack.contains(featureName)) return false;
        if (visited.contains(featureName)) return true;

        stack.add(featureName);
        visited.add(featureName);

        FeatureDescriptor descriptor = featureRegistry.getAvailableFeature(featureName);
        if (descriptor == null) {
            plugin.getLogger().warning("Feature '" + featureName + "' is not registered as available.");
            return false;
        }

        for (String dependency : descriptor.featureDependencies()) {
            if (!resolveFeatureLoadOrder(dependency, stack, visited, loadOrder)) {
                return false;
            }
        }

        stack.remove(featureName);
        loadOrder.add(featureName);
        return true;
    }

    public FeatureEnableResponse enableFeature(String featureName) {
        FeatureDescriptor descriptor = featureRegistry.getAvailableFeature(featureName);
        if (descriptor == null) {
            plugin.getLogger().warning("Feature not found: " + featureName);
            return new FeatureEnableResponse(FeatureEnableResult.NOT_FOUND, Set.of(), Set.of());
        }
        if (featureRegistry.isFeatureLoaded(featureName)) {
            plugin.getLogger().warning("Feature already loaded: " + featureName);
            return new FeatureEnableResponse(FeatureEnableResult.ALREADY_LOADED, Set.of(), Set.of());
        }

        DependencyCheckResult diag = diagnoseDependencies(descriptor);
        if (!diag.ok()) {
            if (!diag.missingPluginDependencies().isEmpty()) {
                return new FeatureEnableResponse(
                        FeatureEnableResult.MISSING_PLUGIN_DEPENDENCY,
                        diag.missingPluginDependencies(),
                        diag.missingFeatureDependencies()
                );
            }
            return new FeatureEnableResponse(
                    FeatureEnableResult.MISSING_FEATURE_DEPENDENCY,
                    diag.missingPluginDependencies(),
                    diag.missingFeatureDependencies()
            );
        }

        boolean previousEnabled = mainConfigHandler.isFeatureEnabled(featureName);
        mainConfigHandler.setFeatureEnabled(featureName, true);

        boolean loaded = loadFeature(featureName);
        if (!loaded) {
            mainConfigHandler.setFeatureEnabled(featureName, previousEnabled);
            return new FeatureEnableResponse(FeatureEnableResult.FAILED, Set.of(), Set.of());
        }

        CommandSync.apply(plugin);

        return new FeatureEnableResponse(FeatureEnableResult.SUCCESS, Set.of(), Set.of());
    }

    public FeatureDisableResponse disableFeature(String featureName) {
        BukkitBaseFeature<?> feature = featureRegistry.getLoadedFeature(featureName);
        if (feature == null) {
            plugin.getLogger().warning("Feature not currently loaded: " + featureName);
            return new FeatureDisableResponse(FeatureDisableResult.NOT_LOADED, featureName, Set.of());
        }

        Set<String> dependents = new LinkedHashSet<>(dependencyManager.getDependentFeatures(featureName));
        for (String dep : dependents) {
            FeatureDisableResponse depResp = disableFeature(dep);
            if (!depResp.success()) {
                plugin.getLogger().warning("Failed to disable dependent feature: " + dep);
            }
        }

        try {
            feature.cleanup();
            mainConfigHandler.setFeatureEnabled(featureName, false);
            featureRegistry.deregisterLoadedFeature(featureName);
            plugin.getLogger().info("Feature disabled: " + featureName);
            CommandSync.apply(plugin);
            return new FeatureDisableResponse(FeatureDisableResult.SUCCESS, featureName, dependents);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Disable failed: " + featureName, t);
            return new FeatureDisableResponse(FeatureDisableResult.FAILED, featureName, dependents);
        }
    }

    public FeatureSoftReloadResponse softReloadFeature(String featureName) {
        if (!featureRegistry.isFeatureLoaded(featureName)) {
            plugin.getLogger().warning("Feature not currently loaded: " + featureName);
            return new FeatureSoftReloadResponse(FeatureSoftReloadResult.NOT_LOADED, featureName);
        }
        try {
            BukkitBaseFeature<?> feature = featureRegistry.getLoadedFeature(featureName);
            feature.getConfigHandler().reloadConfig();
            feature.getLocalizationHandler().reloadLocalization();
            plugin.getLogger().info("Feature " + featureName + " soft reloaded.");
            return new FeatureSoftReloadResponse(FeatureSoftReloadResult.SUCCESS, featureName);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Soft reload failed for: " + featureName, t);
            return new FeatureSoftReloadResponse(FeatureSoftReloadResult.FAILED, featureName);
        }
    }

    public FeatureReloadResponse reloadFeature(String featureName) {
        if (!featureRegistry.isFeatureLoaded(featureName)) {
            plugin.getLogger().warning("Feature not currently loaded: " + featureName);
            return new FeatureReloadResponse(FeatureReloadResult.NOT_LOADED, featureName, Set.of());
        }

        Set<String> reloadedDependents = new LinkedHashSet<>();
        try {
            mainConfigHandler.reloadConfig();
            localizationHandler.reloadLocalization();

            BukkitBaseFeature<?> feature = featureRegistry.getLoadedFeature(featureName);
            feature.cleanup();
            featureRegistry.deregisterLoadedFeature(featureName);

            boolean hasReloaded = loadFeature(featureName);
            if (!hasReloaded) {
                plugin.getLogger().severe("Reload failed for: " + featureName + " (feature did not load back)");
                return new FeatureReloadResponse(FeatureReloadResult.FAILED, featureName, reloadedDependents);
            }

            plugin.getLogger().info("Feature " + featureName + " reloaded.");

            for (String dependent : dependencyManager.getDependentFeatures(featureName)) {
                plugin.getLogger().info("Reloading dependent feature: " + dependent);
                FeatureReloadResponse depResp = reloadFeature(dependent);
                if (depResp.success()) reloadedDependents.add(dependent);
            }
            return new FeatureReloadResponse(FeatureReloadResult.SUCCESS, featureName, reloadedDependents);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Reload failed for: " + featureName, t);
            return new FeatureReloadResponse(FeatureReloadResult.FAILED, featureName, reloadedDependents);
        }
    }

    public FeatureRegistry getFeatureRegistry() {
        return featureRegistry;
    }

    public void unloadAllFeatures() {
        plugin.getLogger().info("Unloading all loaded features...");
        List<String> loadedFeatureNames = new ArrayList<>(featureRegistry.getLoadedFeatureNames());
        for (String featureName : loadedFeatureNames) {
            BukkitBaseFeature<?> feature = featureRegistry.getLoadedFeature(featureName);
            if (feature == null) {
                continue;
            }

            try {
                feature.cleanup();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Failed to cleanup feature during unload: " + featureName, t);
            } finally {
                featureRegistry.deregisterLoadedFeature(featureName);
            }
        }
        CommandSync.apply(plugin);
        plugin.getLogger().info("All features have been unloaded.");
    }

    public Set<String> getMissingPluginDependencies(String featureName) {
        FeatureDescriptor descriptor = featureRegistry.getAvailableFeature(featureName);
        if (descriptor == null) {
            return Set.of();
        }

        Set<String> missingPlugins = new LinkedHashSet<>();
        for (String pluginName : descriptor.pluginDependencies()) {
            if (!isPluginEnabled(pluginName)) {
                missingPlugins.add(pluginName);
            }
        }
        return missingPlugins;
    }

    private DependencyCheckResult diagnoseDependencies(FeatureDescriptor descriptor) {
        Set<String> missingPlugins = new LinkedHashSet<>(getMissingPluginDependencies(descriptor.registryName()));
        Set<String> missingFeatures = new LinkedHashSet<>();

        for (String dep : descriptor.featureDependencies()) {
            if (!featureRegistry.isFeatureLoaded(dep)) {
                missingFeatures.add(dep);
            }
        }
        return new DependencyCheckResult(missingPlugins, missingFeatures);
    }

    public boolean loadFeature(String featureName) {
        if (featureRegistry.isFeatureLoaded(featureName)) {
            plugin.getLogger().warning("Feature already loaded: " + featureName);
            return false;
        }

        FeatureDescriptor descriptor = featureRegistry.getAvailableFeature(featureName);
        if (descriptor == null) {
            plugin.getLogger().warning("Feature not found: " + featureName);
            return false;
        }

        mainConfigHandler.registerFeature(featureName);
        boolean enabled = mainConfigHandler.isFeatureEnabled(featureName);

        Set<String> missingPlugins = getMissingPluginDependencies(featureName);
        if (!missingPlugins.isEmpty()) {
            if (enabled) {
                plugin.getLogger().warning("Feature " + featureName + " cannot be enabled due to missing plugin dependency(s): " + String.join(", ", missingPlugins));
            }
            return false;
        }

        if (enabled && !dependencyManager.areDependenciesMet(featureName)) {
            plugin.getLogger().warning("Feature " + featureName + " is missing dependencies and cannot be enabled.");
            return false;
        }

        BukkitBaseFeature<?> feature = FeatureFactory.createFeature(descriptor.featureClassName(), plugin);
        if (feature == null) {
            return false;
        }

        mainConfigHandler.injectFeatureDefaults(featureName, feature.getDefaultConfig());
        localizationHandler.registerDefaultMessages(feature.getDefaultMessages());
        feature.getConfigHandler().reloadConfig();

        if (!enabled) {
            return false;
        }

        try {
            feature.initialize();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Feature '" + featureName + "' failed to initialize.", t);
            return false;
        }

        featureRegistry.registerLoadedFeature(featureName, feature);
        plugin.getLogger().info("Feature loaded: " + featureName);
        return true;
    }

    private boolean isPluginEnabled(String pluginName) {
        var foundPlugin = plugin.getServer().getPluginManager().getPlugin(pluginName);
        return foundPlugin != null && foundPlugin.isEnabled();
    }
}
