package tritium.music.client.screens;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import tritium.music.client.config.WidgetConfig;
import tritium.music.client.rendering.RGBA;
import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.hud.HudWidget;
import tritium.music.client.rendering.hud.MusicInfoWidget;
import tritium.music.client.rendering.hud.MusicLyricsWidget;
import tritium.music.client.rendering.hud.MusicSpectrumWidget;
import tritium.music.client.screens.ncm.NCMScreen;

import java.util.List;

public class WidgetEditorScreen extends BaseScreen {

    private final List<HudWidget> widgets = List.of(
            new MusicInfoWidget(),
            new MusicLyricsWidget(),
            new MusicSpectrumWidget()
    );

    private HudWidget dragging = null;
    private double dragOffsetX, dragOffsetY;
    private HudWidget hovered = null;

    private static final double SNAP = 6;

    public static void open() {
        Minecraft.getInstance().setScreenAndShow(new WidgetEditorScreen());
    }

    @Override
    public void drawScreen(double mouseX, double mouseY) {
        double screenW = RenderSystem.getWidth(), screenH = RenderSystem.getHeight();

        Rect.draw(0, 0, screenW, screenH, RGBA.color(0, 0, 0, 140));

        hovered = null;
        for (HudWidget widget : widgets) {
            boolean enabled = widget.isEnabled();
            if (enabled) {
                widget.onRender();
            }

            double wx = widget.getX(), wy = widget.getY();
            double ww = widget.editorWidth();
            double wh = widget.editorHeight();

            boolean over = mouseX >= wx && mouseX <= wx + ww && mouseY >= wy && mouseY <= wy + wh;
            if (over && dragging == null) {
                hovered = widget;
            }

            if (!enabled) {
                Rect.draw(wx, wy, ww, wh, RGBA.color(40, 40, 48, 160));
                FontManager.pf14bold.drawCenteredString(widget.getName() + " (关闭)", wx + ww / 2.0, wy + wh / 2.0 - FontManager.pf14bold.getHeight() / 2.0, RGBA.color(160, 160, 160, 220));
            }

            int outline = (widget == dragging || widget == hovered) ? RGBA.color(120, 200, 255, 255)
                    : (enabled ? RGBA.color(255, 255, 255, 120) : RGBA.color(120, 120, 120, 120));
            RenderSystem.drawOutLine(wx, wy, ww, wh, 1, outline);
            FontManager.pf14bold.drawString(widget.getName(), wx + 2, wy - FontManager.pf14bold.getHeight() - 2, RGBA.color(255, 255, 255, 220));
        }

        if (dragging != null) {
            double nx = mouseX - dragOffsetX;
            double ny = mouseY - dragOffsetY;

            double ww = Math.max(dragging.editorWidth(), 1);
            double wh = Math.max(dragging.editorHeight(), 1);

            nx = snap(nx, ww, screenW);
            ny = snap(ny, wh, screenH);

            dragging.setX(nx);
            dragging.setY(ny);
        }

        String hint = "拖动移动 · 滚轮缩放 · 右键开关 · ESC 保存返回";
        FontManager.pf16bold.drawCenteredString(hint, screenW / 2.0, screenH - 18, RGBA.color(255, 255, 255, 220));

        boolean overSettings = mouseX >= settingsBtnX() && mouseX <= settingsBtnX() + SETTINGS_BTN_W
                && mouseY >= SETTINGS_BTN_Y && mouseY <= SETTINGS_BTN_Y + SETTINGS_BTN_H;
        roundedRect(settingsBtnX(), SETTINGS_BTN_Y, SETTINGS_BTN_W, SETTINGS_BTN_H, 4,
                overSettings ? RGBA.color(120, 200, 255, 230) : RGBA.color(60, 60, 70, 230));
        FontManager.pf16bold.drawCenteredString("设置", settingsBtnX() + SETTINGS_BTN_W / 2.0,
                SETTINGS_BTN_Y + SETTINGS_BTN_H / 2.0 - FontManager.pf16bold.getHeight() / 2.0, -1);
    }

    private static final double SETTINGS_BTN_W = 70, SETTINGS_BTN_H = 22, SETTINGS_BTN_Y = 10;

    private double settingsBtnX() {
        return RenderSystem.getWidth() - SETTINGS_BTN_W - 10;
    }

    private double snap(double pos, double size, double screen) {
        if (Math.abs(pos) <= SNAP) return 0;
        if (Math.abs(pos + size - screen) <= SNAP) return screen - size;
        if (Math.abs(pos + size / 2.0 - screen / 2.0) <= SNAP) return screen / 2.0 - size / 2.0;
        return pos;
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (mouseButton == 0 && mouseX >= settingsBtnX() && mouseX <= settingsBtnX() + SETTINGS_BTN_W
                && mouseY >= SETTINGS_BTN_Y && mouseY <= SETTINGS_BTN_Y + SETTINGS_BTN_H) {
            WidgetConfig.get().save();
            WidgetSettingsScreen.open();
            return;
        }

        HudWidget target = widgetAt(mouseX, mouseY);
        if (target == null) {
            return;
        }

        if (mouseButton == 1) {
            target.setEnabled(!target.isEnabled());
            return;
        }

        if (mouseButton == 0) {
            dragging = target;
            dragOffsetX = mouseX - target.getX();
            dragOffsetY = mouseY - target.getY();
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int mouseButton) {
        dragging = null;
    }

    @Override
    public void mouseScrolled(double mouseX, double mouseY, int dWheel) {
        HudWidget target = widgetAt(mouseX, mouseY);
        if (target == null || dWheel == 0) {
            return;
        }

        double scale = target.settings().scale + Math.signum(dWheel) * 0.05;
        target.settings().scale = Math.max(0.3, Math.min(3.0, scale));
    }

    private HudWidget widgetAt(double mouseX, double mouseY) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            HudWidget widget = widgets.get(i);
            double wx = widget.getX(), wy = widget.getY();
            double ww = Math.max(widget.editorWidth(), 80), wh = Math.max(widget.editorHeight(), 30);
            if (mouseX >= wx && mouseX <= wx + ww && mouseY >= wy && mouseY <= wy + wh) {
                return widget;
            }
        }
        return null;
    }

    @Override
    public void onKeyTyped(char typedChar, int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private void close() {
        WidgetConfig.get().save();
        NCMScreen.open();
    }

    @Override
    public void removed() {
        WidgetConfig.get().save();
        super.removed();
    }
}
