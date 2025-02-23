package nl.hauntedmc.serverfeatures.features.nametags.internal.update;

import nl.hauntedmc.serverfeatures.features.nametags.internal.Nametag;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles building the passenger list that should be sent to players.
 * It always retrieves the current (up-to-date) list of passengers from the player
 * and prefixes that list with the nametag’s custom entity ID.
 */
public class PassengerHandler {

    public int[] updatePassengerList(Player player, Nametag nametag) {
        List<Integer> passengerIds = new ArrayList<>();

        passengerIds.add(nametag.getEntityId());
        for (Entity passenger : player.getPassengers()) {
            passengerIds.add(passenger.getEntityId());
        }
        int[] passengers = passengerIds.stream().mapToInt(Integer::intValue).toArray();

        return passengers;
    }
}
