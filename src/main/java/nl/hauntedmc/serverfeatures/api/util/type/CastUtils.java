package nl.hauntedmc.serverfeatures.api.util.type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CastUtils {

    private CastUtils() {
    }

    public static <T> List<T> safeCastToList(Object obj, Class<T> clazz) {
        if (obj instanceof List<?> rawList) {
            List<T> result = new ArrayList<>();
            for (Object item : rawList) {
                if (clazz.isInstance(item)) {
                    result.add(clazz.cast(item));
                } else {
                    throw new ClassCastException("Expected a " + clazz.getName() + ", but found: " + item.getClass());
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}