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
    static final Part BASE = new Part(-0.55f, 0.0f, -1.10f, 1.10f, 0.55f, 2.20f);
    static final Part HEADSTONE_STEM = new Part(-0.16f, 0.18f, 0.72f, 0.32f, 1.70f, 0.28f);
    static final Part HEADSTONE_CROSSBAR = new Part(-0.58f, 1.24f, 0.72f, 1.16f, 0.30f, 0.28f);

    static final float TEXT_OFFSET_Y = 2.18f;
    static final float INTERACTION_WIDTH = 2.35f;
    static final float INTERACTION_HEIGHT = 2.20f;

    private GraveVisualLayout() {
    }

    record Part(float x, float y, float z, float width, float height, float depth) {
        Part {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                throw new IllegalArgumentException("Grave visual translation must be finite");
            }
            if (!positiveFinite(width) || !positiveFinite(height) || !positiveFinite(depth)) {
                throw new IllegalArgumentException("Grave visual dimensions must be positive and finite");
            }
        }

        Vector3f translation() {
            return new Vector3f(x, y, z);
        }

        Vector3f scale() {
            return new Vector3f(width, height, depth);
        }

        float maximumX() {
            return x + width;
        }

        float maximumY() {
            return y + height;
        }

        float maximumZ() {
            return z + depth;
        }

        private static boolean positiveFinite(float value) {
            return Float.isFinite(value) && value > 0.0f;
        }
    }
}
