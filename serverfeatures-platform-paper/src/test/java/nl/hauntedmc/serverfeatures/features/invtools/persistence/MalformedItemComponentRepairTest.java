package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import de.tr7zw.changeme.nbtapi.NBTType;
import de.tr7zw.changeme.nbtapi.NbtApiException;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MalformedItemComponentRepairTest {

    @Test
    void removesTheSingleComponentNamedByTheCodecAndPreservesTheRest() throws IOException {
        ReadWriteNBT original = mock(ReadWriteNBT.class);
        ReadWriteNBT candidate = mock(ReadWriteNBT.class);
        ReadWriteNBT components = components(candidate, "minecraft:custom_name",
                "minecraft:damage_resistant");
        NbtApiException codecFailure = wrappedMissingInput(
                missingInput(
                        "{\"minecraft:damage_resistant\":{types:\"#minecraft:cactus\"}}",
                        "{components:{\"minecraft:custom_name\":{text:\"Weather God\"},"
                                + "\"minecraft:damage_resistant\":"
                                + "{types:\"#minecraft:cactus\"}}}"
                )
        );
        ItemStack decoded = decodedTrident();
        ItemStack cloned = mock(ItemStack.class);
        MalformedItemComponentRepair.ItemNbtCodec codec = codec(original, candidate);
        when(codec.decode(original)).thenThrow(codecFailure);
        when(codec.decode(candidate)).thenReturn(decoded);
        when(codec.validateAndClone(decoded, "EnderItems slot 1")).thenReturn(cloned);

        MalformedItemComponentRepair.Result result = MalformedItemComponentRepair.decode(
                original,
                "EnderItems slot 1",
                true,
                codec
        );

        assertSame(cloned, result.item());
        assertEquals(List.of("minecraft:damage_resistant"), result.removedComponents());
        verify(components).removeKey("minecraft:damage_resistant");
        verify(original).clearNBT();
        verify(original).mergeCompound(candidate);
    }

    @Test
    void removesMultipleComponentsReportedByConsecutiveCodecFailures() throws IOException {
        ReadWriteNBT original = mock(ReadWriteNBT.class);
        ReadWriteNBT candidate = mock(ReadWriteNBT.class);
        ReadWriteNBT components = components(candidate, "custom:first", "custom:second");
        NbtApiException firstFailure = missingInput("{\"custom:first\":{bad:1b}}", "{}");
        NbtApiException secondFailure = missingInput("{\"custom:second\":{bad:1b}}", "{}");
        ItemStack decoded = decodedTrident();
        MalformedItemComponentRepair.ItemNbtCodec codec = codec(original, candidate);
        when(codec.decode(original)).thenThrow(firstFailure);
        when(codec.decode(candidate)).thenThrow(secondFailure).thenReturn(decoded);
        when(codec.validateAndClone(decoded, "Inventory slot 4")).thenReturn(decoded);

        MalformedItemComponentRepair.Result result = MalformedItemComponentRepair.decode(
                original,
                "Inventory slot 4",
                true,
                codec
        );

        assertEquals(List.of("custom:first", "custom:second"), result.removedComponents());
        verify(components).removeKey("custom:first");
        verify(components).removeKey("custom:second");
        verify(original).clearNBT();
    }

    @Test
    void refusesToGuessWhenMissedInputNamesMultipleComponents() {
        ReadWriteNBT item = mock(ReadWriteNBT.class);
        components(item, "custom:first", "custom:second");
        NbtApiException failure = missingInput(
                "{\"custom:first\":{bad:1b},\"custom:second\":{bad:1b}}",
                "{}"
        );

        assertEquals(
                java.util.Optional.empty(),
                MalformedItemComponentRepair.rejectedComponentKey(item, failure)
        );
    }

    @Test
    void ignoresComponentLikeTextNestedInsideAnotherMissedInputValue() {
        ReadWriteNBT item = mock(ReadWriteNBT.class);
        components(item, "custom:first");
        NbtApiException failure = missingInput(
                "{unknown:{text:'embedded \\\"custom:first\\\": value'}}",
                "{}"
        );

        assertEquals(
                java.util.Optional.empty(),
                MalformedItemComponentRepair.rejectedComponentKey(item, failure)
        );
    }

    @Test
    void ignoresAUserControlledDiagnosticDecoyInTheOuterItemDump() {
        ReadWriteNBT item = mock(ReadWriteNBT.class);
        components(item, "custom:decoy", "custom:actual");
        NbtApiException actualFailure = missingInput(
                "{\"custom:actual\":{bad:1b}}",
                "{}"
        );
        NbtApiException wrappedFailure = new NbtApiException(
                "Exception while converting NBTCompound to NMS ItemStack! "
                        + "{lore:'Failed to convert NBT to ItemStack. DataResult.Error["
                        + "missed input: {\"custom:decoy\":{bad:1b}}]'}",
                actualFailure
        );

        assertEquals(
                java.util.Optional.of("custom:actual"),
                MalformedItemComponentRepair.rejectedComponentKey(item, wrappedFailure)
        );
    }

    @Test
    void leavesOriginalNbtUntouchedWhenTheFailureCannotBeMapped() {
        ReadWriteNBT original = mock(ReadWriteNBT.class);
        ReadWriteNBT candidate = mock(ReadWriteNBT.class);
        components(candidate, "minecraft:custom_name");
        NbtApiException codecFailure = new NbtApiException("Unmapped codec failure");
        MalformedItemComponentRepair.ItemNbtCodec codec = codec(original, candidate);
        when(codec.decode(original)).thenThrow(codecFailure);

        assertThrows(
                IOException.class,
                () -> MalformedItemComponentRepair.decode(
                        original,
                        "EnderItems slot 1",
                        true,
                        codec
                )
        );

        verify(original, never()).clearNBT();
        verify(original, never()).mergeCompound(candidate);
    }

    private static ReadWriteNBT components(ReadWriteNBT item, String... keys) {
        ReadWriteNBT components = mock(ReadWriteNBT.class);
        Set<String> remaining = new LinkedHashSet<>(List.of(keys));
        when(item.hasTag("components", NBTType.NBTTagCompound)).thenReturn(true);
        when(item.getCompound("components")).thenReturn(components);
        when(components.getKeys()).thenAnswer(ignored -> Set.copyOf(remaining));
        when(components.isEmpty()).thenAnswer(ignored -> remaining.isEmpty());
        doAnswer(invocation -> {
            remaining.remove(invocation.getArgument(0, String.class));
            return null;
        }).when(components).removeKey(org.mockito.ArgumentMatchers.anyString());
        return components;
    }

    private static ItemStack decodedTrident() {
        return mock(ItemStack.class);
    }

    private static MalformedItemComponentRepair.ItemNbtCodec codec(
            ReadWriteNBT original,
            ReadWriteNBT candidate
    ) {
        MalformedItemComponentRepair.ItemNbtCodec codec = mock(
                MalformedItemComponentRepair.ItemNbtCodec.class
        );
        when(codec.copy(original)).thenReturn(candidate);
        return codec;
    }

    private static NbtApiException missingInput(String missedInput, String completeItem) {
        return new NbtApiException(
                "Failed to convert NBT to ItemStack. DataResult.Error['Missing registry value'"
                        + " missed input: " + missedInput + "': 1 minecraft:trident] "
                        + completeItem
        );
    }

    private static NbtApiException wrappedMissingInput(NbtApiException cause) {
        return new NbtApiException(
                "Exception while converting NBTCompound to NMS ItemStack! {item}",
                cause
        );
    }
}
