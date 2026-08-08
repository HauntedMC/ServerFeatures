package nl.hauntedmc.serverfeatures.features.builtincommandblocker.internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record BuiltinCommandSnapshot(
        Set<String> blockedCommands,
        Map<String, Integer> detectedSources
) {

    public static final BuiltinCommandSnapshot EMPTY = new BuiltinCommandSnapshot(Set.of(), Map.of());

    public BuiltinCommandSnapshot {
        blockedCommands = Set.copyOf(blockedCommands);
        detectedSources = Collections.unmodifiableMap(new LinkedHashMap<>(detectedSources));
    }

    public boolean isBlocked(String command) {
        return blockedCommands.contains(BuiltinCommandBlockerSettings.normalizeCommand(command));
    }

    public int blockedCommandCount() {
        return blockedCommands.size();
    }

    public List<String> sortedBlockedCommands() {
        return blockedCommands.stream().sorted().toList();
    }
}
