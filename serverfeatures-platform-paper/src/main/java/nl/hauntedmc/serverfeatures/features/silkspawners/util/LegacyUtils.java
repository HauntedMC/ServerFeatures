package nl.hauntedmc.serverfeatures.features.silkspawners.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LegacyUtils {

    public static Optional<String> extractLegacyMobType(String metaString) {
        Pattern PATTERN = Pattern.compile("ms_mob\\s*:\\s*\"([^\"]+)\"");
        Matcher m = PATTERN.matcher(metaString);
        if (m.find()) {
            return Optional.of(m.group(1));
        }
        return Optional.empty();
    }
}
