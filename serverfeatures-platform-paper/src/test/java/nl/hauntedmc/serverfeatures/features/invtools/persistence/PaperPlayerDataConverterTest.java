package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PaperPlayerDataConverterTest {

    @Test
    void selectsTheCompoundTagOverloadInsteadOfTheDynamicOverload() throws IOException {
        Method selected = PaperPlayerDataConverter.selectCompoundUpdateMethod(
                FakeDataFixTypes.class,
                new FakeDataFixer(),
                FakeCompoundTag.class.getName()
        );

        assertEquals(FakeCompoundTag.class, selected.getParameterTypes()[1]);
        assertEquals(FakeCompoundTag.class, selected.getReturnType());
    }

    @Test
    void rejectsAnOverloadWhoseReturnTypeIsNotTheCompoundTag() {
        assertThrows(
                IOException.class,
                () -> PaperPlayerDataConverter.selectCompoundUpdateMethod(
                        WrongReturnDataFixTypes.class,
                        new FakeDataFixer(),
                        FakeCompoundTag.class.getName()
                )
        );
    }

    @Test
    void rejectsAnOverloadForADifferentDataFixerType() {
        assertThrows(
                IOException.class,
                () -> PaperPlayerDataConverter.selectCompoundUpdateMethod(
                        DifferentFixerDataFixTypes.class,
                        new FakeDataFixer(),
                        FakeCompoundTag.class.getName()
                )
        );
    }

    @Test
    void rejectsNonForwardConversionRangesBeforePaperBridgeInitialization() {
        PaperPlayerDataConverter converter = new PaperPlayerDataConverter();
        ReadWriteNBT root = mock(ReadWriteNBT.class);

        assertThrows(IOException.class, () -> converter.convertToCurrent(root, 0, 4903));
        assertThrows(IOException.class, () -> converter.convertToCurrent(root, 4903, 4903));
        assertThrows(IOException.class, () -> converter.convertToCurrent(root, 5000, 4903));
    }

    @Test
    void rejectsAnNbtRootThatCannotExposeTheRawCompoundTag() {
        PaperPlayerDataConverter converter = new PaperPlayerDataConverter();

        IOException exception = assertThrows(
                IOException.class,
                () -> converter.convertToCurrent(mock(ReadWriteNBT.class), 4440, 4903)
        );

        assertTrue(exception.getMessage().contains("mutable playerdata compound"));
    }

    private static <T> T validateFixtureInvocation(Object fixer, T tag, int sourceVersion) {
        Objects.requireNonNull(fixer, "fixer");
        Objects.requireNonNull(tag, "tag");
        if (sourceVersion < 0) {
            throw new IllegalArgumentException("sourceVersion must not be negative");
        }
        return tag;
    }

    private enum FakeDataFixTypes {
        PLAYER;

        public FakeCompoundTag updateToCurrentVersion(
                FakeDataFixer fixer,
                FakeCompoundTag tag,
                int sourceVersion
        ) {
            return validateFixtureInvocation(fixer, tag, sourceVersion);
        }

        public FakeDynamic updateToCurrentVersion(
                FakeDataFixer fixer,
                FakeDynamic tag,
                int sourceVersion
        ) {
            return validateFixtureInvocation(fixer, tag, sourceVersion);
        }
    }

    private enum WrongReturnDataFixTypes {
        PLAYER;

        public Object updateToCurrentVersion(
                FakeDataFixer fixer,
                FakeCompoundTag tag,
                int sourceVersion
        ) {
            return validateFixtureInvocation(fixer, tag, sourceVersion);
        }
    }

    private enum DifferentFixerDataFixTypes {
        PLAYER;

        public FakeCompoundTag updateToCurrentVersion(
                DifferentDataFixer fixer,
                FakeCompoundTag tag,
                int sourceVersion
        ) {
            return validateFixtureInvocation(fixer, tag, sourceVersion);
        }
    }

    private static final class FakeDataFixer {
    }

    private static final class DifferentDataFixer {
    }

    private static final class FakeCompoundTag {
    }

    private static final class FakeDynamic {
    }
}
