package nl.hauntedmc.serverfeatures.features.nametags.internal;

import nl.hauntedmc.serverfeatures.features.nametags.internal.hook.PlaceholderHook;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Single owner + single TextDisplay. Knows how to (re)spawn and stay mounted.
 */
public class Nametag {
    private final Player owner;
    private TextDisplay display;

    // Visual config snapshot
    private boolean shadow;
    private boolean seeThrough;
    private boolean useDefaultBg;
    private int backgroundARGB;
    private int lineWidth;
    private double translationY;
    private double spawnYOffsetBlocks;

    public Nametag(Player owner) {
        this.owner = owner;
    }

    public Player getNametagOwner() {
        return owner;
    }

    public java.util.UUID getNametagOwnerId() {
        return owner.getUniqueId();
    }

    public TextDisplay getDisplay() {
        return display;
    }

    public void configureFromDefaults(boolean shadow, boolean seeThrough, boolean useDefaultBg, int backgroundARGB,
                                      int lineWidth, double translationY, double spawnYOffsetBlocks) {
        this.shadow = shadow;
        this.seeThrough = seeThrough;
        this.useDefaultBg = useDefaultBg;
        this.backgroundARGB = backgroundARGB;
        this.lineWidth = lineWidth;
        this.translationY = translationY;
        this.spawnYOffsetBlocks = spawnYOffsetBlocks;
    }

    public void spawnOrRespawn() {
        remove(); // ensure clean start

        World world = owner.getWorld();
        Location spawnLoc = owner.getLocation().clone().add(0.0, spawnYOffsetBlocks, 0.0);

        this.display = world.spawn(spawnLoc, TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(shadow);
            td.setSeeThrough(seeThrough);
            td.setDefaultBackground(useDefaultBg);
            td.setBackgroundColor(Color.fromARGB(backgroundARGB));
            td.setLineWidth(lineWidth);
            td.text(PlaceholderHook.getInstance().getNametagText(owner));
            td.setPersistent(false);
            td.setSilent(true);
            td.setInvulnerable(true);
            td.setGravity(false);

            // Upward relative translation while mounted
            Transformation t = td.getTransformation();
            Vector3f trans = new Vector3f(t.getTranslation());
            trans.y = (float) translationY;
            td.setTransformation(new Transformation(
                    trans,
                    new Quaternionf(t.getLeftRotation()),
                    new Vector3f(t.getScale()),
                    new Quaternionf(t.getRightRotation())
            ));
        });

        ensureMounted();
    }

    public void ensureMounted() {
        if (display == null || display.isDead()) return;
        if (!owner.getPassengers().contains(display)) {
            owner.addPassenger(display);
        }
    }

    public void updateTextOnly() {
        if (display != null && !display.isDead()) {
            display.text(PlaceholderHook.getInstance().getNametagText(owner));
        }
    }

    public void remove() {
        if (display != null) {
            try {
                if (display.getVehicle() != null) {
                    display.getVehicle().removePassenger(display);
                }
                display.remove();
            } finally {
                display = null;
            }
        }
    }
}
