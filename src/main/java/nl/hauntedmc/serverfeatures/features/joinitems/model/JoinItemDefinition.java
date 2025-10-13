package nl.hauntedmc.serverfeatures.features.joinitems.model;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.util.text.format.ComponentFormatter;
import nl.hauntedmc.serverfeatures.api.util.text.format.TextFormatter;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Immutable definition of a join item parsed from config. */
public final class JoinItemDefinition {

    private final String id;
    private final Material material;
    private final int slot;
    private final Component name;
    private final List<Component> lore;
    private final List<String> commands;
    private final boolean locked;
    private final boolean unmovable;
    private final boolean undroppable;

    public JoinItemDefinition(
            String id, Material material, int slot,
            Component name, List<Component> lore, List<String> commands,
            boolean locked, boolean unmovable, boolean undroppable
    ) {
        this.id = Objects.requireNonNull(id, "id").toLowerCase(Locale.ROOT);
        this.material = Objects.requireNonNull(material, "material");
        this.slot = slot;
        this.name = name;
        this.lore = lore == null ? List.of() : List.copyOf(lore);
        this.commands = commands == null ? List.of() : List.copyOf(commands);
        this.locked = locked;
        this.unmovable = unmovable;
        this.undroppable = undroppable;
    }

    public String id() { return id; }
    public Material material() { return material; }
    public int slot() { return slot; }
    public Component name() { return name; }
    public List<Component> lore() { return lore; }
    public List<String> commands() { return commands; }
    public boolean locked() { return locked; }
    public boolean unmovable() { return unmovable; }
    public boolean undroppable() { return undroppable; }

    // Utility: parse &-codes into Components
    public static Component toComponent(String s) {
        if (s == null || s.isBlank()) return Component.empty();
        return ComponentFormatter.deserialize(s).expect(TextFormatter.InputFormat.LEGACY_AMPERSAND).features(ComponentFormatter.Feature.COLORS).toComponent();
    }

    public static List<Component> toComponents(List<String> lines) {
        if (lines == null || lines.isEmpty()) return List.of();
        List<Component> out = new ArrayList<>(lines.size());
        for (String l : lines) out.add(toComponent(l));
        return out;
    }
}
