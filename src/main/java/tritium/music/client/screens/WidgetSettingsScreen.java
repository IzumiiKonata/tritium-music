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
import tritium.music.client.render.Render;
import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.RGBA;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.hud.HudWidget;
import tritium.music.client.rendering.hud.MusicLyricsWidget;
import tritium.music.client.rendering.hud.MusicSpectrumWidget;
import tritium.music.client.screens.widget.ColorPickerWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public class WidgetSettingsScreen extends Screen {

    private enum Tab {
        LYRICS("桌面歌词", "歌词布局、动画与辉光"),
        SPECTRUM("音乐频谱", "频谱样式、响应与颜色");

        final String title;
        final String description;

        Tab(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    private record Section(String title, String description, int x, int y, int width, int height) {
    }

    private static final int ROW_HEIGHT = 20;
    private static final int ROW_PITCH = 25;
    private static final int CARD_GAP = 14;
    private static final int CARD_PADDING = 14;

    private final Screen parent;
    private final List<Section> sections = new ArrayList<>();

    private Tab tab = Tab.LYRICS;
    private ColorPickerWidget colorPicker;
    private double pickerX;
    private double pickerY;
    private double pickerWidth;
    private double pickerHeight;
    private String pickerLabel;
    private int pageX;
    private int pageWidth;
    private int contentTop;

    public WidgetSettingsScreen() {
        this(null);
    }

    public WidgetSettingsScreen(Screen parent) {
        super(Component.literal("Widget 设置"));
        this.parent = parent;
    }

    public static void open() {
        Minecraft.getInstance().setScreenAndShow(new WidgetSettingsScreen());
    }

    @Override
    protected void init() {
        sections.clear();
        colorPicker = null;
        pickerLabel = null;

        pageWidth = Math.min(760, Math.max(520, width - 32));
        pageX = (width - pageWidth) / 2;
        contentTop = 82;

        int tabWidth = 126;
        int tabGap = 8;
        int tabX = pageX + pageWidth / 2 - tabWidth - tabGap / 2;
        for (Tab value : Tab.values()) {
            Button button = Button.builder(Component.literal(value.title), ignored -> selectTab(value))
                    .bounds(tabX, 43, tabWidth, 24)
                    .build();
            button.active = value != tab;
            addRenderableWidget(button);
            tabX += tabWidth + tabGap;
        }

        if (tab == Tab.LYRICS) {
            buildLyrics();
        } else {
            buildSpectrum();
        }

        addRenderableWidget(Button.builder(Component.literal("恢复此页默认值"), ignored -> resetCurrentTab())
                .bounds(pageX, height - 34, 122, ROW_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.literal("完成"), ignored -> back())
                .bounds(pageX + pageWidth - 82, height - 34, 82, ROW_HEIGHT)
                .build());
    }

    private void buildLyrics() {
        WidgetConfig config = WidgetConfig.get();
        WidgetConfig.Lyrics lyrics = config.lyrics;
        int cardWidth = (pageWidth - CARD_GAP) / 2;
        int cardHeight = Math.min(330, height - contentTop - 54);
        int leftX = pageX;
        int rightX = leftX + cardWidth + CARD_GAP;

        sections.add(new Section("显示与排版", "控制歌词组件的布局和辅助文本", leftX, contentTop, cardWidth, cardHeight));
        sections.add(new Section("动画与视觉", "调整歌词动效、辉光和组件尺寸", rightX, contentTop, cardWidth, cardHeight));

        int controlWidth = cardWidth - CARD_PADDING * 2;
        int y = contentTop + 47;
        addRenderableWidget(onOff(leftX + CARD_PADDING, y, controlWidth, "启用桌面歌词", config.musicLyrics.enabled,
                value -> config.musicLyrics.enabled = value));
        y += ROW_PITCH;
        addRenderableWidget(enumButton(leftX + CARD_PADDING, y, controlWidth, "滚动效果", lyrics.scrollEffect,
                value -> lyrics.scrollEffect = value));
        y += ROW_PITCH;
        addRenderableWidget(enumButton(leftX + CARD_PADDING, y, controlWidth, "文字对齐", lyrics.alignMode,
                value -> lyrics.alignMode = value));
        y += ROW_PITCH;
        addRenderableWidget(onOff(leftX + CARD_PADDING, y, controlWidth, "文字阴影", lyrics.shadow,
                value -> lyrics.shadow = value));
        y += ROW_PITCH;
        addRenderableWidget(onOff(leftX + CARD_PADDING, y, controlWidth, "单行模式", lyrics.singleLine,
                value -> lyrics.singleLine = value));
        y += ROW_PITCH;
        addRenderableWidget(onOff(leftX + CARD_PADDING, y, controlWidth, "优雅滚动", lyrics.graceScroll,
                value -> lyrics.graceScroll = value));
        y += ROW_PITCH;
        addRenderableWidget(onOff(leftX + CARD_PADDING, y, controlWidth, "显示翻译", lyrics.showTranslation, value -> {
            lyrics.showTranslation = value;
            config.applyToState();
        }));
        y += ROW_PITCH;
        addRenderableWidget(onOff(leftX + CARD_PADDING, y, controlWidth, "显示罗马音", lyrics.showRoman, value -> {
            lyrics.showRoman = value;
            config.applyToState();
        }));

        y = contentTop + 47;
        addRenderableWidget(onOff(rightX + CARD_PADDING, y, controlWidth, "极光辉光", lyrics.auroraBloom,
                value -> lyrics.auroraBloom = value));
        y += ROW_PITCH;
        addRenderableWidget(onOff(rightX + CARD_PADDING, y, controlWidth, "极光火花", lyrics.auroraSpark,
                value -> lyrics.auroraSpark = value));
        y += ROW_PITCH;
        addRenderableWidget(onOff(rightX + CARD_PADDING, y, controlWidth, "音频响应", lyrics.audioReactive,
                value -> lyrics.audioReactive = value));
        y += ROW_PITCH;
        addRenderableWidget(slider(rightX + CARD_PADDING, y, controlWidth, "行间距", 14, 50, false,
                () -> lyrics.lyricHeight, value -> lyrics.lyricHeight = value));
        y += ROW_PITCH;
        addRenderableWidget(slider(rightX + CARD_PADDING, y, controlWidth, "组件宽度", 225, 900, true,
                () -> lyrics.width, value -> lyrics.width = (int) Math.round(value)));
        y += ROW_PITCH;
        addRenderableWidget(slider(rightX + CARD_PADDING, y, controlWidth, "组件高度", 60, 480, true,
                () -> lyrics.height, value -> lyrics.height = (int) Math.round(value)));
        y += ROW_PITCH;
        addRenderableWidget(slider(rightX + CARD_PADDING, y, controlWidth, "未唱歌词亮度", 0, 1, false,
                () -> lyrics.auroraUnsungOpacity, value -> lyrics.auroraUnsungOpacity = value));

        colorPicker = new ColorPickerWidget(() -> lyrics.glowColor, value -> lyrics.glowColor = value, false);
        pickerLabel = "歌词辉光颜色";
        pickerX = rightX + CARD_PADDING;
        pickerY = y + 39;
        pickerWidth = Math.min(180, controlWidth);
        pickerHeight = Math.max(54, contentTop + cardHeight - pickerY - CARD_PADDING);
    }

    private void buildSpectrum() {
        WidgetConfig config = WidgetConfig.get();
        WidgetConfig.Spectrum spectrum = config.spectrum;
        int cardWidth = (pageWidth - CARD_GAP) / 2;
        int cardHeight = Math.min(300, height - contentTop - 54);
        int leftX = pageX;
        int rightX = leftX + cardWidth + CARD_GAP;

        sections.add(new Section("显示模式", "选择频谱外观和辅助显示", leftX, contentTop, cardWidth, cardHeight));
        sections.add(new Section("响应与颜色", "调整动态范围、速度和频谱配色", rightX, contentTop, cardWidth, cardHeight));

        int controlWidth = cardWidth - CARD_PADDING * 2;
        int y = contentTop + 47;
        addRenderableWidget(onOff(leftX + CARD_PADDING, y, controlWidth, "启用音乐频谱", config.musicSpectrum.enabled,
                value -> config.musicSpectrum.enabled = value));
        y += ROW_PITCH;
        addRenderableWidget(enumButton(leftX + CARD_PADDING, y, controlWidth, "显示样式", spectrum.style,
                value -> spectrum.style = value));
        y += ROW_PITCH;
        addRenderableWidget(onOff(leftX + CARD_PADDING, y, controlWidth, "紧凑模式", spectrum.compatMode,
                value -> spectrum.compatMode = value));
        y += ROW_PITCH;
        addRenderableWidget(onOff(leftX + CARD_PADDING, y, controlWidth, "峰值指示器", spectrum.indicator,
                value -> spectrum.indicator = value));
        y += ROW_PITCH;
        addRenderableWidget(onOff(leftX + CARD_PADDING, y, controlWidth, "立体声示波器", spectrum.stereo,
                value -> spectrum.stereo = value));
        y += ROW_PITCH;
        addRenderableWidget(onOff(leftX + CARD_PADDING, y, controlWidth, "补偿播放音量", spectrum.absVol,
                value -> spectrum.absVol = value));

        y = contentTop + 47;
        addRenderableWidget(slider(rightX + CARD_PADDING, y, controlWidth, "显示倍率", 0.1, 3.0, false,
                () -> spectrum.multiplier, value -> spectrum.multiplier = value));
        y += ROW_PITCH;
        addRenderableWidget(slider(rightX + CARD_PADDING, y, controlWidth, "平滑程度", 0.0, 0.95, false,
                () -> spectrum.smoothing, value -> spectrum.smoothing = value));
        y += ROW_PITCH;
        addRenderableWidget(slider(rightX + CARD_PADDING, y, controlWidth, "高频倾斜", 0.0, 6.0, false,
                () -> spectrum.spectrumTilt, value -> spectrum.spectrumTilt = value));
        y += ROW_PITCH;
        addRenderableWidget(slider(rightX + CARD_PADDING, y, controlWidth, "示波窗口", 4, 256, false,
                () -> spectrum.windowTime, value -> spectrum.windowTime = value));

        colorPicker = new ColorPickerWidget(() -> spectrum.rectColor, value -> spectrum.rectColor = value, true);
        pickerLabel = "频谱颜色与透明度";
        pickerX = rightX + CARD_PADDING;
        pickerY = y + 39;
        pickerWidth = Math.min(200, controlWidth);
        pickerHeight = Math.max(64, contentTop + cardHeight - pickerY - CARD_PADDING);
    }

    private void selectTab(Tab next) {
        if (tab == next) {
            return;
        }
        tab = next;
        rebuildWidgets();
    }

    private void resetCurrentTab() {
        WidgetConfig config = WidgetConfig.get();
        if (tab == Tab.LYRICS) {
            config.lyrics = new WidgetConfig.Lyrics();
        } else {
            config.spectrum = new WidgetConfig.Spectrum();
        }
        config.applyToState();
        rebuildWidgets();
    }

    private void back() {
        WidgetConfig.get().save();
        Minecraft.getInstance().setScreenAndShow(parent != null ? parent : new WidgetEditorScreen());
    }

    private CycleButton<Boolean> onOff(int x, int y, int width, String label, boolean initial, Consumer<Boolean> setter) {
        return CycleButton.<Boolean>builder(value -> Component.literal(value ? "开启" : "关闭"), initial)
                .withValues(false, true)
                .create(x, y, width, ROW_HEIGHT, Component.literal(label), (button, value) -> setter.accept(value));
    }

    private <E extends Enum<E>> CycleButton<E> enumButton(int x, int y, int width, String label, E initial, Consumer<E> setter) {
        @SuppressWarnings("unchecked")
        E[] values = (E[]) initial.getClass().getEnumConstants();
        return CycleButton.<E>builder(value -> Component.literal(enumName(value)), initial)
                .withValues(values)
                .create(x, y, width, ROW_HEIGHT, Component.literal(label), (button, value) -> setter.accept(value));
    }

    private SettingSlider slider(int x, int y, int width, String label, double min, double max, boolean integer,
                                 DoubleSupplier getter, DoubleConsumer setter) {
        return new SettingSlider(x, y, width, ROW_HEIGHT, label, min, max, integer, getter, setter);
    }

    private String enumName(Enum<?> value) {
        if (value instanceof MusicLyricsWidget.ScrollEffects effect) {
            return switch (effect) {
                case Scroll -> "滚动";
                case FadeIn -> "淡入";
                case SlideIn -> "滑入";
                case Aurora -> "极光";
            };
        }
        if (value instanceof MusicLyricsWidget.AlignMode align) {
            return switch (align) {
                case Left -> "左对齐";
                case Center -> "居中";
                case Right -> "右对齐";
            };
        }
        if (value instanceof MusicSpectrumWidget.Style style) {
            return switch (style) {
                case Rect -> "柱状频谱";
                case Oscilloscope -> "示波器";
            };
        }
        return value.name();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        HudWidget.renderInFrame(graphics, partialTick, this::renderChrome);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (colorPicker != null) {
            double mx = RenderSystem.getMouseX();
            double my = RenderSystem.getMouseY();
            HudWidget.renderInFrame(graphics, partialTick, () -> {
                FontManager.pf14bold.drawString(pickerLabel, pickerX, pickerY - 15, RGBA.color(205, 214, 228, 255));
                colorPicker.setBounds(pickerX, pickerY, pickerWidth, pickerHeight);
                colorPicker.render(mx, my);
            });
        }
    }

    private void renderChrome() {
        double screenWidth = RenderSystem.getWidth();
        double screenHeight = RenderSystem.getHeight();
        Render.rect(RenderContext.graphics(), 0, 0, (float) screenWidth, (float) screenHeight, RGBA.color(9, 11, 17, 245));
        Render.roundedRect(RenderContext.graphics(), pageX - 12, 12, pageWidth + 24, height - 56, 10,
                RGBA.color(20, 24, 34, 245));
        FontManager.pf25bold.drawString("Widget 设置", pageX, 15, RGBA.color(242, 246, 255, 255));
        FontManager.pf14bold.drawString(tab.description, pageX, 15 + FontManager.pf25bold.getHeight() + 2,
                RGBA.color(142, 154, 176, 255));

        for (Section section : sections) {
            Render.roundedRect(RenderContext.graphics(), section.x, section.y, section.width, section.height, 8,
                    RGBA.color(29, 35, 48, 245));
            FontManager.pf18bold.drawString(section.title, section.x + CARD_PADDING, section.y + 11,
                    RGBA.color(235, 241, 252, 255));
            FontManager.pf14bold.drawString(section.description, section.x + CARD_PADDING, section.y + 28,
                    RGBA.color(130, 144, 168, 255));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (colorPicker != null && colorPicker.mouseClicked(RenderSystem.getMouseX(), RenderSystem.getMouseY())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (colorPicker != null) {
            colorPicker.mouseReleased();
        }
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
        private final DoubleConsumer setter;

        SettingSlider(int x, int y, int width, int height, String label, double min, double max,
                      boolean integer, DoubleSupplier getter, DoubleConsumer setter) {
            super(x, y, width, height, Component.empty(), (getter.getAsDouble() - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.integer = integer;
            this.setter = setter;
            updateMessage();
        }

        private double actual() {
            return min + value * (max - min);
        }

        @Override
        protected void updateMessage() {
            double current = actual();
            String formatted = integer ? Integer.toString((int) Math.round(current)) : String.format("%.2f", current);
            setMessage(Component.literal(label + "  " + formatted));
        }

        @Override
        protected void applyValue() {
            setter.accept(actual());
        }
    }
}
