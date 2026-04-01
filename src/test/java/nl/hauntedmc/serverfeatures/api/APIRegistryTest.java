package nl.hauntedmc.serverfeatures.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class APIRegistryTest {

    @AfterEach
    void cleanup() {
        APIRegistry.clear();
    }

    @Test
    void registerGetUnregisterAndClear() {
        APIRegistry.register(String.class, "value");

        assertEquals("value", APIRegistry.get(String.class).orElseThrow());

        APIRegistry.unregister(String.class);
        assertFalse(APIRegistry.get(String.class).isPresent());

        APIRegistry.register(Integer.class, 7);
        APIRegistry.clear();
        assertFalse(APIRegistry.get(Integer.class).isPresent());
    }

    @Test
    void registerRejectsNullArguments() {
        assertThrows(IllegalArgumentException.class, () -> APIRegistry.register(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> APIRegistry.register(String.class, null));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void getThrowsWhenStoredInstanceCannotBeCastToRequestedType() {
        APIRegistry.register((Class) String.class, 5);
        assertThrows(ClassCastException.class, () -> APIRegistry.get(String.class));
        assertTrue(APIRegistry.get(Integer.class).isEmpty());
    }
}
