package nl.hauntedmc.serverfeatures.features.sanitize.internal.task;

public final class SanitizeResult {

    private final boolean changed;
    private final String summary;

    private SanitizeResult(boolean changed, String summary) {
        this.changed = changed;
        this.summary = summary;
    }

    public static SanitizeResult changed(String summary) {
        return new SanitizeResult(true, summary);
    }

    public static SanitizeResult unchanged(String summary) {
        return new SanitizeResult(false, summary);
    }

    public boolean changed() { return changed; }
    public String summary() { return summary; }
}
