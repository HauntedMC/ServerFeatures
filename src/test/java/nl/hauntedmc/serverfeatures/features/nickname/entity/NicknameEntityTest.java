package nl.hauntedmc.serverfeatures.features.nickname.entity;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NicknameEntityTest {

    @Test
    void constructorAndSetPlayerKeepPlayerIdInSync() {
        PlayerEntity first = new PlayerEntity();
        first.setId(1L);
        PlayerEntity second = new PlayerEntity();
        second.setId(2L);

        NicknameEntity entity = new NicknameEntity(first, "Nick");
        assertEquals(1L, entity.getPlayerId());
        assertEquals("Nick", entity.getNickname());

        entity.setPlayer(second);
        entity.setNickname("Other");

        assertEquals(2L, entity.getPlayerId());
        assertEquals(second, entity.getPlayer());
        assertEquals("Other", entity.getNickname());
    }
}

