package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;

final class NbtApiPlayerDataIo implements PlayerDataNbtIo {

    @Override
    public ReadWriteNBT read(byte[] bytes, Path source) throws IOException {
        try {
            return NBT.readNBT(new ByteArrayInputStream(bytes));
        } catch (RuntimeException | LinkageError exception) {
            throw new IOException(
                    "Could not parse playerdata file " + source.getFileName(),
                    exception
            );
        }
    }

    @Override
    public void write(Path destination, ReadWriteNBT root) throws IOException {
        try {
            NBT.writeFile(destination.toFile(), root);
        } catch (RuntimeException | LinkageError exception) {
            throw new IOException(
                    "Could not encode playerdata file " + destination.getFileName(),
                    exception
            );
        }
    }
}
