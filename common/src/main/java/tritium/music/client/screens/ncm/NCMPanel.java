package tritium.music.client.screens.ncm;

import tritium.music.client.rendering.ui.container.Panel;

public abstract class NCMPanel extends Panel {

    public abstract void onInit();

    protected int getColor(NCMScreen.ColorType type) {
        return 0xFF000000 | NCMScreen.getColor(type);
    }
}
