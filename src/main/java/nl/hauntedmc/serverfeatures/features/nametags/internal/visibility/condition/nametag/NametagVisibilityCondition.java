package nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition.nametag;

import nl.hauntedmc.serverfeatures.features.nametags.internal.Nametag;
import nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.VisibilityCondition;

public abstract class NametagVisibilityCondition implements VisibilityCondition {
    public abstract boolean isVisible(Nametag nametag);
}
