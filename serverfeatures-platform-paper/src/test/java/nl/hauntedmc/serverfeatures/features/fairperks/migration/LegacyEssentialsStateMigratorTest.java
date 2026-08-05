package nl.hauntedmc.serverfeatures.features.fairperks.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyEssentialsStateMigratorTest {

    @Test
    void capturesAndClearsLegacyFlyAndGodFlags() throws ReflectiveOperationException {
        FakeUser user = new FakeUser(true, true);

        LegacyEssentialsStateMigrator.MigrationResult result =
                LegacyEssentialsStateMigrator.migrateUser(user);

        assertTrue(result.completed());
        assertTrue(result.flyEnabled());
        assertTrue(result.godEnabled());
        assertFalse(user.isFlyModeEnabled());
        assertFalse(user.isGodModeEnabled());
    }

    @Test
    void reportsDisabledLegacyFlagsWithoutMutatingThem() throws ReflectiveOperationException {
        FakeUser user = new FakeUser(false, false);

        LegacyEssentialsStateMigrator.MigrationResult result =
                LegacyEssentialsStateMigrator.migrateUser(user);

        assertTrue(result.completed());
        assertFalse(result.flyEnabled());
        assertFalse(result.godEnabled());
        assertFalse(user.isFlyModeEnabled());
        assertFalse(user.isGodModeEnabled());
    }

    public static final class FakeUser {
        private boolean flyModeEnabled;
        private boolean godModeEnabled;

        private FakeUser(boolean flyModeEnabled, boolean godModeEnabled) {
            this.flyModeEnabled = flyModeEnabled;
            this.godModeEnabled = godModeEnabled;
        }

        public boolean isFlyModeEnabled() {
            return flyModeEnabled;
        }

        public boolean isGodModeEnabled() {
            return godModeEnabled;
        }

        public void setFlyModeEnabled(boolean enabled) {
            flyModeEnabled = enabled;
        }

        public void setGodModeEnabled(boolean enabled) {
            godModeEnabled = enabled;
        }
    }
}
