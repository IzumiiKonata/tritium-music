package tritium.music.client.screens.ncm;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import tritium.music.client.render.Render;
import tritium.music.client.render.RenderContext;
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
import tritium.music.fabric.ui.Identifiers;
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
 * A 3D Cover Flow grouped by album. Covers are transformed in 3D (carousel
 * X-offset + Y-axis rotation) and projected through a perspective matrix on the
 * CPU, then submitted as textured quads with the four projected screen corners,
 * reproducing the original gluPerspective view on 26.2's 2D GUI pipeline.
 */
public class CoverflowOverlay extends BaseScreen {

    private static final double FOVY = 45.0;
    private static final double NEAR_TRANSLATE_Z = 200.0;

    @Getter
    private static final CoverflowOverlay instance = new CoverflowOverlay();

    public boolean closing = false;
    public float alpha = 0.0f;

    Map<Album, List<Music>> albumList = new ConcurrentHashMap<>();
    Map<Album, AlbumRenderingData> albumRenderingData = new HashMap<>();
    List<Album> renderList = new CopyOnWriteArrayList<>();

    TextField textBox = new TextField();

    boolean reloadOnClosed = false;

    @Override
    protected float screenAlpha() {
        return alpha;
    }

    private static class AlbumRenderingData {
        public boolean coverLoaded;
        public float rotateDeg = 0f;
        public double scale = .75;
    }

    private static double[] project(double x, double y, double z, double f, double aspect, double screenW, double screenH) {
        double mz = z - NEAR_TRANSLATE_Z;
        double my = -y;
        double ndcX = (x * f / aspect) / -mz;
        double ndcY = (my * f) / -mz;
        return new double[]{(ndcX * 0.5 + 0.5) * screenW, (ndcY * 0.5 + 0.5) * screenH};
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

        double coverSize = 96;
        double spacing = 24;
        float rotDegTarget = 45;

        int dWheel = consumeWheel();

        if (dWheel != 0 && !renderList.isEmpty()) {
            int amount = isShiftDown() ? 5 : 1;
            if (dWheel > 0) index -= amount;
            else index += amount;
        }

        index = Math.max(0, Math.min(renderList.size() - 1, index));

        scrollOffset = Interpolations.interpolate(scrollOffset, index * (coverSize * 0.5 + spacing), 0.2f);

        double offsetX = -coverSize * 0.5 - scrollOffset;
        for (int i = 0; i < index; i++) {
            if (index - i <= 7) {
                this.renderCover(renderList.get(i), offsetX, coverSize, rotDegTarget, i, true, mouseX, mouseY);
            }
            offsetX += coverSize * 0.5 + spacing;
        }

        offsetX = -coverSize * 0.5 + ((coverSize * 0.5 + spacing) * (renderList.size() - 1)) - scrollOffset;
        for (int i = renderList.size() - 1; i >= index; i--) {
            if (i - index <= 7) {
                this.renderCover(renderList.get(i), offsetX, coverSize, rotDegTarget, i, false, mouseX, mouseY);
            }
            offsetX -= coverSize * 0.5 + spacing;
        }

        if (!renderList.isEmpty()) {
            Album al = renderList.get(index);
            CFontRenderer fr = FontManager.pf50bold;
            double titleX = screenW * 0.5;
            double titleY = screenH * 0.5 + coverSize * 0.95;
            fr.drawCenteredStringWithShadow(al.getName(), titleX, titleY, RGBA.white(alpha));

            List<String> translatedNames = al.getTranslatedName();
            if (translatedNames != null && !translatedNames.isEmpty()) {
                CFontRenderer subtitle = FontManager.pf25;
                subtitle.drawCenteredString(translatedNames.get(0), titleX, titleY + fr.getHeight() + 3, RGBA.color(185, 185, 192, (int) (190 * alpha)));
            }
        }
    }

    private void renderCover(Album al, double offsetX, double coverSize, float rotDegTarget, int i, boolean left, double mouseX, double mouseY) {
        AlbumRenderingData data = albumRenderingData.computeIfAbsent(al, k -> new AlbumRenderingData());

        TextureHandle coverLoc = al.getCoverLocation();
        if (!Platform.hasTexture(coverLoc) && !data.coverLoaded) {
            data.coverLoaded = true;
            int cSize = 512;
            Textures.downloadTextureAndLoadAsync(al.getPicUrl() + "?param=" + cSize + "y" + cSize, coverLoc);
        }

        if (index != i) {
            data.rotateDeg = Interpolations.interpolate(data.rotateDeg, rotDegTarget * (left ? 1 : -1), 0.2f);
            data.scale = Interpolations.interpolate(data.scale, 0.75, 0.2f);
        } else {
            data.rotateDeg = Interpolations.interpolate(data.rotateDeg, 0f, 0.2f);
            data.scale = Interpolations.interpolate(data.scale, 1.0, 0.2f);
        }

        double screenW = RenderSystem.getWidth();
        double screenH = RenderSystem.getHeight();
        double aspect = screenW / screenH;
        double f = 1.0 / Math.tan(Math.toRadians(FOVY) / 2.0);

        double cx = offsetX + coverSize * 0.5;
        double cy = 0;

        double half = coverSize * 0.5 * data.scale;
        double rad = Math.toRadians(data.rotateDeg);
        double cosR = Math.cos(rad), sinR = Math.sin(rad);

        double[][] local = {{-half, -half}, {-half, half}, {half, half}, {half, -half}};
        double[][] screen = new double[4][];
        for (int c = 0; c < 4; c++) {
            double lx = local[c][0];
            double ly = local[c][1];
            double wx = cx + lx * cosR;
            double wz = lx * sinR;
            double wy = cy + ly;

            screen[c] = project(wx, wy, wz, f, aspect, screenW, screenH);
        }

        boolean center = index == i;

        int shade = RGBA.color(255, 255, 255, (int) (22 * alpha));
        Render.colorQuad(RenderContext.graphics(),
                (float) screen[0][0], (float) screen[0][1],
                (float) screen[1][0], (float) screen[1][1],
                (float) screen[2][0], (float) screen[2][1],
                (float) screen[3][0], (float) screen[3][1], shade);

        if (Platform.hasTexture(coverLoc)) {
            Render.texturedQuad(RenderContext.graphics(), Identifiers.of(coverLoc),
                    (float) screen[0][0], (float) screen[0][1],
                    (float) screen[1][0], (float) screen[1][1],
                    (float) screen[2][0], (float) screen[2][1],
                    (float) screen[3][0], (float) screen[3][1], false, alpha);
        }

        if (center) {
            double minX = Math.min(Math.min(screen[0][0], screen[1][0]), Math.min(screen[2][0], screen[3][0]));
            double minY = Math.min(Math.min(screen[0][1], screen[1][1]), Math.min(screen[2][1], screen[3][1]));
            double maxX = Math.max(Math.max(screen[0][0], screen[1][0]), Math.max(screen[2][0], screen[3][0]));
            double maxY = Math.max(Math.max(screen[0][1], screen[1][1]), Math.max(screen[2][1], screen[3][1]));
            boolean hovering = RenderSystem.isHovered(mouseX, mouseY, minX, minY, maxX - minX, maxY - minY);
            if (hovering) {
                Render.colorQuad(RenderContext.graphics(),
                        (float) screen[0][0], (float) screen[0][1],
                        (float) screen[1][0], (float) screen[1][1],
                        (float) screen[2][0], (float) screen[2][1],
                        (float) screen[3][0], (float) screen[3][1], RGBA.white(alpha * 0.12f));
            }
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
