package nl.hauntedmc.serverfeatures.api.ui.inventory.preview.item;

import nl.hauntedmc.serverfeatures.api.ui.inventory.preview.PreviewHolder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemPreviewHolderTest {

    @Test
    void supportsNullSnapshotAndUsesNullInventoryMarker() {
        ItemPreviewHolder holder = new ItemPreviewHolder((ItemStack) null);

        assertNull(holder.snapshot());
        assertNull(holder.getInventory());
    }

    @Test
    void retainsProvidedSnapshotAndImplementsPreviewMarker() {
        ItemStack snapshot = new DummyItemStack(Material.STONE);
        ItemPreviewHolder holder = new ItemPreviewHolder(snapshot);

        assertSame(snapshot, holder.snapshot());
        assertTrue(holder instanceof PreviewHolder);
        assertNull(holder.getInventory());
    }

    private static final class DummyItemStack extends ItemStack {

        private final Material material;

        private DummyItemStack(Material material) {
            this.material = material;
        }

        @Override
        public Material getType() {
            return material;
        }
    }
}
