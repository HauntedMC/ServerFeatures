package nl.hauntedmc.serverfeatures.api.util.type;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CastUtilsTest {

    @Test
    void safeCastToListReturnsTypedListWhenAllElementsMatch() {
        List<String> result = CastUtils.safeCastToList(List.of("a", "b"), String.class);
        assertEquals(List.of("a", "b"), result);
    }

    @Test
    void safeCastToListReturnsEmptyListWhenInputNotList() {
        assertTrue(CastUtils.safeCastToList("not-a-list", String.class).isEmpty());
    }

    @Test
    void safeCastToListThrowsWhenAnyElementHasWrongType() {
        assertThrows(ClassCastException.class, () -> CastUtils.safeCastToList(List.of("a", 1), String.class));
    }

    @Test
    void safeCastToListReportsNullElementsWithoutSecondaryFailure() {
        List<Object> values = new ArrayList<>();
        values.add("a");
        values.add(null);

        ClassCastException exception = assertThrows(
                ClassCastException.class,
                () -> CastUtils.safeCastToList(values, String.class)
        );

        assertTrue(exception.getMessage().endsWith("found: null"));
    }
}
