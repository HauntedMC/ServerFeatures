package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.meta;

import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetaTest {

    @Test
    void declaresPacketAndWorldEditDependencies() {
        Meta meta = new Meta();

        assertEquals(
                List.of(BaseMeta.PACKET_EVENTS, "FastAsyncWorldEdit"),
                meta.getPluginDependencies()
        );
    }
}
