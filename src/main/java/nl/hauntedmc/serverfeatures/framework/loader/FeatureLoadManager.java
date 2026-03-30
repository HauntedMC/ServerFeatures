package nl.hauntedmc.serverfeatures.framework.loader;

import nl.hauntedmc.serverfeatures.ServerFeatures;
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
                try {
                    Class<?> clazz = Class.forName(classInfo.getName());
                    if (BukkitBaseFeature.class.isAssignableFrom(clazz)) {
                        @SuppressWarnings("unchecked")
                        Class<? extends BukkitBaseFeature<?>> featureClass = (Class<? extends BukkitBaseFeature<?>>) clazz;
                        featureRegistry.registerAvailableFeature(classInfo.getSimpleName(), featureClass);
                    }
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to load feature class: " + classInfo.getName(), t);
                }
            });
        }
        plugin.getLogger().info("Discovered features: " + featureRegistry.getAvailableFeatures().keySet());
        mainConfigHandler.cleanupUnusedFeatures(featureRegistry.getAvailableFeatures().keySet());
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

        BukkitBaseFeature<?> feature = FeatureFactory.createFeature(featureRegistry.getAvailableFeatures().get(featureName), plugin);
        if (feature != null) {
            for (String dependency : feature.getDependencies()) {
                if (!resolveFeatureLoadOrder(dependency, stack, visited, loadOrder)) {
                    return false;
                }
            }
        }

        stack.remove(featureName);
        loadOrder.add(featureName);
        return true;
    }

    public FeatureEnableResponse enableFeature(String featureName) {
        if (!featureRegistry.getAvailableFeatures().containsKey(featureName)) {
            plugin.getLogger().warning("Feature not found: " + featureName);
            return new FeatureEnableResponse(FeatureEnableResult.NOT_FOUND, Set.of(), Set.of());
        }
        if (featureRegistry.isFeatureLoaded(featureName)) {
            plugin.getLogger().warning("Feature already loaded: " + featureName);
            return new FeatureEnableResponse(FeatureEnableResult.ALREADY_LOADED, Set.of(), Set.of());
        }

        BukkitBaseFeature<?> feature = FeatureFactory.createFeature(featureRegistry.getAvailableFeatures().get(featureName), plugin);
        if (feature == null) {
            return new FeatureEnableResponse(FeatureEnableResult.FAILED, Set.of(), Set.of());
        }

        DependencyCheckResult diag = diagnoseDependencies(feature);
        if (!diag.ok()) {
            if (!diag.missingPluginDependencies().isEmpty()) {
                return new FeatureEnableResponse(FeatureEnableResult.MISSING_PLUGIN_DEPENDENCY, diag.missingPluginDependencies(), diag.missingFeatureDependencies());
            }
            return new FeatureEnableResponse(FeatureEnableResult.MISSING_FEATURE_DEPENDENCY, diag.missingPluginDependencies(), diag.missingFeatureDependencies());
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

        // Determine and disable dependents first (to avoid dangling refs)
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

            // Reload dependents automatically (best-effort)
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
        List<BukkitBaseFeature<?>> loadedFeatures = featureRegistry.getLoadedFeatures();
        for (BukkitBaseFeature<?> feature : loadedFeatures) {
            feature.cleanup();
        }
        CommandSync.apply(plugin);
        plugin.getLogger().info("All features have been unloaded.");
    }

    public Set<String> getMissingPluginDependencies(BukkitBaseFeature<?> feature) {
        Set<String> missingPlugins = new LinkedHashSet<>();
        for (String pluginName : feature.getPluginDependencies()) {
            if (!isPluginEnabled(pluginName)) {
                missingPlugins.add(pluginName);
            }
        }
        return missingPlugins;
    }

    private DependencyCheckResult diagnoseDependencies(BukkitBaseFeature<?> feature) {
        Set<String> missingPlugins = getMissingPluginDependencies(feature);
        Set<String> missingFeatures = new LinkedHashSet<>();

        // Feature dependencies
        for (String dep : feature.getDependencies()) {
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

        BukkitBaseFeature<?> feature = FeatureFactory.createFeature(featureRegistry.getAvailableFeatures().get(featureName), plugin);
        if (feature == null) return false;

        mainConfigHandler.registerFeature(featureName);
        mainConfigHandler.injectFeatureDefaults(featureName, feature.getDefaultConfig());
        localizationHandler.registerDefaultMessages(feature.getDefaultMessages());
        feature.getConfigHandler().reloadConfig();

        if (mainConfigHandler.isFeatureEnabled(featureName)) {
            if (!dependencyManager.areDependenciesMet(feature)) {
                plugin.getLogger().warning("Feature " + featureName + " is missing dependencies and cannot be enabled.");
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
        return false;
    }

    private boolean isPluginEnabled(String pluginName) {
        var foundPlugin = plugin.getServer().getPluginManager().getPlugin(pluginName);
        return foundPlugin != null && foundPlugin.isEnabled();
    }
}
