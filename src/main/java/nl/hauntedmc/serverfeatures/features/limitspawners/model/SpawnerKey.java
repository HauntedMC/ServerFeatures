package nl.hauntedmc.serverfeatures.features.limitspawners.model;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record SpawnerKey(UUID worldId, int x, int y, int z) {

        public static SpawnerKey of(Location loc) {
                return new SpawnerKey(loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof SpawnerKey(UUID id, int x1, int y1, int z1))) return false;
                return x == x1 && y == y1 && z == z1 && worldId.equals(id);
            }

        @Override
        public @NotNull String toString() {
                return worldId + ":" + x + ":" + y + ":" + z;
            }

            public static SpawnerKey fromString(String s) {
                String[] parts = s.split(":");
                if (parts.length != 4) return null;
                UUID w = UUID.fromString(parts[0]);
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                return new SpawnerKey(w, x, y, z);
            }
        }