package nl.hauntedmc.serverfeatures.localization;

public enum Language {
    NL("NL"),
    EN("EN"),
    DE("DE");

    private final String code;

    Language(String code) {
        this.code = code;
    }

    /**
     * Returns the filename for this language.
     * For example, for NL it returns "messages_NL.yml".
     */
    public String getFileName() {
        return "messages_" + code + ".yml";
    }
}
