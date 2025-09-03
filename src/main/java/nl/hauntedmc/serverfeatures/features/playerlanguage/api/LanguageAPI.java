package nl.hauntedmc.serverfeatures.features.playerlanguage.api;

import nl.hauntedmc.commonlib.localization.Language;

import java.util.UUID;

public interface LanguageAPI {
    Language get(UUID playerUuid);
    void set(UUID playerUuid, Language language);
}
