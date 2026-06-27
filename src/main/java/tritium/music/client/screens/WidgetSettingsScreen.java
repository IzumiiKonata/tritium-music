package tritium.music.client.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import tritium.music.client.config.WidgetConfig;
import tritium.music.client.rendering.RGBA;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.hud.HudWidget;
import tritium.music.client.screens.widget.ColorPickerWidget;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public class WidgetSettingsScreen extends Screen {

    private enum Tab {
        LYRICS("歌词"),
        SPECTRUM("频谱");

        final String title;

        Tab(String title) {
            this.title = title;
        }
    }

    private static final int ROW_W = 200;
    private static final int ROW_H = 20;
    private static final int PITCH = 24;
    private static final int COL_GAP = 16;

    private final Screen parent;

    private Tab tab = Tab.LYRICS;

    private ColorPickerWidget glowPicker;
    private ColorPickerWidget rectPicker;
    private double pickerX, pickerY, pickerW, pickerH;
    private String pickerLabel;

    public WidgetSettingsScreen() {
        this(null);
    }

    public WidgetSettingsScreen(Screen parent) {
        super(Component.literal("Widget Settings"));
        this.parent = parent;
    }

    public static void open() {
        Minecraft.getInstance().setScreenAndShow(new WidgetSettingsScreen());
    }

    private void back() {
        WidgetConfig.get().save();
        if (parent != null) {
            Minecraft.getInstance().setScreenAndShow(parent);
        } else {
            WidgetEditorScreen.open();
        }
    }

    private void selectTab(Tab next) {
        this.tab = next;
        this.rebuildWidgets();
    }

    @Override
    protected void init() {
        glowPicker = null;
        rectPicker = null;
        pickerLabel = null;

        int tabY = 16;
        int tabW = 70;
        int tabGap = 6;
        int tabsTotal = Tab.values().length * tabW + (Tab.values().length - 1) * tabGap;
        int tabX = (this.width - tabsTotal) / 2;
        for (Tab t : Tab.values()) {
            Button button = Button.builder(Component.literal(t.title), b -> selectTab(t))
                    .bounds(tabX, tabY, tabW, ROW_H)
                    .build();
            button.active = t != this.tab;
            addRenderableWidget(button);
            tabX += tabW + tabGap;
        }

        int contentTop = tabY + ROW_H + 16;
        int colX = (this.width - ROW_W) / 2;

        if (tab == Tab.LYRICS) {
            buildLyrics(colX, contentTop);
        } else {
            buildSpectrum(colX, contentTop);
        }

        int doneW = 80;
        addRenderableWidget(Button.builder(Component.literal("完成"), b -> back())
                .bounds(this.width - doneW - 12, this.height - ROW_H - 12, doneW, ROW_H)
                .build());
    }

    private void buildLyrics(int x, int top) {
        WidgetConfig.Lyrics lyrics = WidgetConfig.get().lyrics;

        int twoColX2 = x + ROW_W + COL_GAP;
        boolean wide = (twoColX2 + ROW_W) <= (this.width - 12);
        int leftX = wide ? (this.width - (ROW_W * 2 + COL_GAP)) / 2 : x;
        int rightX = leftX + ROW_W + COL_GAP;

        int yL = top;
        int yR = top;

        addRenderableWidget(enumButton(leftX, yL, "滚动效果", lyrics.scrollEffect,
                v -> lyrics.scrollEffect = v));
        yL += PITCH;
        addRenderableWidget(enumButton(leftX, yL, "对齐", lyrics.alignMode,
                v -> lyrics.alignMode = v));
        yL += PITCH;
        addRenderableWidget(onOff(leftX, yL, "阴影", lyrics.shadow, v -> lyrics.shadow = v));
        yL += PITCH;
        addRenderableWidget(onOff(leftX, yL, "单行模式", lyrics.singleLine, v -> lyrics.singleLine = v));
        yL += PITCH;
        addRenderableWidget(onOff(leftX, yL, "优雅滚动", lyrics.graceScroll, v -> lyrics.graceScroll = v));
        yL += PITCH;
        addRenderableWidget(onOff(leftX, yL, "显示翻译", lyrics.showTranslation, v -> {
            lyrics.showTranslation = v;
            WidgetConfig.get().applyToState();
        }));
        yL += PITCH;
        addRenderableWidget(onOff(leftX, yL, "罗马音", lyrics.showRoman, v -> {
            lyrics.showRoman = v;
            WidgetConfig.get().applyToState();
        }));
        yL += PITCH;

        int rightColX = wide ? rightX : leftX;
        if (!wide) yR = yL;

        addRenderableWidget(onOff(rightColX, yR, "极光-泛光", lyrics.auroraBloom, v -> lyrics.auroraBloom = v));
        yR += PITCH;
        addRenderableWidget(onOff(rightColX, yR, "极光-火花", lyrics.auroraSpark, v -> lyrics.auroraSpark = v));
        yR += PITCH;
        addRenderableWidget(onOff(rightColX, yR, "音频反应", lyrics.audioReactive, v -> lyrics.audioReactive = v));
        yR += PITCH;
        addRenderableWidget(new SettingSlider(rightColX, yR, ROW_W, ROW_H, "行高", 14, 50, false,
                () -> lyrics.lyricHeight, v -> lyrics.lyricHeight = v));
        yR += PITCH;
        addRenderableWidget(new SettingSlider(rightColX, yR, ROW_W, ROW_H, "宽度", 225, 900, true,
                () -> lyrics.width, v -> lyrics.width = (int) Math.round(v)));
        yR += PITCH;
        addRenderableWidget(new SettingSlider(rightColX, yR, ROW_W, ROW_H, "高度", 60, 480, true,
                () -> lyrics.height, v -> lyrics.height = (int) Math.round(v)));
        yR += PITCH;
        addRenderableWidget(new SettingSlider(rightColX, yR, ROW_W, ROW_H, "极光未唱不透明度", 0, 1, false,
                () -> lyrics.auroraUnsungOpacity, v -> lyrics.auroraUnsungOpacity = v));
        yR += PITCH;

        glowPicker = new ColorPickerWidget(() -> lyrics.glowColor, c -> lyrics.glowColor = c, false);
        pickerLabel = "歌词辉光色";
        layoutPicker(rightColX, Math.max(yL, yR) + 16);
    }

    private void buildSpectrum(int x, int top) {
        WidgetConfig.Spectrum spectrum = WidgetConfig.get().spectrum;

        boolean wide = ((this.width - (ROW_W * 2 + COL_GAP)) / 2 + ROW_W * 2 + COL_GAP) <= (this.width - 12);
        int leftX = wide ? (this.width - (ROW_W * 2 + COL_GAP)) / 2 : x;
        int rightX = leftX + ROW_W + COL_GAP;

        int yL = top;
        int yR = top;

//        addRenderableWidget(enumButton(leftX, yL, "样式", spectrum.style, v -> spectrum.style = v));
//        yL += PITCH;
        addRenderableWidget(onOff(leftX, yL, "紧凑模式", spectrum.compatMode, v -> spectrum.compatMode = v));
        yL += PITCH;
        addRenderableWidget(onOff(leftX, yL, "峰值指示", spectrum.indicator, v -> spectrum.indicator = v));
        yL += PITCH;
//        addRenderableWidget(onOff(leftX, yL, "立体声", spectrum.stereo, v -> spectrum.stereo = v));
//        yL += PITCH;

        int rightColX = wide ? rightX : leftX;
        if (!wide) yR = yL;

        addRenderableWidget(new SettingSlider(rightColX, yR, ROW_W, ROW_H, "倍率", 0.1, 3.0, false,
                () -> spectrum.multiplier, v -> spectrum.multiplier = v));
        yR += PITCH;
        addRenderableWidget(new SettingSlider(rightColX, yR, ROW_W, ROW_H, "平滑", 0.0, 0.95, false,
                () -> spectrum.smoothing, v -> spectrum.smoothing = v));
        yR += PITCH;
        addRenderableWidget(new SettingSlider(rightColX, yR, ROW_W, ROW_H, "频谱倾斜", 0.0, 6.0, false,
                () -> spectrum.spectrumTilt, v -> spectrum.spectrumTilt = v));
        yR += PITCH;
//        addRenderableWidget(new SettingSlider(rightColX, yR, ROW_W, ROW_H, "窗口时间(ms)", 4, 256, false,
//                () -> spectrum.windowTime, v -> spectrum.windowTime = v));
//        yR += PITCH;

        rectPicker = new ColorPickerWidget(() -> spectrum.rectColor, c -> spectrum.rectColor = c, true);
        pickerLabel = "频谱颜色";
        layoutPicker(rightColX, Math.max(yL, yR) + 16);
    }

    private void layoutPicker(int x, int y) {
        pickerX = x;
        pickerY = y + 12;
        pickerW = 140;
        pickerH = 90;
    }

    private CycleButton<Boolean> onOff(int x, int y, String label, boolean initial, java.util.function.Consumer<Boolean> set) {
        return CycleButton.onOffBuilder(initial)
                .create(x, y, ROW_W, ROW_H, Component.literal(label), (btn, val) -> set.accept(val));
    }

    private <E extends Enum<E>> CycleButton<E> enumButton(int x, int y, String label, E initial, java.util.function.Consumer<E> set) {
        @SuppressWarnings("unchecked")
        E[] values = (E[]) initial.getClass().getEnumConstants();
        return CycleButton.<E>builder(e -> Component.literal(e.name()), initial)
                .withValues(values)
                .create(x, y, ROW_W, ROW_H, Component.literal(label), (btn, val) -> set.accept(val));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        ColorPickerWidget picker = activePicker();
        String label = pickerLabel;
        double px = pickerX, py = pickerY, pw = pickerW, ph = pickerH;
        double mx = RenderSystem.getMouseX();
        double my = RenderSystem.getMouseY();

        HudWidget.renderInFrame(graphics, partialTick, () -> {
            FontManager.pf20bold.drawCenteredString("Widget 设置", RenderSystem.getWidth() / 2.0, 4, -1);
            if (picker != null) {
                FontManager.pf14bold.drawString(label, px, py - 12, RGBA.color(200, 200, 200, 255));
                picker.setBounds(px, py, pw, ph);
                picker.render(mx, my);
            }
        });
    }

    private ColorPickerWidget activePicker() {
        return tab == Tab.LYRICS ? glowPicker : rectPicker;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        ColorPickerWidget picker = activePicker();
        if (picker != null && picker.mouseClicked(RenderSystem.getMouseX(), RenderSystem.getMouseY())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (glowPicker != null) glowPicker.mouseReleased();
        if (rectPicker != null) rectPicker.mouseReleased();
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        back();
    }

    private static final class SettingSlider extends AbstractSliderButton {

        private final String label;
        private final double min;
        private final double max;
        private final boolean integer;
        private final DoubleConsumer set;

        SettingSlider(int x, int y, int width, int height, String label, double min, double max,
                      boolean integer, DoubleSupplier get, DoubleConsumer set) {
            super(x, y, width, height, Component.empty(), (get.getAsDouble() - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.integer = integer;
            this.set = set;
            updateMessage();
        }

        private double actual() {
            return min + value * (max - min);
        }

        @Override
        protected void updateMessage() {
            double v = actual();
            String text = integer
                    ? label + ": " + (int) Math.round(v)
                    : label + ": " + String.format("%.2f", v);
            setMessage(Component.literal(text));
        }

        @Override
        protected void applyValue() {
            set.accept(actual());
        }
    }
}
