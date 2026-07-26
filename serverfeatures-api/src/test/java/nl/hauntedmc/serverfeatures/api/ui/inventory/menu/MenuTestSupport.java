package nl.hauntedmc.serverfeatures.api.ui.inventory.menu;

public final class MenuTestSupport {

    private MenuTestSupport() {
    }

    public static MenuNavigator guiManager() {
        return new MenuNavigator() {
            @Override
            public void openRoot(org.bukkit.entity.Player player, GuiMenu menu) {
            }

            @Override
            public void openChild(org.bukkit.entity.Player player, GuiMenu menu) {
            }

            @Override
            public void reopenSame(org.bukkit.entity.Player player, GuiMenu menu) {
            }

            @Override
            public boolean goBack(org.bukkit.entity.Player player) {
                return false;
            }
        };
    }
}
