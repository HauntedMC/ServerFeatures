package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
