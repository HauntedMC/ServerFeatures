package nl.hauntedmc.serverfeatures.features.nametags.internal;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataType;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3f;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.features.nametags.internal.properties.BillboardConstraints;
import nl.hauntedmc.serverfeatures.features.nametags.internal.properties.EntityFlag;
import nl.hauntedmc.serverfeatures.features.nametags.internal.properties.TextDisplayAlignment;
import nl.hauntedmc.serverfeatures.features.nametags.internal.properties.TextDisplayFlag;
import org.bukkit.entity.Pose;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * LayoutManager encapsulates the metadata for a nametag and exposes a full
 * API for every network protocol field. Each field is accessible via a
 * dedicated getter and setter so that you can write code such as:
 *     properties.setIsSeeThrough(true);
 *     properties.setTextDisplayAlignment(TextDisplayAlignment.CENTER);
 * Internally the LayoutManager takes care of adding, updating, or removing
 * the corresponding EntityData entries.
 */
public class NametagProperties {

    private final List<EntityData<?>> metadata;

    public NametagProperties() {
        metadata = new ArrayList<>();

        // --- Entity Defaults (indices 0-7) ---
        setAirTicks(300);                                // Index 1, default 300
        setCustomName(Component.empty());                // Index 2, default empty
        setCustomNameVisible(false);                     // Index 3, default false
        setSilent(false);                                // Index 4, default false
        setHasNoGravity(false);                          // Index 5, default false
        setPose(EntityPose.STANDING);                    // Index 6, default STANDING (assume Pose enum)
        setFrozenTicks(0);                               // Index 7, default 0

        // --- Display Defaults (indices 8-22) ---
        setInterpolationDelay(0);                        // Index 8
        setTransformationInterpolationDuration(0);       // Index 9
        setPosRotInterpolationDuration(0);               // Index 10
        setTranslation(new Vector3f(0.0f, 0.0f, 0.0f));// Index 11
        setScale(new Vector3f(1.0f, 1.0f, 1.0f)); // Index 12
        setRotationLeft(new Quaternion4f(0.0f, 0.0f, 0.0f, 1.0f));// Index 13
        setRotationRight(new Quaternion4f(0.0f, 0.0f, 0.0f, 1.0f));// Index 14
        setBillboardConstraints(BillboardConstraints.FIXED); // Index 15
        setBrightnessOverride(-1);                       // Index 16
        setViewRange(1.0f);                              // Index 17
        setShadowRadius(0.0f);                           // Index 18
        setShadowStrength(1.0f);                         // Index 19
        setWidth(0.0f);                                  // Index 20
        setHeight(0.0f);                                 // Index 21
        setGlowColorOverride(-1);                        // Index 22

        // --- Text Display Defaults (indices 23-27) ---
        setText(Component.empty());                      // Index 23, default empty text
        setLineWidth(200);                               // Index 24, default 200
        setBackgroundColor(0x40000000);                  // Index 25, default 1073741824 (0x40000000)
        setTextOpacity((byte) -1);                       // Index 26, default fully opaque (-1)
        setHasShadow(true);
    }

    // --- Internal helper methods for metadata management ---

    private Optional<EntityData<?>> getEntityData(int index) {
        return metadata.stream()
                .filter(data -> data.getIndex() == index)
                .findFirst();
    }

    private void setEntityData(int index, Object value, EntityDataType<?> type) {
        removeEntityData(index);
        metadata.add(new EntityData(index, type, value));
    }

    private void removeEntityData(int index) {
        metadata.removeIf(data -> data.getIndex() == index);
    }

    public List<EntityData<?>> getMetadata() {
        return metadata;
    }

    // ================================
    // Entity Fields (Indices 0 - 7)
    // ================================

    // Index 0: Bit mask (Entity Flags)
    private void setEntityFlags(byte flags) {
        setEntityData(0, flags, EntityDataTypes.BYTE);
    }

    private byte getEntityFlags() {
        return getEntityData(0)
                .map(data -> (Byte) data.getValue())
                .orElse((byte) 0);
    }

    // ON_FIRE flag
    public void setIsOnFire(boolean onFire) {
        byte flags = getEntityFlags();
        if (onFire) {
            flags |= EntityFlag.ON_FIRE.getBit();
        } else {
            flags &= (byte) ~EntityFlag.ON_FIRE.getBit();
        }
        setEntityFlags(flags);
    }

    public boolean isOnFire() {
        return (getEntityFlags() & EntityFlag.ON_FIRE.getBit()) != 0;
    }

    // PRESSING_SNEAK flag
    public void setIsPressingSneak(boolean pressingSneak) {
        byte flags = getEntityFlags();
        if (pressingSneak) {
            flags |= EntityFlag.PRESSING_SNEAK.getBit();
        } else {
            flags &= (byte) ~EntityFlag.PRESSING_SNEAK.getBit();
        }
        setEntityFlags(flags);
    }

    public boolean isPressingSneak() {
        return (getEntityFlags() & EntityFlag.PRESSING_SNEAK.getBit()) != 0;
    }

    // SPRINTING flag
    public void setIsSprinting(boolean sprinting) {
        byte flags = getEntityFlags();
        if (sprinting) {
            flags |= EntityFlag.SPRINTING.getBit();
        } else {
            flags &= (byte) ~EntityFlag.SPRINTING.getBit();
        }
        setEntityFlags(flags);
    }

    public boolean isSprinting() {
        return (getEntityFlags() & EntityFlag.SPRINTING.getBit()) != 0;
    }

    // SWIMMING flag
    public void setIsSwimming(boolean swimming) {
        byte flags = getEntityFlags();
        if (swimming) {
            flags |= EntityFlag.SWIMMING.getBit();
        } else {
            flags &= (byte) ~EntityFlag.SWIMMING.getBit();
        }
        setEntityFlags(flags);
    }

    public boolean isSwimming() {
        return (getEntityFlags() & EntityFlag.SWIMMING.getBit()) != 0;
    }

    // INVISIBLE flag
    public void setIsInvisible(boolean invisible) {
        byte flags = getEntityFlags();
        if (invisible) {
            flags |= EntityFlag.INVISIBLE.getBit();
        } else {
            flags &= (byte) ~EntityFlag.INVISIBLE.getBit();
        }
        setEntityFlags(flags);
    }

    public boolean isInvisible() {
        return (getEntityFlags() & EntityFlag.INVISIBLE.getBit()) != 0;
    }

    // GLOWING flag
    public void setIsGlowing(boolean glowing) {
        byte flags = getEntityFlags();
        if (glowing) {
            flags |= EntityFlag.GLOWING.getBit();
        } else {
            flags &= (byte) ~EntityFlag.GLOWING.getBit();
        }
        setEntityFlags(flags);
    }

    public boolean isGlowing() {
        return (getEntityFlags() & EntityFlag.GLOWING.getBit()) != 0;
    }

    // FLYING flag
    public void setIsFlying(boolean flying) {
        byte flags = getEntityFlags();
        if (flying) {
            flags |= EntityFlag.FLYING.getBit();
        } else {
            flags &= (byte) ~EntityFlag.FLYING.getBit();
        }
        setEntityFlags(flags);
    }

    public boolean isFlying() {
        return (getEntityFlags() & EntityFlag.FLYING.getBit()) != 0;
    }

    // Index 1: Air ticks (VarInt)
    public void setAirTicks(int ticks) {
        setEntityData(1, ticks, EntityDataTypes.INT);
    }

    public int getAirTicks() {
        return getEntityData(1)
                .map(data -> (Integer) data.getValue())
                .orElse(300);
    }

    // Index 2: Custom name (Optional Text Component)
    public void setCustomName(Component name) {
        setEntityData(2, Optional.of(name), EntityDataTypes.OPTIONAL_ADV_COMPONENT);
    }

    public Component getCustomName() {
        return getEntityData(2)
                .map(data -> (Component) data.getValue())
                .orElse(Component.empty());
    }

    // Index 3: Is custom name visible (Boolean)
    public void setCustomNameVisible(boolean visible) {
        setEntityData(3, visible, EntityDataTypes.BOOLEAN);
    }

    public boolean isCustomNameVisible() {
        return getEntityData(3)
                .map(data -> (Boolean) data.getValue())
                .orElse(false);
    }

    // Index 4: Is silent (Boolean)
    public void setSilent(boolean silent) {
        setEntityData(4, silent, EntityDataTypes.BOOLEAN);
    }

    public boolean isSilent() {
        return getEntityData(4)
                .map(data -> (Boolean) data.getValue())
                .orElse(false);
    }

    // Index 5: Has no gravity (Boolean)
    public void setHasNoGravity(boolean noGravity) {
        setEntityData(5, noGravity, EntityDataTypes.BOOLEAN);
    }

    public boolean hasNoGravity() {
        return getEntityData(5)
                .map(data -> (Boolean) data.getValue())
                .orElse(false);
    }

    // Index 6: Pose (Assume Pose is an enum)
    public void setPose(EntityPose pose) {
        setEntityData(6, pose, EntityDataTypes.ENTITY_POSE); // Serialized as an int
    }

    public Pose getPose() {
        return getEntityData(6)
                .map(data -> (Pose) data.getValue())
                .orElse(Pose.STANDING);
    }

    // Index 7: Ticks frozen in powdered snow (VarInt)
    public void setFrozenTicks(int ticks) {
        setEntityData(7, ticks, EntityDataTypes.INT);
    }

    public int getFrozenTicks() {
        return getEntityData(7)
                .map(data -> (Integer) data.getValue())
                .orElse(0);
    }

    // ================================
    // Display Fields (Indices 8 - 22)
    // ================================

    // Index 8: Interpolation delay (VarInt)
    public void setInterpolationDelay(int delay) {
        setEntityData(8, delay, EntityDataTypes.INT);
    }

    public int getInterpolationDelay() {
        return getEntityData(8)
                .map(data -> (Integer) data.getValue())
                .orElse(0);
    }

    // Index 9: Transformation interpolation duration (VarInt)
    public void setTransformationInterpolationDuration(int duration) {
        setEntityData(9, duration, EntityDataTypes.INT);
    }

    public int getTransformationInterpolationDuration() {
        return getEntityData(9)
                .map(data -> (Integer) data.getValue())
                .orElse(0);
    }

    // Index 10: Position/Rotation interpolation duration (VarInt)
    public void setPosRotInterpolationDuration(int duration) {
        setEntityData(10, duration, EntityDataTypes.INT);
    }

    public int getPosRotInterpolationDuration() {
        return getEntityData(10)
                .map(data -> (Integer) data.getValue())
                .orElse(0);
    }

    // Index 11: Translation (Vector3)
    public void setTranslation(Vector3f translation) {
        setEntityData(11, translation, EntityDataTypes.VECTOR3F);
    }

    public Vector3f getTranslation() {
        return getEntityData(11)
                .map(data -> (Vector3f) data.getValue())
                .orElse(new Vector3f(0.0f, 0.0f, 0.0f));
    }

    // Index 12: Scale (Vector3)
    public void setScale(Vector3f scale) {
        setEntityData(12, scale, EntityDataTypes.VECTOR3F);
    }

    public Vector3f getScale() {
        return getEntityData(12)
                .map(data -> (Vector3f) data.getValue())
                .orElse(new Vector3f(1.0f, 1.0f, 1.0f));
    }

    // Index 13: Rotation left (Quaternion)
    public void setRotationLeft(Quaternion4f rotation) {
        setEntityData(13, rotation, EntityDataTypes.QUATERNION);
    }

    public Quaternion4f getRotationLeft() {
        return getEntityData(13)
                .map(data -> (Quaternion4f) data.getValue())
                .orElse(new Quaternion4f(0.0f, 0.0f, 0.0f, 1.0f));
    }

    // Index 14: Rotation right (Quaternion)
    public void setRotationRight(Quaternion4f rotation) {
        setEntityData(14, rotation, EntityDataTypes.QUATERNION);
    }

    public Quaternion4f getRotationRight() {
        return getEntityData(14)
                .map(data -> (Quaternion4f) data.getValue())
                .orElse(new Quaternion4f(0.0f, 0.0f, 0.0f, 1.0f));
    }

    // Index 15: Billboard Constraints (Byte)
    public void setBillboardConstraints(BillboardConstraints constraints) {
        setEntityData(15, constraints.getValue(), EntityDataTypes.BYTE);
    }

    public BillboardConstraints getBillboardConstraints() {
        byte value = getEntityData(15)
                .map(data -> (Byte) data.getValue())
                .orElse((byte) 0);
        return BillboardConstraints.fromValue(value);
    }

    // Index 16: Brightness override (VarInt)
    public void setBrightnessOverride(int override) {
        setEntityData(16, override, EntityDataTypes.INT);
    }

    public int getBrightnessOverride() {
        return getEntityData(16)
                .map(data -> (Integer) data.getValue())
                .orElse(-1);
    }

    // Index 17: View range (Float)
    public void setViewRange(float viewRange) {
        setEntityData(17, viewRange, EntityDataTypes.FLOAT);
    }

    public float getViewRange() {
        return getEntityData(17)
                .map(data -> (Float) data.getValue())
                .orElse(1.0f);
    }

    // Index 18: Shadow radius (Float)
    public void setShadowRadius(float radius) {
        setEntityData(18, radius, EntityDataTypes.FLOAT);
    }

    public float getShadowRadius() {
        return getEntityData(18)
                .map(data -> (Float) data.getValue())
                .orElse(0.0f);
    }

    // Index 19: Shadow strength (Float)
    public void setShadowStrength(float strength) {
        setEntityData(19, strength, EntityDataTypes.FLOAT);
    }

    public float getShadowStrength() {
        return getEntityData(19)
                .map(data -> (Float) data.getValue())
                .orElse(1.0f);
    }

    // Index 20: Width (Float)
    public void setWidth(float width) {
        setEntityData(20, width, EntityDataTypes.FLOAT);
    }

    public float getWidth() {
        return getEntityData(20)
                .map(data -> (Float) data.getValue())
                .orElse(0.0f);
    }

    // Index 21: Height (Float)
    public void setHeight(float height) {
        setEntityData(21, height, EntityDataTypes.FLOAT);
    }

    public float getHeight() {
        return getEntityData(21)
                .map(data -> (Float) data.getValue())
                .orElse(0.0f);
    }

    // Index 22: Glow color override (VarInt)
    public void setGlowColorOverride(int color) {
        setEntityData(22, color, EntityDataTypes.INT);
    }

    public int getGlowColorOverride() {
        return getEntityData(22)
                .map(data -> (Integer) data.getValue())
                .orElse(-1);
    }

    // ================================
    // Text Display Fields (Indices 23 - 27)
    // ================================

    // Index 23: Text (Text Component)
    public void setText(Component text) {
        setEntityData(23, text, EntityDataTypes.ADV_COMPONENT);
    }

    public Component getText() {
        return getEntityData(23)
                .map(data -> (Component) data.getValue())
                .orElse(Component.empty());
    }

    // Index 24: Line width (VarInt)
    public void setLineWidth(int width) {
        setEntityData(24, width, EntityDataTypes.INT);
    }

    public int getLineWidth() {
        return getEntityData(24)
                .map(data -> (Integer) data.getValue())
                .orElse(200);
    }

    // Index 25: Background color (VarInt)
    public void setBackgroundColor(int color) {
        setEntityData(25, color, EntityDataTypes.INT);
    }

    public int getBackgroundColor() {
        return getEntityData(25)
                .map(data -> (Integer) data.getValue())
                .orElse(0x40000000);
    }

    // Index 26: Text opacity (Byte)
    public void setTextOpacity(byte opacity) {
        setEntityData(26, opacity, EntityDataTypes.BYTE);
    }

    public byte getTextOpacity() {
        return getEntityData(26)
                .map(data -> (Byte) data.getValue())
                .orElse((byte) -1);
    }

    // Index 27: Text display bit mask
    // (has shadow, is see through, use default bg, and alignment)
    private void setTextDisplayFlags(byte flags) {
        setEntityData(27, flags, EntityDataTypes.BYTE);
    }

    private byte getTextDisplayFlags() {
        return getEntityData(27)
                .map(data -> (Byte) data.getValue())
                .orElse((byte) 0);
    }

    public void setHasShadow(boolean hasShadow) {
        byte flags = getTextDisplayFlags();
        if (hasShadow) {
            flags |= TextDisplayFlag.HAS_SHADOW.getBit();
        } else {
            flags &= (byte) ~TextDisplayFlag.HAS_SHADOW.getBit();
        }
        setTextDisplayFlags(flags);
    }

    public boolean hasShadow() {
        return (getTextDisplayFlags() & TextDisplayFlag.HAS_SHADOW.getBit()) != 0;
    }

    public void setIsSeeThrough(boolean isSeeThrough) {
        byte flags = getTextDisplayFlags();
        if (isSeeThrough) {
            flags |= TextDisplayFlag.IS_SEE_THROUGH.getBit();
        } else {
            flags &= (byte) ~TextDisplayFlag.IS_SEE_THROUGH.getBit();
        }
        setTextDisplayFlags(flags);
    }

    public boolean isSeeThrough() {
        return (getTextDisplayFlags() & TextDisplayFlag.IS_SEE_THROUGH.getBit()) != 0;
    }

    public void setUseDefaultBackgroundColor(boolean useDefault) {
        byte flags = getTextDisplayFlags();
        if (useDefault) {
            flags |= TextDisplayFlag.USE_DEFAULT_BG.getBit();
        } else {
            flags &= (byte) ~TextDisplayFlag.USE_DEFAULT_BG.getBit();
        }
        setTextDisplayFlags(flags);
    }

    public boolean useDefaultBackgroundColor() {
        return (getTextDisplayFlags() & TextDisplayFlag.USE_DEFAULT_BG.getBit()) != 0;
    }

    private static final byte ALIGNMENT_MASK  = 0x18; // bits 3 and 4
    private static final int  ALIGNMENT_SHIFT = 3;

    public void setTextDisplayAlignment(TextDisplayAlignment alignment) {

        byte flags = getTextDisplayFlags();
        // Clear alignment bits:
        flags = (byte) (flags & ~ALIGNMENT_MASK);
        // Set new alignment bits:
        flags |= (byte) ((alignment.getValue() << ALIGNMENT_SHIFT) & ALIGNMENT_MASK);
        setTextDisplayFlags(flags);
    }

    public TextDisplayAlignment getTextDisplayAlignment() {
        byte flags = getTextDisplayFlags();
        int value = (flags & ALIGNMENT_MASK) >> ALIGNMENT_SHIFT;
        return TextDisplayAlignment.fromValue(value);
    }
}
