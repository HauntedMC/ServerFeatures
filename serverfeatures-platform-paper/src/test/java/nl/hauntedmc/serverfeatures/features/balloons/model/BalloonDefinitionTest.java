package nl.hauntedmc.serverfeatures.features.balloons.model;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalloonDefinitionTest {

    @Test
    void itemDefinitionReportsItemAndCustomModelData() {
        BalloonDefinition def = new BalloonDefinition(
                "test",
                "perm",
                Component.text("Balloon"),
                Material.STONE,
                42,
                null
        );

        assertTrue(def.isItem());
        assertFalse(def.isHead());
        assertEquals(Optional.of(42), def.customModelData());
    }

    @Test
    void headDefinitionReportsHeadAndNoCustomModelData() {
        BalloonDefinition def = new BalloonDefinition(
                "head",
                "perm.head",
                Component.text("Head"),
                null,
                null,
                "base64texture"
        );

        assertFalse(def.isItem());
        assertTrue(def.isHead());
        assertEquals(Optional.empty(), def.customModelData());
    }

    @Test
    void blankTextureIsNotTreatedAsHead() {
        BalloonDefinition def = new BalloonDefinition(
                "blank",
                "perm.blank",
                Component.text("Blank"),
                null,
                null,
                "   "
        );

        assertFalse(def.isHead());
    }

    @Test
    void constructorRejectsNullRequiredFields() {
        assertThrows(NullPointerException.class, () -> new BalloonDefinition(null, "perm", Component.text("x"), null, null, null));
        assertThrows(NullPointerException.class, () -> new BalloonDefinition("id", null, Component.text("x"), null, null, null));
        assertThrows(NullPointerException.class, () -> new BalloonDefinition("id", "perm", null, null, null, null));
    }
}
