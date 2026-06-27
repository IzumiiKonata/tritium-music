package tritium.music.client.screens.ncm;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import tritium.music.client.rendering.Image;
import tritium.music.client.rendering.RGBA;
import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.TextField;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.screens.BaseScreen;
import tritium.music.core.CloudMusic;
import tritium.music.core.model.Album;
import tritium.music.core.model.Music;
import tritium.music.core.model.PlayList;
import tritium.music.core.util.AsyncUtil;
import tritium.music.core.util.Textures;
import tritium.music.platform.Platform;
import tritium.music.platform.TextureHandle;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * A horizontal cover carousel grouped by album. The original used a real 3D
 * perspective projection (gluPerspective + Y-axis rotations); 26.2's GUI render
 * path is 2D only, so this is a 2D scaled-cover carousel preserving the same
 * navigation, search and play behaviour.
 */
public class CoverflowOverlay extends BaseScreen {

    @Getter
    private static final CoverflowOverlay instance = new CoverflowOverlay();

    public boolean closing = false;
    public float alpha = 0.0f;

    Map<Album, List<Music>> albumList = new ConcurrentHashMap<>();
    Map<Album, AlbumRenderingData> albumRenderingData = new HashMap<>();
    List<Album> renderList = new CopyOnWriteArrayList<>();

    TextField textBox = new TextField();

    boolean reloadOnClosed = false;

    private static class AlbumRenderingData {
        public boolean coverLoaded;
        public double scale = .75;
    }

    public void display() {
        closing = false;
    }

    private static boolean isCtrlDown() {
        long handle = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private static boolean isShiftDown() {
        long handle = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
    }

    @Override
    public void removed() {
        if (this.reloadOnClosed) {
            this.albumList.clear();
            this.reloadOnClosed = false;
            Minecraft.getInstance().setScreenAndShow(NCMScreen.getInstance());
        }
    }

    private void loadAlbumData(List<PlayList> list) {
        AsyncUtil.runAsync(() -> {
            for (PlayList pl : list) {
                if (pl == null) continue;

                List<Music> musics = pl.getMusics();
                if (musics == null) continue;

                for (Music m : musics) {
                    if (m == null) continue;
                    Album album = m.getAlbum();
                    if (album == null) continue;

                    List<Music> playLists = albumList.computeIfAbsent(album, k -> new CopyOnWriteArrayList<>());
                    playLists.add(m);
                }
            }

            renderList.addAll(this.albumList.keySet());
        });
    }

    @Override
    protected void init() {
        if (!this.reloadOnClosed && this.albumList.isEmpty())
            this.loadAlbumData(CloudMusic.playLists);
    }

    int index = 0;
    double scrollOffset = 0;

    @Override
    public void drawScreen(double mouseX, double mouseY) {

        alpha = Interpolations.interpolate(alpha, closing ? .0f : 1f, 0.2f);

        if (alpha <= 0.05)
            return;

        double screenW = RenderSystem.getWidth();
        double screenH = RenderSystem.getHeight();
        double vignetteH = screenH * 0.3;

        Rect.draw(0, 0, screenW, screenH, RGBA.black(alpha * 0.6f));
        RenderSystem.drawGradientRectTopToBottom(0, 0, screenW, vignetteH, RGBA.black(alpha * 0.35f), RGBA.black(0f));
        RenderSystem.drawGradientRectTopToBottom(0, screenH - vignetteH, screenW, screenH, RGBA.black(0f), RGBA.black(alpha * 0.45f));

        if (albumList.isEmpty())
            return;

        textBox.xPosition = 8;
        textBox.yPosition = 8;
        textBox.width = 240;
        textBox.height = 34;
        textBox.setFontRenderer(FontManager.pf40bold);
        textBox.setPlaceholder("Search (Ctrl + F)");
        textBox.setTextChangedCallback(text -> {
            renderList.clear();

            if (text.isEmpty()) {
                renderList.addAll(albumList.keySet());
            } else {
                for (Album album : albumList.keySet()) {
                    if (album.getName().toLowerCase().contains(text.toLowerCase()))
                        renderList.add(album);
                }

                for (List<Music> m : albumList.values()) {
                    for (Music music : m) {
                        if (music.getName().toLowerCase().contains(text.toLowerCase())) {
                            renderList.add(music.getAlbum());
                            break;
                        }
                        if (music.getTranslatedNames() != null && music.getTranslatedNames().toLowerCase().contains(text.toLowerCase())) {
                            renderList.add(music.getAlbum());
                        }
                    }
                }

                renderList = new CopyOnWriteArrayList<>(renderList.stream().distinct().collect(Collectors.toList()));
            }
        });

        textBox.drawTextBox((int) mouseX, (int) mouseY);

        double coverSize = 160;
        double spacing = 28;
        double centerX = screenW * 0.5;
        double centerY = screenH * 0.5;

        int dWheel = consumeWheel();

        if (dWheel != 0 && !renderList.isEmpty()) {
            int amount = isShiftDown() ? 5 : 1;
            if (dWheel > 0) index -= amount;
            else index += amount;
        }

        index = Math.max(0, Math.min(renderList.size() - 1, index));

        scrollOffset = Interpolations.interpolate(scrollOffset, index * (coverSize * 0.5 + spacing), 0.2f);

        for (int i = 0; i < renderList.size(); i++) {
            if (Math.abs(i - index) > 7) continue;

            Album al = renderList.get(i);
            double slot = i * (coverSize * 0.5 + spacing) - scrollOffset;
            this.renderCover(al, centerX + slot, centerY, coverSize, i == index, mouseX, mouseY);
        }

        if (!renderList.isEmpty()) {
            Album al = renderList.get(index);
            CFontRenderer fr = FontManager.pf40bold;
            double titleY = centerY + coverSize * 0.5 + 12;
            fr.drawCenteredStringWithShadow(al.getName(), centerX, titleY, RGBA.white(alpha));

            List<String> translatedNames = al.getTranslatedName();
            if (translatedNames != null && !translatedNames.isEmpty()) {
                CFontRenderer subtitle = FontManager.pf25;
                subtitle.drawCenteredString(translatedNames.get(0), centerX, titleY + fr.getHeight() + 3, RGBA.color(185, 185, 192, (int) (190 * alpha)));
            }
        }
    }

    private void renderCover(Album al, double cx, double cy, double coverSize, boolean isCenter, double mouseX, double mouseY) {
        AlbumRenderingData data = albumRenderingData.computeIfAbsent(al, k -> new AlbumRenderingData());

        TextureHandle coverLoc = al.getCoverLocation();
        if (!Platform.hasTexture(coverLoc) && !data.coverLoaded) {
            data.coverLoaded = true;
            int cSize = 512;
            Textures.downloadTextureAndLoadAsync(al.getPicUrl() + "?param=" + cSize + "y" + cSize, coverLoc);
        }

        data.scale = Interpolations.interpolate(data.scale, isCenter ? 1.0 : 0.7, 0.2f);

        double size = coverSize * data.scale;
        double x = cx - size * 0.5;
        double y = cy - size * 0.5;

        Rect.draw(x - 1, y - 1, size + 2, size + 2, RGBA.black(alpha * 0.5f));

        if (Platform.hasTexture(coverLoc)) {
            Image.draw(coverLoc, x, y, size, size, Image.Type.NoColor);
        } else {
            Rect.draw(x, y, size, size, RGBA.color(40, 40, 40, (int) (alpha * 255)));
        }

        boolean hovering = RenderSystem.isHovered(mouseX, mouseY, x, y, size, size);
        if (hovering && !isCenter) {
            Rect.draw(x, y, size, size, RGBA.white(alpha * 0.12f));
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (this.textBox.mouseClicked(mouseX, mouseY, button)) {
            return;
        }

        if (renderList.isEmpty()) return;

        Album al = renderList.get(index);
        List<Music> musics = albumList.get(al);
        if (musics != null && !musics.isEmpty()) {
            CloudMusic.play(musics, 0);
        }
    }

    @Override
    public void onKeyTyped(char typedChar, int keyCode) {

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.textBox.isFocused()) {
                this.textBox.setFocused(false);
                return;
            }
            Minecraft.getInstance().setScreenAndShow(NCMScreen.getInstance());
            return;
        }

        if (this.textBox.isFocused()) {
            if (keyCode != 0) {
                boolean handled = this.textBox.keyPressed(keyCode);
                if (!handled && typedChar != 0) this.textBox.charTyped(typedChar);
            } else if (typedChar != 0) {
                this.textBox.charTyped(typedChar);
            }
            return;
        }

        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            if (index > 0) index--;
            return;
        }

        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (index < renderList.size() - 1) index++;
            return;
        }

        if (isCtrlDown() && keyCode == GLFW.GLFW_KEY_F) {
            this.textBox.setFocused(true);
        }
    }

    public static CoverflowOverlay byPlaylist(PlayList playList) {
        CoverflowOverlay screen = getInstance();

        screen.reloadOnClosed = true;
        screen.albumList.clear();
        screen.renderList.clear();
        screen.loadAlbumData(Collections.singletonList(playList));

        return screen;
    }
}
