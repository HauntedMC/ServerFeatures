package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.NBTType;
import de.tr7zw.changeme.nbtapi.NbtApiException;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Removes only item components that Minecraft's codec identifies as the single missed input.
 */
final class MalformedItemComponentRepair {

    private static final String COMPONENTS_KEY = "components";
    private static final String CODEC_FAILURE_MARKER =
            "Failed to convert NBT to ItemStack. DataResult.Error[";
    private static final String MISSED_INPUT_MARKER = "missed input:";
    private static final ItemNbtCodec NBT_API_CODEC = new ItemNbtCodec() {
        @Override
        public ItemStack decode(ReadWriteNBT item) {
            return NBT.itemStackFromNBT(item);
        }

        @Override
        public ReadWriteNBT copy(ReadWriteNBT item) {
            ReadWriteNBT copy = NBT.createNBTObject();
            copy.mergeCompound(item);
            return copy;
        }

        @Override
        public ItemStack validateAndClone(ItemStack decoded, String location) throws IOException {
            if (decoded == null || decoded.getType().isAir() || decoded.getAmount() <= 0) {
                return null;
            }
            if (decoded.getAmount() > decoded.getMaxStackSize()) {
                throw new IOException(
                        "Player item at " + location + " exceeds its legal stack size"
                );
            }
            return decoded.clone();
        }
    };

    private MalformedItemComponentRepair() {
    }

    static Result decode(ReadWriteNBT item, String location, boolean repair) throws IOException {
        return decode(item, location, repair, NBT_API_CODEC);
    }

    static Result decode(
            ReadWriteNBT item,
            String location,
            boolean repair,
            ItemNbtCodec codec
    ) throws IOException {
        try {
            return new Result(decodeValidated(item, location, codec), List.of());
        } catch (NbtApiException initialFailure) {
            if (!repair) {
                throw decodeFailure(location, initialFailure);
            }
            try {
                return repairAndDecode(item, location, initialFailure, codec);
            } catch (IOException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw decodeFailure(location, exception);
            }
        } catch (RuntimeException exception) {
            throw decodeFailure(location, exception);
        }
    }

    private static Result repairAndDecode(
            ReadWriteNBT original,
            String location,
            NbtApiException initialFailure,
            ItemNbtCodec codec
    ) throws IOException {
        ReadWriteNBT candidate = codec.copy(original);
        List<String> removedComponents = new ArrayList<>();
        NbtApiException currentFailure = initialFailure;

        while (true) {
            Optional<String> rejected = rejectedComponentKey(candidate, currentFailure);
            if (rejected.isEmpty() || removedComponents.contains(rejected.get())) {
                throw decodeFailure(location, currentFailure);
            }

            String componentKey = rejected.get();
            ReadWriteNBT components = candidate.getCompound(COMPONENTS_KEY);
            components.removeKey(componentKey);
            removedComponents.add(componentKey);
            if (components.isEmpty()) {
                candidate.removeKey(COMPONENTS_KEY);
            }

            try {
                ItemStack decoded = decodeValidated(candidate, location, codec);
                original.clearNBT();
                original.mergeCompound(candidate);
                return new Result(decoded, List.copyOf(removedComponents));
            } catch (NbtApiException exception) {
                currentFailure = exception;
            } catch (RuntimeException exception) {
                throw decodeFailure(location, exception);
            }
        }
    }

    static Optional<String> rejectedComponentKey(
            ReadWriteNBT item,
            NbtApiException failure
    ) {
        if (!item.hasTag(COMPONENTS_KEY, NBTType.NBTTagCompound)) {
            return Optional.empty();
        }
        String diagnostic = missedInputDiagnostic(failure);
        if (diagnostic == null) {
            return Optional.empty();
        }

        Set<String> missedInputKeys = topLevelCompoundKeys(diagnostic);
        ReadWriteNBT components = item.getCompound(COMPONENTS_KEY);
        String match = null;
        for (String key : components.getKeys()) {
            if (!missedInputKeys.contains(key)) {
                continue;
            }
            if (match != null) {
                return Optional.empty();
            }
            match = key;
        }
        return Optional.ofNullable(match);
    }

    /**
     * Reads only keys in the outer missed-input compound. A textual search is unsafe here because
     * custom names and lore can contain text that looks like another component entry.
     */
    private static Set<String> topLevelCompoundKeys(String diagnostic) {
        int cursor = diagnostic.indexOf('{');
        if (cursor < 0) {
            return Set.of();
        }
        cursor++;

        Set<String> keys = new LinkedHashSet<>();
        while (true) {
            cursor = skipWhitespace(diagnostic, cursor);
            if (cursor >= diagnostic.length() || diagnostic.charAt(cursor) == '}') {
                return keys;
            }

            ParsedKey parsedKey = parseKey(diagnostic, cursor);
            if (parsedKey == null) {
                return Set.of();
            }
            cursor = skipWhitespace(diagnostic, parsedKey.end());
            if (cursor >= diagnostic.length() || diagnostic.charAt(cursor) != ':') {
                return Set.of();
            }
            keys.add(parsedKey.value());

            cursor = skipValue(diagnostic, cursor + 1);
            if (cursor < 0 || cursor >= diagnostic.length()) {
                return Set.of();
            }
            char delimiter = diagnostic.charAt(cursor);
            if (delimiter == '}') {
                return keys;
            }
            if (delimiter != ',') {
                return Set.of();
            }
            cursor++;
        }
    }

    private static ParsedKey parseKey(String input, int start) {
        char first = input.charAt(start);
        if (first == '"' || first == '\'') {
            StringBuilder key = new StringBuilder();
            boolean escaped = false;
            for (int cursor = start + 1; cursor < input.length(); cursor++) {
                char character = input.charAt(cursor);
                if (escaped) {
                    key.append(character);
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == first) {
                    return new ParsedKey(key.toString(), cursor + 1);
                } else {
                    key.append(character);
                }
            }
            return null;
        }

        int cursor = start;
        while (cursor < input.length()) {
            char character = input.charAt(cursor);
            if (character == ':' || Character.isWhitespace(character)) {
                break;
            }
            if (character == ',' || character == '{' || character == '}'
                    || character == '[' || character == ']') {
                return null;
            }
            cursor++;
        }
        return cursor == start ? null : new ParsedKey(input.substring(start, cursor), cursor);
    }

    private static int skipValue(String input, int start) {
        int compoundDepth = 0;
        int listDepth = 0;
        int arrayDepth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int cursor = start; cursor < input.length(); cursor++) {
            char character = input.charAt(cursor);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '"' || character == '\'') {
                quote = character;
            } else if (character == '{') {
                compoundDepth++;
            } else if (character == '}') {
                if (compoundDepth == 0 && listDepth == 0 && arrayDepth == 0) {
                    return cursor;
                }
                compoundDepth--;
                if (compoundDepth < 0) {
                    return -1;
                }
            } else if (character == '[') {
                listDepth++;
            } else if (character == ']') {
                listDepth--;
                if (listDepth < 0) {
                    return -1;
                }
            } else if (character == '(') {
                arrayDepth++;
            } else if (character == ')') {
                arrayDepth--;
                if (arrayDepth < 0) {
                    return -1;
                }
            } else if (character == ',' && compoundDepth == 0
                    && listDepth == 0 && arrayDepth == 0) {
                return cursor;
            }
        }
        return -1;
    }

    private static int skipWhitespace(String input, int start) {
        int cursor = start;
        while (cursor < input.length() && Character.isWhitespace(input.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static String missedInputDiagnostic(NbtApiException failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        String diagnostic = null;
        Throwable current = failure;
        while (current != null && visited.add(current)) {
            String message = current.getMessage();
            if (message != null) {
                int codecFailure = message.indexOf(CODEC_FAILURE_MARKER);
                int missedInput = codecFailure < 0
                        ? -1
                        : message.indexOf(MISSED_INPUT_MARKER, codecFailure);
                if (missedInput >= 0 && isCodecFailurePrefix(message, codecFailure)) {
                    int diagnosticEnd = message.indexOf("] {", missedInput);
                    diagnostic = diagnosticEnd < 0
                            ? message.substring(missedInput)
                            : message.substring(missedInput, diagnosticEnd);
                }
            }
            current = current.getCause();
        }
        return diagnostic;
    }

    private static boolean isCodecFailurePrefix(String message, int codecFailure) {
        for (int index = 0; index < codecFailure; index++) {
            char character = message.charAt(index);
            if (character == '{' || character == '}' || character == '"'
                    || character == '\'') {
                return false;
            }
        }
        return true;
    }

    private static ItemStack decodeValidated(
            ReadWriteNBT item,
            String location,
            ItemNbtCodec codec
    ) throws IOException {
        return codec.validateAndClone(codec.decode(item), location);
    }

    private static IOException decodeFailure(String location, RuntimeException failure) {
        return new IOException("Could not decode player item at " + location, failure);
    }

    record Result(ItemStack item, List<String> removedComponents) {
    }

    interface ItemNbtCodec {
        ItemStack decode(ReadWriteNBT item);

        ReadWriteNBT copy(ReadWriteNBT item);

        ItemStack validateAndClone(ItemStack decoded, String location) throws IOException;
    }

    private record ParsedKey(String value, int end) {
    }
}
