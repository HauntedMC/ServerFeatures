package nl.hauntedmc.serverfeatures.features.graveyard.packet;

import com.github.retrooper.packetevents.util.Vector3f;

/**
 * Defines the local-space composition of one virtual grave.
 *
 * <p>The grave location and yaw are supplied by the packet entity spawn. These offsets therefore
 * rotate as one coherent model: the bed runs along the local Z axis, while the crossbar runs along
 * local X. Keeping the complete composition in one layout prevents visual and interaction bounds
 * from drifting apart when the design changes.</p>
 */
final class GraveVisualLayout {
    static final Part BASE = new Part(
            new Vector3f(-0.55f, 0.0f, -1.10f),
            new Vector3f(1.10f, 0.55f, 2.20f)
    );
    static final Part HEADSTONE_STEM = new Part(
            new Vector3f(-0.16f, 0.18f, 0.72f),
            new Vector3f(0.32f, 1.70f, 0.28f)
    );
    static final Part HEADSTONE_CROSSBAR = new Part(
            new Vector3f(-0.58f, 1.24f, 0.72f),
            new Vector3f(1.16f, 0.30f, 0.28f)
    );

    static final float TEXT_OFFSET_Y = 2.18f;
    static final float INTERACTION_WIDTH = 2.35f;
    static final float INTERACTION_HEIGHT = 2.20f;

    private GraveVisualLayout() {
    }

    record Part(Vector3f translation, Vector3f scale) {
        Part {
            if (translation == null || scale == null) {
                throw new IllegalArgumentException("Grave visual vectors must not be null");
            }
            if (!positiveFinite(scale.getX())
                    || !positiveFinite(scale.getY())
                    || !positiveFinite(scale.getZ())) {
                throw new IllegalArgumentException("Grave visual scale must be positive and finite");
            }
        }

        float maximumX() {
            return translation.getX() + scale.getX();
        }

        float maximumY() {
            return translation.getY() + scale.getY();
        }

        float maximumZ() {
            return translation.getZ() + scale.getZ();
        }

        private static boolean positiveFinite(float value) {
            return Float.isFinite(value) && value > 0.0f;
        }
    }
}
