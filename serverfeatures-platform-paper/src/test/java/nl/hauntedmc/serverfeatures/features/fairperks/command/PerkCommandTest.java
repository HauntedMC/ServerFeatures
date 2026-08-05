package nl.hauntedmc.serverfeatures.features.fairperks.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import nl.hauntedmc.serverfeatures.features.fairperks.FairPerks;
import nl.hauntedmc.serverfeatures.features.fairperks.config.FairPerksSettings;
import nl.hauntedmc.serverfeatures.features.fairperks.model.PerkType;
import nl.hauntedmc.serverfeatures.features.fairperks.service.PerkStateService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PerkCommandTest {

    @Test
    void activePlayerCanAccessCommandToDisableAfterPermissionLoss() {
        Fixture fixture = fixture();
        when(fixture.player().hasPermission(FairPerks.FLY_USE_PERMISSION)).thenReturn(false);
        when(fixture.player().hasPermission(FairPerks.FLY_OTHERS_PERMISSION)).thenReturn(false);
        when(fixture.stateService().isDesired(fixture.player(), PerkType.FLY)).thenReturn(true);

        assertTrue(fixture.node().getRequirement().test(fixture.source()));
    }

    @Test
    void inactivePlayerWithoutPermissionCannotAccessCommand() {
        Fixture fixture = fixture();
        when(fixture.player().hasPermission(FairPerks.FLY_USE_PERMISSION)).thenReturn(false);
        when(fixture.player().hasPermission(FairPerks.FLY_OTHERS_PERMISSION)).thenReturn(false);
        when(fixture.stateService().isDesired(fixture.player(), PerkType.FLY)).thenReturn(false);

        assertFalse(fixture.node().getRequirement().test(fixture.source()));
    }

    private static Fixture fixture() {
        FairPerks feature = mock(FairPerks.class);
        FairPerksSettings settings = mock(FairPerksSettings.class);
        FairPerksSettings.CommandSettings commands = new FairPerksSettings.CommandSettings(
                List.of(),
                List.of(),
                List.of()
        );
        PerkStateService stateService = mock(PerkStateService.class);
        Player player = mock(Player.class);
        CommandSourceStack source = mock(CommandSourceStack.class);

        when(feature.settings()).thenReturn(settings);
        when(settings.commands()).thenReturn(commands);
        when(feature.stateService()).thenReturn(stateService);
        when(source.getSender()).thenReturn(player);

        LiteralCommandNode<CommandSourceStack> node = new PerkCommand(feature, PerkType.FLY).buildTree();
        return new Fixture(player, source, stateService, node);
    }

    private record Fixture(
            Player player,
            CommandSourceStack source,
            PerkStateService stateService,
            LiteralCommandNode<CommandSourceStack> node
    ) {
    }
}
