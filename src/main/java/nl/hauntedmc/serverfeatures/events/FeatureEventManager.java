package nl.hauntedmc.serverfeatures.events;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class FeatureEventManager {
    private static final Map<Class<?>, Set<Consumer<Object>>> listeners = new HashMap<>();

    public static <T> void registerListener(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new HashSet<>()).add((Consumer<Object>) listener);
    }

    public static <T> void triggerEvent(T event) {
        Set<Consumer<Object>> eventListeners = listeners.get(event.getClass());
        if (eventListeners != null) {
            eventListeners.forEach(listener -> listener.accept(event));
        }
    }
}
