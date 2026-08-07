package nl.hauntedmc.serverfeatures.framework.loader;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;
import nl.hauntedmc.serverfeatures.api.feature.stateful.SnapshotState;
import nl.hauntedmc.serverfeatures.api.feature.stateful.StatefulFeature;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.FeatureFactory;
import nl.hauntedmc.serverfeatures.framework.command.sync.CommandSync;
import nl.hauntedmc.serverfeatures.framework.config.MainConfigHandler;
import nl.hauntedmc.serverfeatures.framework.feature.FeatureScopeFactory;
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

import java.util.*;
import java.util.logging.Level;

public final class FeatureLoadManager {

    private final ServerFeatures plugin;
    private final MainConfigHandler mainConfigHandler;
    private final FeatureRegistry featureRegistry;
    private final FeatureDependencyManager dependencyManager;
    private final FeatureScopeFactory featureScopeFactory;

    private FeatureLoadManager(ServerFeatures plugin) {
        this(plugin, plugin.getFeatureScopeFactory(), true);
    }

    FeatureLoadManager(ServerFeatures plugin, FeatureScopeFactory featureScopeFactory, boolean discoverFeatures) {
        this.plugin = plugin;
        this.mainConfigHandler = plugin.getConfigHandler();
        this.featureRegistry = new FeatureRegistry();
        this.featureScopeFactory = Objects.requireNonNull(featureScopeFactory, "featureScopeFactory");
        this.dependencyManager = new FeatureDependencyManager(this, plugin);
        if (discoverFeatures) {
            discoverFeatures();
        }
    }

    public static FeatureLoadManager create(ServerFeatures plugin) {
        return new FeatureLoadManager(plugin);
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
                String conflictingKey = FeatureKeyResolver.findCaseInsensitiveMatch(
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
        prepareFeatureStorage();

        plugin.getLogger().info("Discovered features: " + featureRegistry.getAvailableFeatures().keySet());
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
        if (!FeatureKeyResolver.isValidFeatureKey(featureKey)) {
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
        Set<String> optionalDependencies = normalizeFeatureDependencies(
                featureClassName,
                featureKey,
                meta.getOptionalDependencies()
        );
        if (optionalDependencies == null) {
            return Optional.empty();
        }
        Set<String> pluginDependencies = meta.getPluginDependencies() == null
                ? Set.of()
                : new LinkedHashSet<>(meta.getPluginDependencies());

        return Optional.of(new FeatureDescriptor(
                featureKey,
                featureClassName,
                meta.getClass().asSubclass(BaseMeta.class),
                featureName,
                featureVersion,
                featureDependencies,
                optionalDependencies,
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
            if (!FeatureKeyResolver.isValidFeatureKey(dependencyKey)) {
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

    private void prepareFeatureStorage() {
        for (FeatureDescriptor descriptor : new ArrayList<>(featureRegistry.getAvailableFeatures().values())) {
            FeatureContext<?> context = null;
            try {
                context = createFeatureContext(descriptor);
                BukkitBaseFeature<?> template = FeatureFactory.createFeature(descriptor.featureClassName(), context);
                if (template == null) {
                    plugin.getLogger().warning(
                            "Skipping storage preparation for feature '" + descriptor.registryName()
                                    + "': unable to instantiate template."
                    );
                    continue;
                }
                template.getConfigHandler().injectDefaults(template.getDefaultConfig());
                template.getLocalizationHandler().registerDefaultMessages(template.getDefaultMessages());
            } catch (Throwable throwable) {
                plugin.getLogger().log(
                        Level.SEVERE,
                        "Failed preparing config/localization storage for feature '"
                                + descriptor.registryName() + "'.",
                        throwable
                );
            } finally {
                if (context != null) {
                    cleanupPreparationContext(context, descriptor.registryName());
                }
            }
        }
    }

    private void cleanupPreparationContext(FeatureContext<?> context, String featureName) {
        try {
            context.lifecycleManager().cleanup();
        } catch (Throwable throwable) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Failed cleaning temporary framework scope for feature '" + featureName + "'.",
                    throwable
            );
        }
    }

    public synchronized void initializeFeatures() {
        FeatureLoadOrderResolver.Result result = FeatureLoadOrderResolver.resolveLoadOrder(
                featureRegistry.getAvailableFeatures().keySet(),
                featureRegistry::getAvailableFeature,
                this::resolveFeatureKey,
                msg -> plugin.getLogger().severe(msg)
        );

        if (!result.skippedFeatures().isEmpty()) {
            plugin.getLogger().warning(
                    "Skipping features due dependency graph issues: " + String.join(", ", result.skippedFeatures())
            );
        }

        for (String featureName : result.loadOrder()) {
            loadFeature(featureName);
        }
        CommandSync.apply(plugin);
    }

    public String resolveFeatureKey(String inputName) {
        return FeatureKeyResolver.resolveFeatureKey(
                inputName,
                featureRegistry.getAvailableFeatures(),
                featureRegistry.getLoadedFeatureNames(),
                loadedKey -> {
                    BukkitBaseFeature<?> loadedFeature = featureRegistry.getLoadedFeature(loadedKey);
                    return loadedFeature == null ? null : loadedFeature.getFeatureName();
                }
        );
    }

    private DependencyCheckResult diagnoseDependenciesRecursively(String featureName) {
        return FeatureDependencyDiagnostics.diagnoseDependenciesRecursively(
                featureName,
                this::resolveFeatureKey,
                featureRegistry::getAvailableFeature,
                featureRegistry::isFeatureLoaded,
                this::getMissingPluginDependencies
        );
    }

    private FeatureDescriptor requireAvailableDescriptor(String inputName) {
        String featureKey = resolveFeatureKey(inputName);
        if (featureKey == null) {
            return null;
        }
        return featureRegistry.getAvailableFeature(featureKey);
    }

    public synchronized FeatureEnableResponse enableFeature(String featureName) {
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

    public synchronized FeatureDisableResponse disableFeature(String featureName) {
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
        Throwable failure = null;
        for (String dep : dependents) {
            FeatureDisableResponse depResp = disableFeature(dep);
            if (!depResp.success()) {
                plugin.getLogger().warning("Failed to disable dependent feature: " + dep);
                failure = appendFailure(
                        failure,
                        new IllegalStateException("Failed to disable dependent feature '" + dep + "'.")
                );
            }
        }

        try {
            Throwable cleanupFailure = cleanupAndDeregister(featureKey, feature);
            failure = appendFailure(failure, cleanupFailure);
            try {
                mainConfigHandler.setFeatureEnabled(featureKey, false);
            } catch (Throwable configFailure) {
                failure = appendFailure(failure, configFailure);
            }
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "Disable completed with failures: " + featureKey, failure);
                return new FeatureDisableResponse(FeatureDisableResult.FAILED, featureKey, dependents);
            }

            plugin.getLogger().info("Feature disabled: " + featureKey);
            return new FeatureDisableResponse(FeatureDisableResult.SUCCESS, featureKey, dependents);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Disable failed: " + featureKey, t);
            return new FeatureDisableResponse(FeatureDisableResult.FAILED, featureKey, dependents);
        } finally {
            CommandSync.apply(plugin);
        }
    }

    public synchronized FeatureSoftReloadResponse softReloadFeature(String featureName) {
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

    public synchronized FeatureReloadResponse reloadFeature(String featureName) {
        String featureKey = resolveFeatureKey(featureName);
        if (featureKey == null || !featureRegistry.isFeatureLoaded(featureKey)) {
            plugin.getLogger().warning("Feature not currently loaded: " + featureName);
            return new FeatureReloadResponse(FeatureReloadResult.NOT_LOADED, featureName, Set.of());
        }

        Set<String> cascade = collectLoadedDependentClosure(featureKey);
        List<String> reloadOrder = resolveReloadOrder(cascade);
        if (reloadOrder.size() != cascade.size()) {
            plugin.getLogger().severe("Reload failed for '" + featureKey
                    + "': unable to resolve a complete dependent load order.");
            return new FeatureReloadResponse(FeatureReloadResult.FAILED, featureKey, Set.of());
        }

        Map<String, Optional<SnapshotState>> reloadStates = new LinkedHashMap<>();
        try {
            for (String reloadKey : reloadOrder) {
                BukkitBaseFeature<?> loaded = featureRegistry.getLoadedFeature(reloadKey);
                if (loaded == null) {
                    throw new IllegalStateException(
                            "Feature '" + reloadKey + "' disappeared while preparing the reload."
                    );
                }
                reloadStates.put(reloadKey, captureReloadState(reloadKey, loaded));
            }
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.SEVERE, "Reload state capture failed for: " + featureKey, throwable);
            return new FeatureReloadResponse(FeatureReloadResult.FAILED, featureKey, Set.of());
        }

        Throwable cleanupFailure = null;
        ListIterator<String> teardown = reloadOrder.listIterator(reloadOrder.size());
        while (teardown.hasPrevious()) {
            String reloadKey = teardown.previous();
            cleanupFailure = appendFailure(
                    cleanupFailure,
                    cleanupAndDeregister(reloadKey, featureRegistry.getLoadedFeature(reloadKey))
            );
        }
        if (cleanupFailure != null) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "Reload aborted after cleanup failures in the '" + featureKey + "' cascade.",
                    cleanupFailure
            );
            CommandSync.apply(plugin);
            return new FeatureReloadResponse(FeatureReloadResult.FAILED, featureKey, Set.of());
        }

        Set<String> failed = new LinkedHashSet<>();
        Set<String> reloadedDependents = new LinkedHashSet<>();
        for (String reloadKey : reloadOrder) {
            FeatureDescriptor descriptor = featureRegistry.getAvailableFeature(reloadKey);
            boolean blockedByFailedDependency = descriptor != null
                    && descriptor.featureDependencies().stream()
                    .map(this::resolveFeatureKey)
                    .anyMatch(failed::contains);
            if (blockedByFailedDependency) {
                failed.add(reloadKey);
                plugin.getLogger().warning("Skipping reload of dependent feature '" + reloadKey
                        + "' because one of its dependencies failed to reload.");
                continue;
            }

            Optional<SnapshotState> reloadState = reloadStates.getOrDefault(reloadKey, Optional.empty());
            boolean loaded = reloadState.isPresent()
                    ? loadFeature(reloadKey, reloadState.get())
                    : loadFeature(reloadKey);
            if (!loaded) {
                failed.add(reloadKey);
                plugin.getLogger().severe("Feature did not load back during cascade reload: " + reloadKey);
                continue;
            }
            if (!reloadKey.equals(featureKey)) {
                reloadedDependents.add(reloadKey);
            }
        }

        CommandSync.apply(plugin);
        if (!failed.isEmpty()) {
            plugin.getLogger().severe("Reload cascade for '" + featureKey
                    + "' was incomplete. Failed features: " + String.join(", ", failed));
            return new FeatureReloadResponse(FeatureReloadResult.FAILED, featureKey, reloadedDependents);
        }
        plugin.getLogger().info("Feature " + featureKey + " and " + reloadedDependents.size()
                + " dependent feature(s) reloaded.");
        return new FeatureReloadResponse(FeatureReloadResult.SUCCESS, featureKey, reloadedDependents);
    }

    public FeatureRegistry getFeatureRegistry() {
        return featureRegistry;
    }

    public synchronized void unloadAllFeatures() {
        plugin.getLogger().info("Unloading all loaded features...");
        List<String> loadedFeatureNames = new ArrayList<>(featureRegistry.getLoadedFeatureNames());
        ListIterator<String> iterator = loadedFeatureNames.listIterator(loadedFeatureNames.size());
        while (iterator.hasPrevious()) {
            String featureName = iterator.previous();
            BukkitBaseFeature<?> feature = featureRegistry.getLoadedFeature(featureName);
            Throwable failure = cleanupAndDeregister(featureName, feature);
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to cleanup feature during unload: " + featureName, failure);
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

    public synchronized boolean loadFeature(String featureName) {
        return loadFeature(featureName, null);
    }

    private boolean loadFeature(String featureName, SnapshotState reloadState) {
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

        boolean enabled = mainConfigHandler.isFeatureEnabled(featureKey);
        if (!enabled) {
            return false;
        }

        Set<String> missingPlugins = getMissingPluginDependencies(featureKey);
        if (!missingPlugins.isEmpty()) {
            plugin.getLogger().warning("Feature " + featureKey
                    + " cannot be enabled due to missing plugin dependency(s): "
                    + String.join(", ", missingPlugins));
            return false;
        }

        if (!dependencyManager.areDependenciesMet(featureKey)) {
            plugin.getLogger().warning("Feature " + featureKey + " is missing dependencies and cannot be enabled.");
            return false;
        }

        FeatureContext<?> context = null;
        BukkitBaseFeature<?> feature = null;
        boolean initializationStarted = false;
        try {
            context = createFeatureContext(descriptor);
            feature = FeatureFactory.createFeature(descriptor.featureClassName(), context);
            if (feature == null) {
                cleanupPreparationContext(context, featureKey);
                return false;
            }

            feature.getConfigHandler().injectDefaults(feature.getDefaultConfig());
            feature.getLocalizationHandler().registerDefaultMessages(feature.getDefaultMessages());
            feature.getConfigHandler().reloadConfig();
            feature.getLocalizationHandler().reloadLocalization();

            initializationStarted = true;
            feature.initialize();
            if (reloadState != null) {
                restoreReloadState(featureKey, feature, reloadState);
            }

            // Services and ingress hooks are deliberately invisible until initialization and
            // reload-state restoration have both succeeded.
            feature.getLifecycleManager().getApiManager().activateServices();

            featureRegistry.registerLoadedFeature(featureKey, feature);
            plugin.getLogger().info("Feature loaded: " + featureKey);
            return true;
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.SEVERE, "Feature '" + featureKey + "' failed to load.", throwable);
            if (feature != null && initializationStarted) {
                try {
                    feature.cleanup();
                } catch (Throwable cleanupError) {
                    throwable.addSuppressed(cleanupError);
                }
            } else if (context != null) {
                try {
                    context.lifecycleManager().cleanup();
                } catch (Throwable cleanupError) {
                    throwable.addSuppressed(cleanupError);
                }
            }
            return false;
        }
    }

    private boolean isPluginEnabled(String pluginName) {
        var foundPlugin = plugin.getServer().getPluginManager().getPlugin(pluginName);
        return foundPlugin != null && foundPlugin.isEnabled();
    }

    private FeatureContext<?> createFeatureContext(FeatureDescriptor descriptor) {
        return featureScopeFactory.createContext(descriptor.createMeta());
    }

    private Optional<SnapshotState> captureReloadState(String featureKey, BukkitBaseFeature<?> feature) {
        if (!(feature instanceof StatefulFeature<?> statefulFeature)) {
            return Optional.empty();
        }
        try {
            @SuppressWarnings("unchecked")
            StatefulFeature<SnapshotState> typed = (StatefulFeature<SnapshotState>) statefulFeature;
            Optional<SnapshotState> state = typed.captureReloadState();
            return state == null ? Optional.empty() : state;
        } catch (Throwable throwable) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "Feature '" + featureKey + "' failed to capture reload state.",
                    throwable
            );
            throw throwable;
        }
    }

    private void restoreReloadState(
            String featureKey,
            BukkitBaseFeature<?> feature,
            SnapshotState reloadState
    ) {
        if (!(feature instanceof StatefulFeature<?> statefulFeature)) {
            throw new IllegalStateException(
                    "Captured reload state exists, but replacement feature does not implement StatefulFeature."
            );
        }
        @SuppressWarnings("unchecked")
        StatefulFeature<SnapshotState> typed = (StatefulFeature<SnapshotState>) statefulFeature;
        typed.restoreReloadState(reloadState);
        plugin.getLogger().info("Feature " + featureKey + " restored reload state.");
    }

    private Set<String> collectLoadedDependentClosure(String featureKey) {
        LinkedHashSet<String> closure = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        closure.add(featureKey);
        pending.add(featureKey);
        while (!pending.isEmpty()) {
            String dependency = pending.removeFirst();
            for (String dependent : dependencyManager.getDependentFeatures(dependency)) {
                if (closure.add(dependent)) {
                    pending.addLast(dependent);
                }
            }
        }
        return closure;
    }

    private List<String> resolveReloadOrder(Set<String> cascade) {
        FeatureLoadOrderResolver.Result result = FeatureLoadOrderResolver.resolveLoadOrder(
                featureRegistry.getAvailableFeatures().keySet(),
                featureRegistry::getAvailableFeature,
                this::resolveFeatureKey,
                msg -> plugin.getLogger().severe(msg)
        );
        return result.loadOrder().stream().filter(cascade::contains).toList();
    }

    private Throwable cleanupAndDeregister(String featureKey, BukkitBaseFeature<?> feature) {
        Throwable failure = null;
        try {
            if (feature != null) {
                feature.cleanup();
            }
        } catch (Throwable throwable) {
            failure = throwable;
        } finally {
            featureRegistry.deregisterLoadedFeature(featureKey);
        }
        return failure;
    }

    private static Throwable appendFailure(Throwable current, Throwable addition) {
        if (addition == null) {
            return current;
        }
        if (current == null) {
            return addition;
        }
        current.addSuppressed(addition);
        return current;
    }
}
