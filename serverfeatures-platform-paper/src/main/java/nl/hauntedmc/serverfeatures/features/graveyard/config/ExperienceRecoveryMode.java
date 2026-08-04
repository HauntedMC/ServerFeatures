package nl.hauntedmc.serverfeatures.features.graveyard.config;

/**
 * Selects how much of the final Minecraft death-event experience is stored in a grave.
 */
public enum ExperienceRecoveryMode {
    /** Store exactly the amount Minecraft and other plugins left in PlayerDeathEvent#getDroppedExp(). */
    NATIVE {
        @Override
        public int capturedExperience(int droppedExperience, int recoveryPercentage) {
            return Math.max(0, droppedExperience);
        }
    },

    /** Store a configured percentage of the final dropped experience. */
    PERCENTAGE {
        @Override
        public int capturedExperience(int droppedExperience, int recoveryPercentage) {
            int dropped = Math.max(0, droppedExperience);
            int percentage = Math.max(0, Math.min(100, recoveryPercentage));
            return (int) (((long) dropped * percentage) / 100L);
        }
    };

    public abstract int capturedExperience(int droppedExperience, int recoveryPercentage);
}
