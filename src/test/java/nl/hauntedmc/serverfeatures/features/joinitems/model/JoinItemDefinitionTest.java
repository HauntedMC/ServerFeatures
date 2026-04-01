package nl.hauntedmc.serverfeatures.features.joinitems.model;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinItemDefinitionTest {

    @Test
    void constructorNormalizesIdAndDefensivelyCopiesLists() {
        List<Component> lore = new ArrayList<>(List.of(Component.text("L1")));
        List<String> commands = new ArrayList<>(List.of("cmd"));

        JoinItemDefinition def = new JoinItemDefinition(
                "WelCome",
                Material.STONE,
                3,
                Component.text("Name"),
                lore,
                commands,
                true,
                false,
                true
        );

        lore.add(Component.text("L2"));
        commands.add("other");

        assertEquals("welcome", def.id());
        assertEquals(1, def.lore().size());
        assertEquals(1, def.commands().size());
        assertTrue(def.locked());
        assertThrows(UnsupportedOperationException.class, () -> def.commands().add("x"));
    }

    @Test
    void toComponentAndToComponentsHandleBlankAndNullInputs() {
        assertEquals(Component.empty(), JoinItemDefinition.toComponent(" "));
        assertEquals(List.of(), JoinItemDefinition.toComponents(null));
        assertEquals(2, JoinItemDefinition.toComponents(List.of("&aOne", "&bTwo")).size());
    }
}

