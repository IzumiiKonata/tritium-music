package tritium.music.fabric.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import tritium.music.core.model.Music;
import tritium.music.platform.TextureHandle;

public class NowPlayingToast implements Toast {

    private static final long DISPLAY_MS = 5000L;
    private static final int WIDTH = 200;
    private static final int HEIGHT = 40;

    private final String title;
    private final String subtitle;
    private final TextureHandle cover;

    private Visibility visibility = Visibility.SHOW;

    private NowPlayingToast(Music music) {
        this.title = music.getName();
        this.subtitle = music.getArtistsName();
        this.cover = music.getSmallCoverLocation();
    }

    public static void show(Music music) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.gui.toastManager().addToast(new NowPlayingToast(music)));
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    @Override
    public Visibility getWantedVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        visibility = fullyVisibleForMs >= DISPLAY_MS ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
        Draw.roundedRect(graphics, 0, 0, WIDTH, HEIGHT, 6f, Colors.withAlpha(Colors.ELEMENT, 0.96f));
        Draw.roundedRect(graphics, 0, 0, 3, HEIGHT, 1.5f, Colors.ACCENT);

        int coverSize = HEIGHT - 12;
        int coverX = 8;
        int coverY = 6;
        if (Minecraft.getInstance().getTextureManager() != null) {
            Draw.roundedRect(graphics, coverX, coverY, coverSize, coverSize, 3f, Colors.BACKGROUND);
            Draw.texture(graphics, cover, coverX, coverY, coverSize, coverSize, 1f);
        }

        int textX = coverX + coverSize + 8;
        int textW = WIDTH - textX - 8;
        Draw.text(graphics, font, Draw.trim(font, title, textW), textX, 9, Colors.PRIMARY_TEXT);
        Draw.text(graphics, font, Draw.trim(font, subtitle, textW), textX, 22, Colors.SECONDARY_TEXT);
    }
}
