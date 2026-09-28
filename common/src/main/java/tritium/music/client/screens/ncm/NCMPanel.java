package tritium.music.client.screens.ncm;

import tritium.music.client.rendering.ui.container.Panel;

public abstract class NCMPanel extends Panel {

    private boolean detached;

    public abstract void onInit();

    public void onRemoved() {
    }

    public boolean isDetached() {
        return detached;
    }

    public void detach() {
        if (detached) {
            return;
        }
        detached = true;
        onRemoved();
    }

    protected int getColor(NCMScreen.ColorType type) {
        return 0xFF000000 | NCMScreen.getColor(type);
    }
}
