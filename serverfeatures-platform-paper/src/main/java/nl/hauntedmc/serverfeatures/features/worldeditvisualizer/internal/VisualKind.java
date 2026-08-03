package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal;

enum VisualKind {
    EDGE(false),
    CORNER(false),
    POS1(false),
    POS2(false),
    LABEL_POS1(true),
    LABEL_POS2(true);

    private final boolean text;

    VisualKind(boolean text) {
        this.text = text;
    }

    boolean isText() {
        return text;
    }
}
