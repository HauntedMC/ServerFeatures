package nl.hauntedmc.serverfeatures.features.balloons.util;

import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DistanceTest {

    @Test
    void doesNothingWhenWorldsDiffer() {
        World parrotWorld = InterfaceProxy.of(World.class, Map.of());
        World playerWorld = InterfaceProxy.of(World.class, Map.of());

        AtomicInteger setVelocityCalls = new AtomicInteger();
        Entity parrot = parrotProxy(parrotWorld, new Location(parrotWorld, 0, 0, 0), new Vector(), setVelocityCalls);
        Player player = playerProxy(playerWorld, new Location(playerWorld, 10, 0, 0));

        Distance.line(parrot, player);

        assertEquals(0, setVelocityCalls.get());
    }

    @Test
    void addsPullVelocityWhenParrotIsTooFar() {
        World world = InterfaceProxy.of(World.class, Map.of());
        AtomicInteger setVelocityCalls = new AtomicInteger();
        AtomicReference<Vector> velocity = new AtomicReference<>(new Vector(0, 0, 0));

        Entity parrot = parrotProxy(world, new Location(world, 0, 0, 0), velocity, setVelocityCalls);
        Player player = playerProxy(world, new Location(world, 4, 0, 0));

        Distance.line(parrot, player);

        assertEquals(1, setVelocityCalls.get());
        assertVector(velocity.get(), 0.2D, 0.0D, 0.0D);
    }

    @Test
    void addsVerticalVelocityWhenParrotIsTooClose() {
        World world = InterfaceProxy.of(World.class, Map.of());
        AtomicInteger setVelocityCalls = new AtomicInteger();
        AtomicReference<Vector> velocity = new AtomicReference<>(new Vector(0, 0, 0));

        Entity parrot = parrotProxy(world, new Location(world, 0, 0, 0), velocity, setVelocityCalls);
        Player player = playerProxy(world, new Location(world, 2, 0, 0));

        Distance.line(parrot, player);

        assertEquals(1, setVelocityCalls.get());
        assertVector(velocity.get(), 0.0D, 0.3D, 0.0D);
    }

    private static Entity parrotProxy(World world, Location location, Vector initialVelocity, AtomicInteger setVelocityCalls) {
        return parrotProxy(world, location, new AtomicReference<>(initialVelocity), setVelocityCalls);
    }

    private static Entity parrotProxy(World world, Location location, AtomicReference<Vector> velocity, AtomicInteger setVelocityCalls) {
        return InterfaceProxy.of(Entity.class, Map.of(
                "getWorld", args -> world,
                "getLocation", args -> location,
                "getVelocity", args -> velocity.get(),
                "setVelocity", args -> {
                    setVelocityCalls.incrementAndGet();
                    velocity.set((Vector) args[0]);
                    return null;
                }
        ));
    }

    private static Player playerProxy(World world, Location location) {
        return InterfaceProxy.of(Player.class, Map.of(
                "getWorld", args -> world,
                "getLocation", args -> location
        ));
    }

    private static void assertVector(Vector vector, double x, double y, double z) {
        assertEquals(x, vector.getX(), 0.00001D);
        assertEquals(y, vector.getY(), 0.00001D);
        assertEquals(z, vector.getZ(), 0.00001D);
    }
}
