package nl.hauntedmc.serverfeatures.common.util;

public class JsonUtils {

    /**
     * Escapes special characters in a string for JSON payloads.
     *
     * @param str the input string.
     * @return the escaped string.
     */
    public static String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}
