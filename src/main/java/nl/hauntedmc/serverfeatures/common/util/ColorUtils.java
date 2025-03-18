package nl.hauntedmc.serverfeatures.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.HashMap;

public class ColorUtils {

    // Pattern to detect various hex formats
    private static final Pattern HEX_PATTERN = Pattern.compile(
            "&#([0-9a-fA-F]{6})(.)|" +  // &#FFFFFFa
                    "§#([0-9a-fA-F]{6})(.)|" +  // §#FFFFFFa
                    "&x(&[0-9a-fA-F]){6}(.)|" + // &x&F&F&F&F&F&Fa
                    "<#([0-9a-fA-F]{6})>(.)|" + // <#FFFFFF>a
                    "<##([0-9a-fA-F]{6})>(.)"   // <##FFFFFF>a
    );

    // Pattern to detect ampersand color codes (&X)
    private static final Pattern AMPERSAND_PATTERN = Pattern.compile("&([0-9a-fA-Fk-oK-OrR])");

    // Pattern to detect MiniMessage color tags (e.g., <red>, <aqua>)
    private static final Pattern MINIMESSAGE_PATTERN = Pattern.compile("<(\\w+)>");

    // MiniMessage color mapping to Minecraft color codes (§X)
    private static final Map<String, String> MINIMESSAGE_TO_COLOR = new HashMap<>();

    static {
        // Standard color mappings
        MINIMESSAGE_TO_COLOR.put("black", "§0");
        MINIMESSAGE_TO_COLOR.put("dark_blue", "§1");
        MINIMESSAGE_TO_COLOR.put("dark_green", "§2");
        MINIMESSAGE_TO_COLOR.put("dark_aqua", "§3");
        MINIMESSAGE_TO_COLOR.put("dark_red", "§4");
        MINIMESSAGE_TO_COLOR.put("dark_purple", "§5");
        MINIMESSAGE_TO_COLOR.put("gold", "§6");
        MINIMESSAGE_TO_COLOR.put("gray", "§7");
        MINIMESSAGE_TO_COLOR.put("dark_gray", "§8");
        MINIMESSAGE_TO_COLOR.put("blue", "§9");
        MINIMESSAGE_TO_COLOR.put("green", "§a");
        MINIMESSAGE_TO_COLOR.put("aqua", "§b");
        MINIMESSAGE_TO_COLOR.put("red", "§c");
        MINIMESSAGE_TO_COLOR.put("light_purple", "§d");
        MINIMESSAGE_TO_COLOR.put("yellow", "§e");
        MINIMESSAGE_TO_COLOR.put("white", "§f");

        // Formatting codes
        MINIMESSAGE_TO_COLOR.put("bold", "§l");
        MINIMESSAGE_TO_COLOR.put("italic", "§o");
        MINIMESSAGE_TO_COLOR.put("underline", "§n");
        MINIMESSAGE_TO_COLOR.put("strikethrough", "§m");
        MINIMESSAGE_TO_COLOR.put("reset", "§r");
    }

    /**
     * Converts various hex formats to the official `§x§R§R§G§G§B§B` format.
     */
    public static String translateHexColors(String input) {
        StringBuilder result = new StringBuilder(input.length()); // Pre-allocate capacity
        Matcher matcher = HEX_PATTERN.matcher(input);
        int lastEnd = 0;

        while (matcher.find()) {
            result.append(input, lastEnd, matcher.start()); // Append unmodified text before match

            String hex = null;
            char character = '\0';

            // Determine which group matched
            for (int i = 1; i <= 5; i += 2) {
                if (matcher.group(i) != null) {
                    hex = matcher.group(i).toUpperCase();
                    character = matcher.group(i + 1).charAt(0);
                    break;
                }
            }

            // Handle &x&F&F&F&F&F&Fa separately
            if (hex == null && matcher.group(6) != null) {
                hex = matcher.group().replaceAll("&x|&", "").substring(0, 6).toUpperCase();
                character = matcher.group(7).charAt(0);
            }

            if (hex != null) {
                result.append(convertToStandardFormat(hex, character));
            }

            lastEnd = matcher.end();
        }
        result.append(input, lastEnd, input.length()); // Append remaining text

        return result.toString();
    }

    /**
     * Converts ampersand color codes (&X) to `§X` formatting.
     */
    public static String translateAmpersandColors(String input) {
        return AMPERSAND_PATTERN.matcher(input).replaceAll("§$1");
    }

    /**
     * Converts MiniMessage color tags (e.g., `<red>`, `<aqua>`) to `§X` formatting.
     */
    public static String translateMiniMessageColors(String input) {
        Matcher matcher = MINIMESSAGE_PATTERN.matcher(input);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String colorName = matcher.group(1).toLowerCase();
            String minecraftColor = MINIMESSAGE_TO_COLOR.get(colorName);
            if (minecraftColor != null) {
                matcher.appendReplacement(result, minecraftColor);
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Converts hex color codes into the official `§x§R§R§G§G§B§B` format.
     */
    private static String convertToStandardFormat(String hex, char character) {
        return "§x§" + hex.charAt(0) + "§" + hex.charAt(1) + "§" + hex.charAt(2) + "§" +
                hex.charAt(3) + "§" + hex.charAt(4) + "§" + hex.charAt(5) + character;
    }

}
