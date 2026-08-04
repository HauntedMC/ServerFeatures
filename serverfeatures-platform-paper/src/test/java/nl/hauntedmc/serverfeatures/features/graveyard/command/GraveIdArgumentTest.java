package nl.hauntedmc.serverfeatures.features.graveyard.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GraveIdArgumentTest {
    @Test
    void acceptsColonSeparatedTimeWithoutQuotes() throws CommandSyntaxException {
        StringReader reader = new StringReader("RemyMine-10:27:15 confirm");

        assertEquals("RemyMine-10:27:15", GraveIdArgument.graveId().parse(reader));
        assertEquals(' ', reader.peek());
    }

    @Test
    void rejectsMissingIdentifier() {
        assertThrows(
                CommandSyntaxException.class,
                () -> GraveIdArgument.graveId().parse(new StringReader(""))
        );
    }

    @Test
    void exposesNativeArgumentForClientSynchronization() {
        assertInstanceOf(StringArgumentType.class, GraveIdArgument.graveId().getNativeType());
    }
}
