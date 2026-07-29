package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;

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

    private enum FakeDataFixTypes {
        PLAYER;

        public FakeCompoundTag updateToCurrentVersion(
                FakeDataFixer fixer,
                FakeCompoundTag tag,
                int sourceVersion
        ) {
            return tag;
        }

        public FakeDynamic updateToCurrentVersion(
                FakeDataFixer fixer,
                FakeDynamic tag,
                int sourceVersion
        ) {
            return tag;
        }
    }

    private enum WrongReturnDataFixTypes {
        PLAYER;

        public Object updateToCurrentVersion(
                FakeDataFixer fixer,
                FakeCompoundTag tag,
                int sourceVersion
        ) {
            return tag;
        }
    }

    private enum DifferentFixerDataFixTypes {
        PLAYER;

        public FakeCompoundTag updateToCurrentVersion(
                DifferentDataFixer fixer,
                FakeCompoundTag tag,
                int sourceVersion
        ) {
            return tag;
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
