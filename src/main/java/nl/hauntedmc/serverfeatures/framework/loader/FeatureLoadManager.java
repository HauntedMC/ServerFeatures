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

    private enum LoadOrderState {
        VISITING,
        VISITED,
        FAILED
    }

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
                if (classInfo.isAbstract()) {
                    return;
                }

                String registryName = classInfo.getSimpleName();
                String featureClassName = classInfo.getName();
                Optional<FeatureDescriptor> descriptorOptional = buildDescriptor(registryName, featureClassName);
                if (descriptorOptional.isEmpty()) {
                    return;
                }

                FeatureDescriptor descriptor = descriptorOptional.get();
                String conflictingKey = findCaseInsensitiveMatch(
                        descriptor.registryName(),
                        featureRegistry.getAvailableFeatures().keySet()
                );
                if (conflictingKey != null) {
                    FeatureDescriptor existing = featureRegistry.getAvailableFeature(conflictingKey);
                    plugin.getLogger().severe(
                            "Skipping feature class '" + descriptor.featureClassName()
                                    + "' because feature key '" + descriptor.registryName()
                                    + "' conflicts with '" + existing.featureClassName() + "'."
                    );
                    return;
                }

                featureRegistry.registerAvailableFeature(descriptor);
            });
        }

        pruneFeaturesWithMissingDependencies();

        plugin.getLogger().info("Discovered features: " + featureRegistry.getAvailableFeatures().keySet());
        mainConfigHandler.cleanupUnusedFeatures(featureRegistry.getAvailableFeatures().keySet());
    }

    private Optional<FeatureDescriptor> buildDescriptor(String registryName, String featureClassName) {
        Optional<BaseMeta> metaOptional = resolveMeta(featureClassName);
        if (metaOptional.isEmpty()) {
            int lastDot = featureClassName.lastIndexOf('.');
            String expectedMetaClass = lastDot < 0
                    ? featureClassName + ".meta.Meta"
                    : featureClassName.substring(0, lastDot) + ".meta.Meta";
            plugin.getLogger().severe(
                    "Skipping feature class '" + featureClassName
                            + "' because required meta class '" + expectedMetaClass + "' is missing or invalid."
            );
            return Optional.empty();
        }

        BaseMeta meta = metaOptional.get();
        String featureName = (meta.getFeatureName() == null || meta.getFeatureName().isBlank())
                ? registryName
                : meta.getFeatureName().trim();
        if (featureName.isBlank()) {
            plugin.getLogger().severe(
                    "Skipping feature class '" + featureClassName
                            + "' because getFeatureName() produced an empty name."
            );
            return Optional.empty();
        }
        String featureKey = featureName;
        if (!isValidFeatureKey(featureKey)) {
            plugin.getLogger().severe(
                    "Skipping feature class '" + featureClassName
                            + "' because getFeatureName() produced an invalid key: '" + featureKey + "'."
                            + " Allowed characters: letters, digits, '_' and '-'."
            );
            return Optional.empty();
        }

        String featureVersion = (meta.getFeatureVersion() == null || meta.getFeatureVersion().isBlank())
                ? "?"
                : meta.getFeatureVersion();
        Set<String> featureDependencies = normalizeFeatureDependencies(featureClassName, featureKey, meta.getDependencies());
        if (featureDependencies == null) {
            return Optional.empty();
        }
        Set<String> pluginDependencies = meta.getPluginDependencies() == null
                ? Set.of()
                : new LinkedHashSet<>(meta.getPluginDependencies());

        return Optional.of(new FeatureDescriptor(
                featureKey,
                featureClassName,
                featureName,
                featureVersion,
                featureDependencies,
                pluginDependencies
        ));
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
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("Meta class not found: " + metaClassName);
            return Optional.empty();
        } catch (ReflectiveOperationException | LinkageError t) {
            plugin.getLogger().log(Level.WARNING, "Could not resolve meta for " + featureClassName, t);
            return Optional.empty();
        }
    }

    private Set<String> normalizeFeatureDependencies(String featureClassName, String featureKey, Collection<String> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return Set.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawDependency : dependencies) {
            String dependencyKey = rawDependency == null ? "" : rawDependency.trim();
            if (dependencyKey.isEmpty()) {
                plugin.getLogger().severe(
                        "Skipping feature class '" + featureClassName
                                + "' because dependency name is invalid: '" + rawDependency + "'."
                );
                return null;
            }
            if (!isValidFeatureKey(dependencyKey)) {
                plugin.getLogger().severe(
                        "Skipping feature class '" + featureClassName
                                + "' because dependency key is invalid: '" + dependencyKey + "'."
                                + " Allowed characters: letters, digits, '_' and '-'."
                );
                return null;
            }

            if (!dependencyKey.equalsIgnoreCase(featureKey)) {
                normalized.add(dependencyKey);
            }
        }
        return normalized;
    }

    private void pruneFeaturesWithMissingDependencies() {
        boolean changed;
        do {
            changed = false;
            Set<String> available = new LinkedHashSet<>(featureRegistry.getAvailableFeatures().keySet());
            List<FeatureDescriptor> snapshot = new ArrayList<>(featureRegistry.getAvailableFeatures().values());

            for (FeatureDescriptor descriptor : snapshot) {
                Set<String> missingDependencies = descriptor.featureDependencies().stream()
                        .filter(dep -> !available.contains(resolveFeatureKey(dep)))
                        .collect(LinkedHashSet::new, Set::add, Set::addAll);

                if (!missingDependencies.isEmpty()) {
                    featureRegistry.deregisterAvailableFeature(descriptor.registryName());
                    changed = true;
                    plugin.getLogger().severe(
                            "Skipping feature '" + descriptor.featureName() + "' (" + descriptor.registryName()
                                    + ") because dependency feature(s) are unavailable: "
                                    + String.join(", ", missingDependencies)
                    );
                }
            }
        } while (changed);
    }

    public void initializeFeatures() {
        List<String> loadOrder = new ArrayList<>();
        Map<String, LoadOrderState> states = new HashMap<>();
        Set<String> skippedFeatures = new LinkedHashSet<>();

        for (String featureName : featureRegistry.getAvailableFeatures().keySet()) {
            if (!resolveFeatureLoadOrder(featureName, states, new ArrayDeque<>(), loadOrder)) {
                skippedFeatures.add(featureName);
            }
        }

        if (!skippedFeatures.isEmpty()) {
            plugin.getLogger().warning("Skipping features due dependency graph issues: " + String.join(", ", skippedFeatures));
        }

        for (String featureName : loadOrder) {
            loadFeature(featureName);
        }
        CommandSync.apply(plugin);
    }

    private boolean resolveFeatureLoadOrder(
            String featureName,
            Map<String, LoadOrderState> states,
            Deque<String> path,
            List<String> loadOrder
    ) {
        LoadOrderState state = states.get(featureName);
        if (state == LoadOrderState.VISITED) {
            return true;
        }
        if (state == LoadOrderState.FAILED) {
            return false;
        }
        if (state == LoadOrderState.VISITING) {
            List<String> cyclePath = new ArrayList<>(path);
            cyclePath.add(featureName);
            plugin.getLogger().severe("Dependency cycle detected: " + String.join(" -> ", cyclePath));
            states.put(featureName, LoadOrderState.FAILED);
            return false;
        }

        FeatureDescriptor descriptor = featureRegistry.getAvailableFeature(featureName);
        if (descriptor == null) {
            plugin.getLogger().severe("Feature '" + featureName + "' is not registered as available.");
            states.put(featureName, LoadOrderState.FAILED);
            return false;
        }

        states.put(featureName, LoadOrderState.VISITING);
        path.addLast(featureName);

        try {
            for (String dependency : descriptor.featureDependencies()) {
                String dependencyKey = resolveFeatureKey(dependency);
                if (dependencyKey == null) {
                    states.put(featureName, LoadOrderState.FAILED);
                    plugin.getLogger().severe(
                            "Feature '" + featureName + "' cannot be loaded because dependency '" + dependency + "' is unavailable."
                    );
                    return false;
                }

                if (!resolveFeatureLoadOrder(dependencyKey, states, path, loadOrder)) {
                    states.put(featureName, LoadOrderState.FAILED);
                    plugin.getLogger().severe(
                            "Feature '" + featureName + "' cannot be loaded because dependency '" + dependencyKey + "' failed."
                    );
                    return false;
                }
            }

            states.put(featureName, LoadOrderState.VISITED);
            loadOrder.add(featureName);
            return true;
        } finally {
            path.removeLast();
        }
    }

    public String resolveFeatureKey(String inputName) {
        if (inputName == null) {
            return null;
        }

        String candidate = inputName.trim();
        if (candidate.isEmpty()) {
            return null;
        }

        if (featureRegistry.getAvailableFeature(candidate) != null || featureRegistry.isFeatureLoaded(candidate)) {
            return candidate;
        }

        String availableCaseMatch = findCaseInsensitiveMatch(candidate, featureRegistry.getAvailableFeatures().keySet());
        if (availableCaseMatch != null) {
            return availableCaseMatch;
        }
        String loadedCaseMatch = findCaseInsensitiveMatch(candidate, featureRegistry.getLoadedFeatureNames());
        if (loadedCaseMatch != null) {
            return loadedCaseMatch;
        }

        for (FeatureDescriptor descriptor : featureRegistry.getAvailableFeatures().values()) {
            if (candidate.equalsIgnoreCase(descriptor.featureName())) {
                return descriptor.registryName();
            }

            String simpleClassName = simpleClassName(descriptor.featureClassName());
            if (candidate.equalsIgnoreCase(simpleClassName)) {
                return descriptor.registryName();
            }
        }

        for (String loadedKey : featureRegistry.getLoadedFeatureNames()) {
            BukkitBaseFeature<?> loadedFeature = featureRegistry.getLoadedFeature(loadedKey);
            if (loadedFeature == null) {
                continue;
            }

            String loadedName = loadedFeature.getFeatureName();
            if (loadedName != null && candidate.equalsIgnoreCase(loadedName)) {
                return loadedKey;
            }
        }

        return null;
    }

    private String findCaseInsensitiveMatch(String candidate, Collection<String> values) {
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(candidate)) {
                return value;
            }
        }
        return null;
    }

    private String simpleClassName(String className) {
        if (className == null || className.isBlank()) {
            return "";
        }
        int lastDot = className.lastIndexOf('.');
        return lastDot < 0 ? className : className.substring(lastDot + 1);
    }

    private boolean isValidFeatureKey(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') {
                return false;
            }
        }
        return true;
    }

    private DependencyCheckResult diagnoseDependenciesRecursively(String featureName) {
        String featureKey = resolveFeatureKey(featureName);
        if (featureKey == null) {
            return new DependencyCheckResult(Set.of(), Set.of(featureName));
        }

        Set<String> missingPlugins = new LinkedHashSet<>();
        Set<String> missingFeatures = new LinkedHashSet<>();
        collectDependencyGaps(featureKey, new HashSet<>(), missingPlugins, missingFeatures);
        return new DependencyCheckResult(missingPlugins, missingFeatures);
    }

    private void collectDependencyGaps(
            String featureName,
            Set<String> visited,
            Set<String> missingPlugins,
            Set<String> missingFeatures
    ) {
        if (!visited.add(featureName)) {
            return;
        }

        FeatureDescriptor descriptor = featureRegistry.getAvailableFeature(featureName);
        if (descriptor == null) {
            missingFeatures.add(featureName);
            return;
        }

        missingPlugins.addAll(getMissingPluginDependencies(featureName));
        for (String dependency : descriptor.featureDependencies()) {
            String dependencyKey = resolveFeatureKey(dependency);
            if (dependencyKey == null) {
                missingFeatures.add(dependency);
                continue;
            }
            if (!featureRegistry.isFeatureLoaded(dependencyKey)) {
                missingFeatures.add(dependencyKey);
            }
            collectDependencyGaps(dependencyKey, visited, missingPlugins, missingFeatures);
        }
    }

    private FeatureDescriptor requireAvailableDescriptor(String inputName) {
        String featureKey = resolveFeatureKey(inputName);
        if (featureKey == null) {
            return null;
        }
        return featureRegistry.getAvailableFeature(featureKey);
    }

    public FeatureEnableResponse enableFeature(String featureName) {
        FeatureDescriptor descriptor = requireAvailableDescriptor(featureName);
        if (descriptor == null) {
            plugin.getLogger().warning("Feature not found: " + featureName);
            return new FeatureEnableResponse(FeatureEnableResult.NOT_FOUND, Set.of(), Set.of());
        }

        String featureKey = descriptor.registryName();
        if (featureRegistry.isFeatureLoaded(featureKey)) {
            plugin.getLogger().warning("Feature already loaded: " + featureKey);
            return new FeatureEnableResponse(FeatureEnableResult.ALREADY_LOADED, Set.of(), Set.of());
        }

        DependencyCheckResult diag = diagnoseDependenciesRecursively(featureKey);
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

        boolean previousEnabled = mainConfigHandler.isFeatureEnabled(featureKey);
        mainConfigHandler.setFeatureEnabled(featureKey, true);

        boolean loaded = loadFeature(featureKey);
        if (!loaded) {
            mainConfigHandler.setFeatureEnabled(featureKey, previousEnabled);
            DependencyCheckResult postLoadDiag = diagnoseDependenciesRecursively(featureKey);
            if (!postLoadDiag.missingPluginDependencies().isEmpty()) {
                return new FeatureEnableResponse(
                        FeatureEnableResult.MISSING_PLUGIN_DEPENDENCY,
                        postLoadDiag.missingPluginDependencies(),
                        postLoadDiag.missingFeatureDependencies()
                );
            }
            if (!postLoadDiag.missingFeatureDependencies().isEmpty()) {
                return new FeatureEnableResponse(
                        FeatureEnableResult.MISSING_FEATURE_DEPENDENCY,
                        postLoadDiag.missingPluginDependencies(),
                        postLoadDiag.missingFeatureDependencies()
                );
            }
            return new FeatureEnableResponse(FeatureEnableResult.FAILED, Set.of(), Set.of());
        }

        CommandSync.apply(plugin);

        return new FeatureEnableResponse(FeatureEnableResult.SUCCESS, Set.of(), Set.of());
    }

    public FeatureDisableResponse disableFeature(String featureName) {
        String featureKey = resolveFeatureKey(featureName);
        if (featureKey == null) {
            plugin.getLogger().warning("Feature not currently loaded: " + featureName);
            return new FeatureDisableResponse(FeatureDisableResult.NOT_LOADED, featureName, Set.of());
        }

        BukkitBaseFeature<?> feature = featureRegistry.getLoadedFeature(featureKey);
        if (feature == null) {
            plugin.getLogger().warning("Feature not currently loaded: " + featureKey);
            return new FeatureDisableResponse(FeatureDisableResult.NOT_LOADED, featureName, Set.of());
        }

        Set<String> dependents = new LinkedHashSet<>(dependencyManager.getDependentFeatures(featureKey));
        for (String dep : dependents) {
            FeatureDisableResponse depResp = disableFeature(dep);
            if (!depResp.success()) {
                plugin.getLogger().warning("Failed to disable dependent feature: " + dep);
            }
        }

        try {
            feature.cleanup();
            mainConfigHandler.setFeatureEnabled(featureKey, false);
            featureRegistry.deregisterLoadedFeature(featureKey);
            plugin.getLogger().info("Feature disabled: " + featureKey);
            CommandSync.apply(plugin);
            return new FeatureDisableResponse(FeatureDisableResult.SUCCESS, featureKey, dependents);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Disable failed: " + featureKey, t);
            return new FeatureDisableResponse(FeatureDisableResult.FAILED, featureKey, dependents);
        }
    }

    public FeatureSoftReloadResponse softReloadFeature(String featureName) {
        String featureKey = resolveFeatureKey(featureName);
        if (featureKey == null || !featureRegistry.isFeatureLoaded(featureKey)) {
            plugin.getLogger().warning("Feature not currently loaded: " + featureName);
            return new FeatureSoftReloadResponse(FeatureSoftReloadResult.NOT_LOADED, featureName);
        }
        try {
            BukkitBaseFeature<?> feature = featureRegistry.getLoadedFeature(featureKey);
            feature.getConfigHandler().reloadConfig();
            feature.getLocalizationHandler().reloadLocalization();
            plugin.getLogger().info("Feature " + featureKey + " soft reloaded.");
            return new FeatureSoftReloadResponse(FeatureSoftReloadResult.SUCCESS, featureKey);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Soft reload failed for: " + featureKey, t);
            return new FeatureSoftReloadResponse(FeatureSoftReloadResult.FAILED, featureKey);
        }
    }

    public FeatureReloadResponse reloadFeature(String featureName) {
        String featureKey = resolveFeatureKey(featureName);
        if (featureKey == null || !featureRegistry.isFeatureLoaded(featureKey)) {
            plugin.getLogger().warning("Feature not currently loaded: " + featureName);
            return new FeatureReloadResponse(FeatureReloadResult.NOT_LOADED, featureName, Set.of());
        }

        Set<String> reloadedDependents = new LinkedHashSet<>();
        try {
            mainConfigHandler.reloadConfig();
            localizationHandler.reloadLocalization();

            BukkitBaseFeature<?> feature = featureRegistry.getLoadedFeature(featureKey);
            feature.cleanup();
            featureRegistry.deregisterLoadedFeature(featureKey);

            boolean hasReloaded = loadFeature(featureKey);
            if (!hasReloaded) {
                plugin.getLogger().severe("Reload failed for: " + featureKey + " (feature did not load back)");
                return new FeatureReloadResponse(FeatureReloadResult.FAILED, featureKey, reloadedDependents);
            }

            plugin.getLogger().info("Feature " + featureKey + " reloaded.");

            for (String dependent : dependencyManager.getDependentFeatures(featureKey)) {
                plugin.getLogger().info("Reloading dependent feature: " + dependent);
                FeatureReloadResponse depResp = reloadFeature(dependent);
                if (depResp.success()) reloadedDependents.add(dependent);
            }
            return new FeatureReloadResponse(FeatureReloadResult.SUCCESS, featureKey, reloadedDependents);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Reload failed for: " + featureKey, t);
            return new FeatureReloadResponse(FeatureReloadResult.FAILED, featureKey, reloadedDependents);
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
            try {
                if (feature != null) {
                    feature.cleanup();
                }
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
        String featureKey = resolveFeatureKey(featureName);
        if (featureKey == null) {
            return Set.of();
        }

        FeatureDescriptor descriptor = featureRegistry.getAvailableFeature(featureKey);
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

    public boolean loadFeature(String featureName) {
        String featureKey = resolveFeatureKey(featureName);
        if (featureKey == null) {
            plugin.getLogger().warning("Feature not found: " + featureName);
            return false;
        }

        if (featureRegistry.isFeatureLoaded(featureKey)) {
            plugin.getLogger().warning("Feature already loaded: " + featureKey);
            return false;
        }

        FeatureDescriptor descriptor = featureRegistry.getAvailableFeature(featureKey);
        if (descriptor == null) {
            plugin.getLogger().warning("Feature not found: " + featureKey);
            return false;
        }

        mainConfigHandler.registerFeature(featureKey);
        boolean enabled = mainConfigHandler.isFeatureEnabled(featureKey);

        Set<String> missingPlugins = getMissingPluginDependencies(featureKey);
        if (!missingPlugins.isEmpty()) {
            if (enabled) {
                plugin.getLogger().warning("Feature " + featureKey + " cannot be enabled due to missing plugin dependency(s): " + String.join(", ", missingPlugins));
            }
            return false;
        }

        if (enabled && !dependencyManager.areDependenciesMet(featureKey)) {
            plugin.getLogger().warning("Feature " + featureKey + " is missing dependencies and cannot be enabled.");
            return false;
        }

        BukkitBaseFeature<?> feature = FeatureFactory.createFeature(descriptor.featureClassName(), plugin);
        if (feature == null) {
            return false;
        }

        mainConfigHandler.injectFeatureDefaults(featureKey, feature.getDefaultConfig());
        localizationHandler.registerDefaultMessages(feature.getDefaultMessages());
        feature.getConfigHandler().reloadConfig();

        if (!enabled) {
            return false;
        }

        try {
            feature.initialize();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Feature '" + featureKey + "' failed to initialize.", t);
            try {
                feature.cleanup();
            } catch (Throwable cleanupError) {
                plugin.getLogger().log(Level.SEVERE, "Feature '" + featureKey + "' failed to cleanup after initialization failure.", cleanupError);
            }
            return false;
        }

        featureRegistry.registerLoadedFeature(featureKey, feature);
        plugin.getLogger().info("Feature loaded: " + featureKey);
        return true;
    }

    private boolean isPluginEnabled(String pluginName) {
        var foundPlugin = plugin.getServer().getPluginManager().getPlugin(pluginName);
        return foundPlugin != null && foundPlugin.isEnabled();
    }
}
