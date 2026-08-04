package nl.hauntedmc.serverfeatures.api.graveyard;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Public, ownership-safe access to Graveyard metadata and claim requests.
 */
public interface GraveyardService {
    Optional<GraveSnapshot> find(UUID graveId);

    Optional<GraveSnapshot> findByShortId(String shortId);

    List<GraveSnapshot> findActiveByOwner(UUID ownerUuid);

    CompletionStage<GraveClaimResult> requestClaim(
            UUID graveId,
            UUID ownerUuid,
            ClaimReason reason
    );
}
