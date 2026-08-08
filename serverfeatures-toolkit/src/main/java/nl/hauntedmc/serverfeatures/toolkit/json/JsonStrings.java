package nl.hauntedmc.serverfeatures.toolkit.json;

/** Small JSON string helper for integrations that intentionally do not need a serializer model. */
public final class JsonStrings {
    private JsonStrings() { }

    public static String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}
