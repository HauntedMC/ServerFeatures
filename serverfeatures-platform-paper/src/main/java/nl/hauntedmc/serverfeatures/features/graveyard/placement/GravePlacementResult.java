package nl.hauntedmc.serverfeatures.features.graveyard.placement;

import nl.hauntedmc.serverfeatures.features.graveyard.model.GraveLocation;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GravePlacementType;

public record GravePlacementResult(GraveLocation location, GravePlacementType type) {
}
