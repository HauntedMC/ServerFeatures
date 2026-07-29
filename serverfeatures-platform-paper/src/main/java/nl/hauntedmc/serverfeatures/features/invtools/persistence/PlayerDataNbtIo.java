package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;

import java.io.IOException;
import java.nio.file.Path;

interface PlayerDataNbtIo {

    ReadWriteNBT read(byte[] bytes, Path source) throws IOException;

    void write(Path destination, ReadWriteNBT root) throws IOException;
}
