package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal;

record BlockPoint(int x, int y, int z) {

    VisualPoint center() {
        return VisualPoint.blockCenter(x, y, z);
    }
}
