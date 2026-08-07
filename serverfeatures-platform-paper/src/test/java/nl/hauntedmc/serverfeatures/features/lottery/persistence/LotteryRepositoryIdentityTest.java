package nl.hauntedmc.serverfeatures.features.lottery.persistence;

import nl.hauntedmc.serverfeatures.features.lottery.entity.LotteryPlayerStatsEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LotteryRepositoryIdentityTest {

    @Test
    void createsPlayerStatsWithRealIdentityBeforePersistence() {
        UUID playerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        LotteryPlayerStatsEntity stats = LotteryRepository.newPlayerStats(
                "survival",
                playerUuid,
                42L,
                "remymine"
        );

        assertEquals("survival:" + playerUuid, stats.getId());
        assertEquals("survival", stats.getLotteryKey());
        assertEquals(42L, stats.getPlayerId());
        assertEquals(playerUuid.toString(), stats.getPlayerUuid());
        assertEquals("remymine", stats.getPlayerName());
        assertEquals(BigDecimal.ZERO.setScale(2), stats.getTotalSpent());
        assertEquals(BigDecimal.ZERO.setScale(2), stats.getTotalDonated());
        assertEquals(BigDecimal.ZERO.setScale(2), stats.getTotalWon());
        assertEquals(0L, stats.getTicketsBought());
        assertEquals(0L, stats.getDonationCount());
        assertEquals(0L, stats.getRoundsWon());
    }

    @Test
    void normalizesPlayerNameBeforeQueuedInsert() {
        UUID playerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        String longName = "x".repeat(40);

        LotteryPlayerStatsEntity stats = LotteryRepository.newPlayerStats(
                "survival",
                playerUuid,
                null,
                longName
        );

        assertEquals("x".repeat(32), stats.getPlayerName());
    }
}
