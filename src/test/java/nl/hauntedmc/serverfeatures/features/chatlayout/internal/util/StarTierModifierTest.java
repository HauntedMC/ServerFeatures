package nl.hauntedmc.serverfeatures.features.chatlayout.internal.util;

import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StarTierModifierTest {

    @Test
    void getStarTierUsesPermissionPriority() {
        Player bypass = playerWithPerms(Set.of("chatformat.bypass", "chatformat.d500"));
        Player top = playerWithPerms(Set.of("chatformat.d500"));
        Player mid = playerWithPerms(Set.of("chatformat.d250"));
        Player none = playerWithPerms(Set.of());

        assertEquals(0, StarTierModifier.getStarTier(bypass));
        assertEquals(9, StarTierModifier.getStarTier(top));
        assertEquals(4, StarTierModifier.getStarTier(mid));
        assertEquals(0, StarTierModifier.getStarTier(none));
    }

    @Test
    void getStarTierFormatMatchesDefinedSymbols() {
        assertEquals("", StarTierModifier.getStarTierFormat(0));
        assertEquals("<gold>✯ ", StarTierModifier.getStarTierFormat(1));
        assertEquals("<yellow>✯✯✯ ", StarTierModifier.getStarTierFormat(9));
    }

    private static Player playerWithPerms(Set<String> perms) {
        return InterfaceProxy.of(Player.class, Map.of(
                "hasPermission", args -> perms.contains((String) args[0])
        ));
    }
}

