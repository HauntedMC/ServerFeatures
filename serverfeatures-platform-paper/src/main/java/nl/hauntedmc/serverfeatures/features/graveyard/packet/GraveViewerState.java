package nl.hauntedmc.serverfeatures.features.graveyard.packet;

public final class GraveViewerState {
    private long generation;
    private boolean spawned;
    private String renderedTimer = "";

    public long generation() {
        return generation;
    }

    public boolean spawned() {
        return spawned;
    }

    public String renderedTimer() {
        return renderedTimer;
    }

    public void markSpawned(long nextGeneration, String timer) {
        generation = nextGeneration;
        spawned = true;
        renderedTimer = timer;
    }

    public void markHidden() {
        generation++;
        spawned = false;
        renderedTimer = "";
    }

    public void setRenderedTimer(String timer) {
        renderedTimer = timer;
    }
}
