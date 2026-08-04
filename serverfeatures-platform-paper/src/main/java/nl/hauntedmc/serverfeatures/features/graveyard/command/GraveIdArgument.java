package nl.hauntedmc.serverfeatures.features.graveyard.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import java.util.Collection;
import java.util.List;

/**
 * Parses one non-whitespace grave identifier while allowing timestamp separators such as colons.
 */
final class GraveIdArgument implements ArgumentType<String> {
    private static final SimpleCommandExceptionType EXPECTED_IDENTIFIER =
            new SimpleCommandExceptionType(() -> "Expected grave identifier");

    private GraveIdArgument() {
    }

    static GraveIdArgument graveId() {
        return new GraveIdArgument();
    }

    static <S> String get(CommandContext<S> context, String name) {
        return context.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
            reader.skip();
        }
        if (reader.getCursor() == start) {
            throw EXPECTED_IDENTIFIER.createWithContext(reader);
        }
        return reader.getString().substring(start, reader.getCursor());
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("RemyMine-10:27:15");
    }
}
