package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.NBTCompound;
import de.tr7zw.changeme.nbtapi.NBTReflectionUtil;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.changeme.nbtapi.utils.nmsmappings.ReflectionMethod;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

/**
 * Thin, version-pinned bridge to the same full {@code DataFixTypes.PLAYER} conversion Paper uses
 * when it loads a player record. All NMS discovery is lazy so current-version unit tests do not
 * require a running Minecraft server.
 */
public final class PaperPlayerDataConverter implements PlayerDataConverter {

    private static final String DATA_FIX_TYPES_CLASS = "net.minecraft.util.datafix.DataFixTypes";
    private static final String COMPOUND_TAG_CLASS = "net.minecraft.nbt.CompoundTag";
    private static final String PLAYER_TYPE = "PLAYER";
    private static final String UPDATE_METHOD = "updateToCurrentVersion";

    private volatile Bridge bridge;

    /**
     * Resolves the version-pinned Paper bridge without mutating playerdata. InvTools calls this
     * during feature initialization so mapping or access changes fail before a staff command can
     * begin a migration.
     */
    public void verifyAvailable() throws IOException {
        bridge();
    }

    @Override
    public ReadWriteNBT convertToCurrent(
            ReadWriteNBT source,
            int sourceVersion,
            int targetVersion
    ) throws IOException {
        Objects.requireNonNull(source, "source");
        if (sourceVersion <= 0 || targetVersion <= 0 || sourceVersion >= targetVersion) {
            throw new IOException(
                    "Invalid playerdata conversion range " + sourceVersion + " -> " + targetVersion
            );
        }
        if (!(source instanceof NBTCompound compound)) {
            throw new IOException("NBT-API did not expose a mutable playerdata compound");
        }

        Object rawTag;
        try {
            rawTag = NBTReflectionUtil.getToCompount(compound.getCompound(), compound);
        } catch (RuntimeException | LinkageError exception) {
            throw new IOException("Could not unwrap playerdata for Paper's data fixer", exception);
        }
        if (rawTag == null) {
            throw new IOException("Paper playerdata conversion received an empty NBT root");
        }

        Bridge activeBridge = bridge();
        if (!activeBridge.updateMethod().getParameterTypes()[1].isInstance(rawTag)) {
            throw new IOException(
                    "NBT-API returned " + rawTag.getClass().getName()
                            + " instead of Paper's CompoundTag"
            );
        }

        Object converted;
        try {
            converted = activeBridge.updateMethod().invoke(
                    activeBridge.playerType(),
                    activeBridge.dataFixer(),
                    rawTag,
                    sourceVersion
            );
        } catch (IllegalAccessException exception) {
            throw new IOException("Paper's player data fixer is not accessible", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IOException(
                    "Paper could not convert playerdata from " + sourceVersion + " to "
                            + targetVersion,
                    cause
            );
        } catch (RuntimeException | LinkageError exception) {
            throw new IOException("Paper's player data fixer failed", exception);
        }
        if (converted == null) {
            throw new IOException("Paper's player data fixer returned no converted NBT");
        }
        if (!activeBridge.updateMethod().getReturnType().isInstance(converted)) {
            throw new IOException(
                    "Paper's player data fixer returned " + converted.getClass().getName()
                            + " instead of CompoundTag"
            );
        }

        try {
            ReadWriteNBT result = NBT.wrapNMSTag(converted);
            result.setInteger("DataVersion", targetVersion);
            return result;
        } catch (RuntimeException | LinkageError exception) {
            throw new IOException("Could not wrap Paper's converted playerdata", exception);
        }
    }

    private Bridge bridge() throws IOException {
        Bridge resolved = bridge;
        if (resolved != null) {
            return resolved;
        }
        synchronized (this) {
            resolved = bridge;
            if (resolved == null) {
                resolved = resolveBridge();
                bridge = resolved;
            }
        }
        return resolved;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Bridge resolveBridge() throws IOException {
        try {
            Object dataFixer = ReflectionMethod.GET_DATAFIXER.run(null);
            if (dataFixer == null) {
                throw new IOException("Paper did not expose its server data fixer");
            }

            Class<?> dataFixTypes = Class.forName(DATA_FIX_TYPES_CLASS);
            if (!dataFixTypes.isEnum()) {
                throw new IOException(DATA_FIX_TYPES_CLASS + " is not an enum on this Paper build");
            }
            Object playerType = Enum.valueOf(
                    (Class<? extends Enum>) dataFixTypes,
                    PLAYER_TYPE
            );
            Method updateMethod = selectCompoundUpdateMethod(
                    dataFixTypes,
                    dataFixer,
                    COMPOUND_TAG_CLASS
            );
            return new Bridge(dataFixer, playerType, updateMethod);
        } catch (IOException exception) {
            throw exception;
        } catch (ClassNotFoundException | IllegalArgumentException exception) {
            throw new IOException(
                    "This Paper build does not expose the expected PLAYER data fixer",
                    exception
            );
        } catch (RuntimeException | LinkageError exception) {
            throw new IOException("Could not initialize Paper's PLAYER data fixer", exception);
        }
    }

    static Method selectCompoundUpdateMethod(
            Class<?> dataFixTypes,
            Object dataFixer,
            String compoundTagClassName
    ) throws IOException {
        Objects.requireNonNull(dataFixTypes, "dataFixTypes");
        Objects.requireNonNull(dataFixer, "dataFixer");
        Objects.requireNonNull(compoundTagClassName, "compoundTagClassName");
        return Arrays.stream(dataFixTypes.getMethods())
                .filter(method -> method.getName().equals(UPDATE_METHOD))
                .filter(method -> method.getParameterCount() == 3)
                .filter(method -> method.getParameterTypes()[2] == int.class)
                .filter(method -> method.getParameterTypes()[0].isInstance(dataFixer))
                .filter(method -> method.getParameterTypes()[1]
                        .getName()
                        .equals(compoundTagClassName))
                .filter(method -> method.getReturnType().getName().equals(compoundTagClassName))
                .findFirst()
                .orElseThrow(() -> new IOException(
                        "Paper's CompoundTag PLAYER data-fixer method was not found on this build"
                ));
    }

    private record Bridge(Object dataFixer, Object playerType, Method updateMethod) {
        private Bridge {
            Objects.requireNonNull(dataFixer, "dataFixer");
            Objects.requireNonNull(playerType, "playerType");
            Objects.requireNonNull(updateMethod, "updateMethod");
        }
    }
}
