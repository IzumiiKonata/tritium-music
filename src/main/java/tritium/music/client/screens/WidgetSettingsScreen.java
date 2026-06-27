package tritium.music.client.screens;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import tritium.music.client.config.WidgetConfig;
import tritium.music.client.rendering.RGBA;
import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.screens.widget.ColorPickerWidget;
import tritium.music.client.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public class WidgetSettingsScreen extends BaseScreen {

    private final List<Row> rows = new ArrayList<>();
    private ColorPickerWidget glowPicker;
    private ColorPickerWidget rectPicker;

    private double panelX, panelY, panelW, panelH;

    public static void open() {
        Minecraft.getInstance().setScreenAndShow(new WidgetSettingsScreen());
    }

    @Override
    protected void init() {
        WidgetConfig.Lyrics lyrics = WidgetConfig.get().lyrics;
        WidgetConfig.Spectrum spectrum = WidgetConfig.get().spectrum;

        rows.clear();

        rows.add(Row.header("歌词 Lyrics"));
        rows.add(Row.cycle("滚动效果", () -> lyrics.scrollEffect.name(),
                () -> lyrics.scrollEffect = next(lyrics.scrollEffect)));
        rows.add(Row.cycle("对齐", () -> lyrics.alignMode.name(),
                () -> lyrics.alignMode = next(lyrics.alignMode)));
        rows.add(Row.toggle("阴影", () -> lyrics.shadow, () -> lyrics.shadow = !lyrics.shadow));
        rows.add(Row.toggle("单行模式", () -> lyrics.singleLine, () -> lyrics.singleLine = !lyrics.singleLine));
        rows.add(Row.toggle("优雅滚动", () -> lyrics.graceScroll, () -> lyrics.graceScroll = !lyrics.graceScroll));
        rows.add(Row.toggle("显示翻译", () -> lyrics.showTranslation, () -> {
            lyrics.showTranslation = !lyrics.showTranslation;
            WidgetConfig.get().applyToState();
        }));
        rows.add(Row.toggle("罗马音", () -> lyrics.showRoman, () -> {
            lyrics.showRoman = !lyrics.showRoman;
            WidgetConfig.get().applyToState();
        }));
        rows.add(Row.toggle("极光-泛光", () -> lyrics.auroraBloom, () -> lyrics.auroraBloom = !lyrics.auroraBloom));
        rows.add(Row.toggle("极光-火花", () -> lyrics.auroraSpark, () -> lyrics.auroraSpark = !lyrics.auroraSpark));
        rows.add(Row.toggle("音频反应", () -> lyrics.audioReactive, () -> lyrics.audioReactive = !lyrics.audioReactive));
        rows.add(Row.slider("行高", 14, 50, () -> lyrics.lyricHeight, v -> lyrics.lyricHeight = v));
        rows.add(Row.slider("宽度", 225, 900, () -> lyrics.width, v -> lyrics.width = (int) v));
        rows.add(Row.slider("高度", 60, 480, () -> lyrics.height, v -> lyrics.height = (int) v));

        rows.add(Row.header("频谱 Spectrum"));
        rows.add(Row.cycle("样式", () -> spectrum.style.name(), () -> spectrum.style = next(spectrum.style)));
        rows.add(Row.toggle("紧凑模式", () -> spectrum.compatMode, () -> spectrum.compatMode = !spectrum.compatMode));
        rows.add(Row.toggle("峰值指示", () -> spectrum.indicator, () -> spectrum.indicator = !spectrum.indicator));
        rows.add(Row.toggle("绝对音量", () -> spectrum.absVol, () -> spectrum.absVol = !spectrum.absVol));
        rows.add(Row.slider("倍率", 0.1, 3.0, () -> spectrum.multiplier, v -> spectrum.multiplier = v));
        rows.add(Row.slider("平滑", 0.0, 0.95, () -> spectrum.smoothing, v -> spectrum.smoothing = v));
        rows.add(Row.slider("频谱倾斜", 0.0, 6.0, () -> spectrum.spectrumTilt, v -> spectrum.spectrumTilt = v));

        glowPicker = new ColorPickerWidget(() -> lyrics.glowColor, c -> lyrics.glowColor = c, false);
        rectPicker = new ColorPickerWidget(() -> spectrum.rectColor, c -> spectrum.rectColor = c, true);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Enum<T>> T next(T value) {
        T[] all = (T[]) value.getClass().getEnumConstants();
        return all[(value.ordinal() + 1) % all.length];
    }

    @Override
    public void drawScreen(double mouseX, double mouseY) {
        double screenW = RenderSystem.getWidth(), screenH = RenderSystem.getHeight();
        Rect.draw(0, 0, screenW, screenH, RGBA.color(0, 0, 0, 160));

        panelW = Math.min(520, screenW - 40);
        panelH = Math.min(screenH - 40, rows.size() * 18 + 160);
        panelX = (screenW - panelW) / 2.0;
        panelY = (screenH - panelH) / 2.0;

        roundedRect(panelX, panelY, panelW, panelH, 8, RGBA.color(24, 24, 28, 245));
        FontManager.pf20bold.drawString("Widget 设置", panelX + 14, panelY + 12, -1);

        double rowX = panelX + 14;
        double rowW = panelW - 28;
        double ry = panelY + 40;

        for (Row row : rows) {
            row.bounds(rowX, ry, rowW);
            row.render(mouseX, mouseY);
            ry += row.isHeader ? 20 : 18;
        }

        double pickerY = ry + 6;
        double pickerH = 70;
        double pickerW = (rowW - 16) / 2.0;

        FontManager.pf14bold.drawString("歌词辉光色", rowX, pickerY - 12, RGBA.color(200, 200, 200, 255));
        FontManager.pf14bold.drawString("频谱颜色", rowX + pickerW + 16, pickerY - 12, RGBA.color(200, 200, 200, 255));
        glowPicker.setBounds(rowX, pickerY, pickerW, pickerH);
        rectPicker.setBounds(rowX + pickerW + 16, pickerY, pickerW, pickerH);
        glowPicker.render(mouseX, mouseY);
        rectPicker.render(mouseX, mouseY);

        FontManager.pf14bold.drawCenteredString("ESC 保存返回", screenW / 2.0, panelY + panelH - 16, RGBA.color(200, 200, 200, 255));
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (glowPicker.mouseClicked(mouseX, mouseY) || rectPicker.mouseClicked(mouseX, mouseY)) {
            return;
        }
        for (Row row : rows) {
            if (row.mouseClicked(mouseX, mouseY)) {
                return;
            }
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int mouseButton) {
        glowPicker.mouseReleased();
        rectPicker.mouseReleased();
        for (Row row : rows) {
            row.dragging = false;
        }
    }

    @Override
    public void onKeyTyped(char typedChar, int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            WidgetConfig.get().save();
            WidgetEditorScreen.open();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void removed() {
        WidgetConfig.get().save();
        super.removed();
    }

    private static final class Row {
        String label;
        boolean isHeader;

        Supplier<String> valueText;
        Runnable onClick;

        boolean isSlider;
        double min, max;
        DoubleSupplier sliderGet;
        DoubleConsumer sliderSet;
        boolean dragging;

        double x, y, w;

        static Row header(String label) {
            Row r = new Row();
            r.label = label;
            r.isHeader = true;
            return r;
        }

        static Row toggle(String label, BooleanSupplier get, Runnable toggle) {
            Row r = new Row();
            r.label = label;
            r.valueText = () -> get.getAsBoolean() ? "开" : "关";
            r.onClick = toggle;
            return r;
        }

        static Row cycle(String label, Supplier<String> value, Runnable advance) {
            Row r = new Row();
            r.label = label;
            r.valueText = value;
            r.onClick = advance;
            return r;
        }

        static Row slider(String label, double min, double max, DoubleSupplier get, DoubleConsumer set) {
            Row r = new Row();
            r.label = label;
            r.isSlider = true;
            r.min = min;
            r.max = max;
            r.sliderGet = get;
            r.sliderSet = set;
            return r;
        }

        void bounds(double x, double y, double w) {
            this.x = x;
            this.y = y;
            this.w = w;
        }

        void render(double mouseX, double mouseY) {
            if (isHeader) {
                FontManager.pf16bold.drawString(label, x, y, RGBA.color(120, 200, 255, 255));
                return;
            }

            FontManager.pf14bold.drawString(label, x, y, -1);

            if (isSlider) {
                if (dragging) {
                    double t = Mth.limit((mouseX - sliderTrackX()) / sliderTrackW(), 0, 1);
                    sliderSet.accept(min + t * (max - min));
                }
                double tx = sliderTrackX(), tw = sliderTrackW();
                double frac = (sliderGet.getAsDouble() - min) / (max - min);
                Rect.draw(tx, y + 4, tw, 2, RGBA.color(255, 255, 255, 60));
                Rect.draw(tx, y + 4, tw * frac, 2, RGBA.color(120, 200, 255, 255));
                Rect.draw(tx + tw * frac - 1.5, y + 1, 3, 8, RGBA.color(255, 255, 255, 255));
                FontManager.pf12.drawString(String.format("%.2f", sliderGet.getAsDouble()), x + w - 34, y, RGBA.color(200, 200, 200, 255));
            } else {
                String v = valueText.get();
                FontManager.pf14bold.drawString(v, x + w - FontManager.pf14bold.getStringWidthD(v), y, RGBA.color(180, 220, 255, 255));
            }
        }

        private double sliderTrackX() {
            return x + w * 0.45;
        }

        private double sliderTrackW() {
            return w * 0.42;
        }

        boolean mouseClicked(double mouseX, double mouseY) {
            if (isHeader) return false;
            if (mouseX < x || mouseX > x + w || mouseY < y - 2 || mouseY > y + 12) return false;

            if (isSlider) {
                dragging = true;
                double t = Mth.limit((mouseX - sliderTrackX()) / sliderTrackW(), 0, 1);
                sliderSet.accept(min + t * (max - min));
            } else if (onClick != null) {
                onClick.run();
            }
            return true;
        }
    }
}
