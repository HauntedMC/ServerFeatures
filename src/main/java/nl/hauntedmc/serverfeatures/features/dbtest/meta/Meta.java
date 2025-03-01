package nl.hauntedmc.serverfeatures.features.dbtest.meta;

import nl.hauntedmc.serverfeatures.features.BaseMeta;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "DBTest";
    }

    @Override
    public String getFeatureVersion() {
        return "1.0.0";
    }

}
