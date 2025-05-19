package nl.hauntedmc.serverfeatures.features.votereward.meta;

import nl.hauntedmc.commonlib.featureapi.feature.meta.BaseMeta;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "VoteReward";
    }

    @Override
    public String getFeatureVersion() {
        return "1.0.0";
    }
}
