package nl.hauntedmc.serverfeatures.api.command.tab.suggestion;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/** Rich suggestion voor Paper: display vs insert + optionele tooltip. */
public final class Suggestion {
    private final String text;
    private final @Nullable String insert;
    private final @Nullable Component tooltip;

    private Suggestion(String text, @Nullable String insert, @Nullable Component tooltip) {
        this.text = text;
        this.insert = insert;
        this.tooltip = tooltip;
    }

    public static Suggestion of(String text) { return new Suggestion(text, null, null); }
    public static Suggestion of(String text, String insert) { return new Suggestion(text, insert, null); }
    public static Suggestion of(String text, String insert, @Nullable Component tooltip) { return new Suggestion(text, insert, tooltip); }

    public String text() { return text; }
    public @Nullable String insert() { return insert; }
    public @Nullable Component tooltip() { return tooltip; }

    /** Sleutel voor filter/sort. */
    public String key() { return text; }
}
