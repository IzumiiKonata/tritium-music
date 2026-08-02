package tritium.music.client.screens.ncm.panels;

import tritium.music.client.config.WidgetConfig;
import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.hud.MusicLyricsWidget;
import tritium.music.client.rendering.ui.AbstractWidget;
import tritium.music.client.rendering.ui.container.Panel;
import tritium.music.client.rendering.ui.container.ScrollPanel;
import tritium.music.client.rendering.ui.widgets.DropdownWidget;
import tritium.music.client.rendering.ui.widgets.LabelWidget;
import tritium.music.client.rendering.ui.widgets.RoundedButtonWidget;
import tritium.music.client.rendering.ui.widgets.SliderWidget;
import tritium.music.client.rendering.ui.widgets.ToggleWidget;
import tritium.music.client.screens.WidgetEditorScreen;
import tritium.music.client.screens.ncm.NCMPanel;
import tritium.music.client.screens.ncm.NCMScreen;

import java.awt.Color;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

public class HudSettingsPanel extends NCMPanel {

    private final ScrollPanel content = new ScrollPanel();
    private Page page = Page.GENERAL;

    @Override
    public void onInit() {
        getChildren().clear();

        LabelWidget title = new LabelWidget("HUD 小组件", FontManager.pf25bold);
        title.setColor(getColor(NCMScreen.ColorType.PRIMARY_TEXT));
        title.setBeforeRenderCallback(() -> title.setPosition(24, 22));
        addChild(title);

        LabelWidget subtitle = new LabelWidget("调整音乐信息、歌词与频谱", FontManager.pf12);
        subtitle.setColor(getColor(NCMScreen.ColorType.SECONDARY_TEXT));
        subtitle.setBeforeRenderCallback(() -> {
            double titleHeight = FontManager.pf25bold.getStringHeight(title.getLabel());
            subtitle.setPosition(24, 22 + titleHeight + 5);
        });
        addChild(subtitle);

        double tabWidth = 82;
        double tabSpacing = 6;
        RoundedButtonWidget layoutTab = new RoundedButtonWidget("布局", FontManager.pf14bold);
        layoutTab.setRadius(5);
        layoutTab.setBounds(tabWidth, 26);
        layoutTab.setBeforeRenderCallback(() -> {
            layoutTab.setPosition(24, 72);
            layoutTab.setColor(getColor(NCMScreen.ColorType.ELEMENT_HOVER));
            layoutTab.setTextColor(getColor(NCMScreen.ColorType.PRIMARY_TEXT));
        });
        layoutTab.setOnClickCallback((x, y, button) -> {
            if (button != 0) {
                return false;
            }
            WidgetEditorScreen.open();
            return true;
        });
        addChild(layoutTab);

        for (int index = 0; index < Page.values().length; index++) {
            Page target = Page.values()[index];
            RoundedButtonWidget tab = new RoundedButtonWidget(target.label, FontManager.pf14bold);
            tab.setRadius(5);
            tab.setBounds(tabWidth, 26);
            int tabIndex = index;
            tab.setBeforeRenderCallback(() -> {
                tab.setPosition(24 + (tabIndex + 1) * (tabWidth + tabSpacing), 72);
                tab.setColor(page == target ? 0xFFC30218 : getColor(NCMScreen.ColorType.ELEMENT_HOVER));
                tab.setTextColor(getColor(NCMScreen.ColorType.PRIMARY_TEXT));
            });
            tab.setOnClickCallback((x, y, button) -> {
                if (button != 0 || page == target) {
                    return false;
                }
                page = target;
                rebuildContent();
                return true;
            });
            addChild(tab);
        }

        RoundedButtonWidget reset = new RoundedButtonWidget("重置本页", FontManager.pf14bold);
        reset.setRadius(5);
        reset.setBounds(88, 26);
        reset.setBeforeRenderCallback(() -> {
            reset.setPosition(reset.getParentWidth() - reset.getWidth() - 24, 72);
            reset.setColor(getColor(NCMScreen.ColorType.ELEMENT_HOVER));
            reset.setTextColor(getColor(NCMScreen.ColorType.PRIMARY_TEXT));
        });
        reset.setOnClickCallback((x, y, button) -> {
            if (button != 0) {
                return false;
            }
            resetPage();
            return true;
        });
        addChild(reset);

        content.setSpacing(3);
        content.setBeforeRenderCallback(() -> content.setBounds(
                24,
                110,
                content.getParentWidth() - 48,
                content.getParentHeight() - 130));
        addChild(content);
        rebuildContent();
    }

    private void rebuildContent() {
        content.getChildren().clear();
        content.actualScrollOffset = 0;
        content.targetScrollOffset = 0;

        switch (page) {
            case GENERAL -> buildGeneralPage();
            case LYRICS -> buildLyricsPage();
            case SPECTRUM -> buildSpectrumPage();
        }
    }

    private void buildGeneralPage() {
        WidgetConfig config = WidgetConfig.get();
        content.addChild(new SectionRow("音乐信息"));
        content.addChild(row(
                "显示组件",
                "显示封面、曲名和播放进度",
                toggle(() -> config.musicInfo.enabled, value -> config.musicInfo.enabled = value)));
        content.addChild(row(
                "组件缩放",
                "调整音乐信息组件的整体尺寸",
                slider(() -> config.musicInfo.scale, value -> config.musicInfo.scale = value, 0.5, 2, 0.05, HudSettingsPanel::percent)));
    }

    private void buildLyricsPage() {
        WidgetConfig config = WidgetConfig.get();
        WidgetConfig.Lyrics lyrics = config.lyrics;

        content.addChild(new SectionRow("常规"));
        content.addChild(row("显示组件", "在 HUD 中显示同步歌词",
                toggle(() -> config.musicLyrics.enabled, value -> config.musicLyrics.enabled = value)));
        content.addChild(row("组件缩放", "调整歌词组件的整体尺寸",
                slider(() -> config.musicLyrics.scale, value -> config.musicLyrics.scale = value, 0.5, 2, 0.05, HudSettingsPanel::percent)));
        content.addChild(row("逐字歌词动效", "选择歌词行进入时的动画",
                dropdown(
                        () -> lyrics.scrollEffect,
                        value -> lyrics.scrollEffect = value,
                        MusicLyricsWidget.ScrollEffects.values(),
                        HudSettingsPanel::scrollEffectName)));
        content.addChild(row("文字对齐", "控制歌词在组件内的排列方向",
                dropdown(
                        () -> lyrics.alignMode,
                        value -> lyrics.alignMode = value,
                        MusicLyricsWidget.AlignMode.values(),
                        HudSettingsPanel::alignName)));

        content.addChild(new SectionRow("内容"));
        content.addChild(row("显示翻译", "存在翻译时显示翻译文本",
                toggle(() -> lyrics.showTranslation, value -> lyrics.showTranslation = value)));
        content.addChild(row("显示罗马音", "存在罗马音时显示读音文本",
                toggle(() -> lyrics.showRoman, value -> lyrics.showRoman = value)));
        content.addChild(row("文字阴影", "为歌词添加阴影以提高可读性",
                toggle(() -> lyrics.shadow, value -> lyrics.shadow = value)));
        content.addChild(row("单行模式", "只显示当前播放的一行歌词",
                toggle(() -> lyrics.singleLine, value -> lyrics.singleLine = value)));
        content.addChild(row("平滑滚动", "切换歌词时使用平滑位移",
                toggle(() -> lyrics.graceScroll, value -> lyrics.graceScroll = value)));

        content.addChild(new SectionRow("尺寸"));
        content.addChild(row("歌词字号", "控制主歌词的基础字号",
                slider(() -> lyrics.lyricHeight, value -> lyrics.lyricHeight = value, 12, 40, 1, HudSettingsPanel::pixels)));
        content.addChild(row("区域宽度", "设置歌词组件的可用宽度",
                slider(() -> lyrics.width, value -> lyrics.width = (int) value, 220, 900, 10, HudSettingsPanel::pixels)));
        content.addChild(row("区域高度", "设置歌词组件的可用高度",
                slider(() -> lyrics.height, value -> lyrics.height = (int) value, 60, 300, 5, HudSettingsPanel::pixels)));

        content.addChild(new SectionRow("极光"));
        content.addChild(row("极光辉光", "显示当前歌词的背景辉光",
                toggle(() -> lyrics.auroraBloom, value -> lyrics.auroraBloom = value)));
        content.addChild(row("音频响应", "让极光强度跟随音乐变化",
                toggle(() -> lyrics.audioReactive, value -> lyrics.audioReactive = value)));
        content.addChild(row("未唱部分亮度", "控制未播放文字的可见度",
                slider(() -> lyrics.auroraUnsungOpacity, value -> lyrics.auroraUnsungOpacity = value, 0.05, 1, 0.05, HudSettingsPanel::percent)));
        addColorRows("辉光", () -> lyrics.glowColor, value -> lyrics.glowColor = value);
    }

    private void buildSpectrumPage() {
        WidgetConfig config = WidgetConfig.get();
        WidgetConfig.Spectrum spectrum = config.spectrum;

        content.addChild(new SectionRow("常规"));
        content.addChild(row("显示组件", "在 HUD 中显示音乐频谱",
                toggle(() -> config.musicSpectrum.enabled, value -> config.musicSpectrum.enabled = value)));
        content.addChild(row("组件缩放", "调整紧凑频谱的整体尺寸",
                slider(() -> config.musicSpectrum.scale, value -> config.musicSpectrum.scale = value, 0.5, 2, 0.05, HudSettingsPanel::percent)));
        content.addChild(row("紧凑模式", "将柱状频谱限制在可移动区域内",
                toggle(() -> spectrum.compatMode, value -> spectrum.compatMode = value)));
        content.addChild(row("峰值指示", "显示频段的短时峰值",
                toggle(() -> spectrum.indicator, value -> spectrum.indicator = value)));

        content.addChild(new SectionRow("音频分析"));
        content.addChild(row("响应强度", "放大或减弱频谱高度",
                slider(() -> spectrum.multiplier, value -> spectrum.multiplier = value, 0.1, 4, 0.1, value -> format(value, 1) + "×")));
        content.addChild(row("平滑程度", "数值越高，频谱变化越平缓",
                slider(() -> spectrum.smoothing, value -> spectrum.smoothing = value, 0, 0.95, 0.05, HudSettingsPanel::percent)));
        content.addChild(row("高频倾斜", "补偿高频能量的显示强度",
                slider(() -> spectrum.spectrumTilt, value -> spectrum.spectrumTilt = value, 0, 8, 0.25, value -> format(value, 2))));
        content.addChild(row("绝对音量", "使用音频绝对幅度驱动频谱",
                toggle(() -> spectrum.absVol, value -> spectrum.absVol = value)));

        content.addChild(new SectionRow("颜色"));
        addColorRows("频谱", () -> spectrum.rectColor, value -> spectrum.rectColor = value);
    }

    private void addColorRows(String prefix, java.util.function.IntSupplier getter, java.util.function.IntConsumer setter) {
        content.addChild(row(prefix + "色相", "调整颜色的色相",
                slider(() -> colorComponent(getter.getAsInt(), 0), value -> setColorComponent(getter, setter, 0, value), 0, 1, 0.01, HudSettingsPanel::percent)));
        content.addChild(row(prefix + "饱和度", "调整颜色的饱和程度",
                slider(() -> colorComponent(getter.getAsInt(), 1), value -> setColorComponent(getter, setter, 1, value), 0, 1, 0.01, HudSettingsPanel::percent)));
        content.addChild(row(prefix + "明度", "调整颜色的亮度",
                slider(() -> colorComponent(getter.getAsInt(), 2), value -> setColorComponent(getter, setter, 2, value), 0, 1, 0.01, HudSettingsPanel::percent)));
        content.addChild(row(prefix + "透明度", "调整颜色的透明度",
                slider(() -> colorComponent(getter.getAsInt(), 3), value -> setColorComponent(getter, setter, 3, value), 0, 1, 0.01, HudSettingsPanel::percent)));
    }

    private SettingRow row(String title, String description, AbstractWidget<?> control) {
        return new SettingRow(title, description, control);
    }

    private ToggleWidget toggle(BooleanSupplier getter, Consumer<Boolean> setter) {
        return new ToggleWidget(getter, value -> {
            setter.accept(value);
            save();
        });
    }

    private SliderWidget slider(
            DoubleSupplier getter,
            DoubleConsumer setter,
            double min,
            double max,
            double step,
            DoubleFunction<String> formatter) {
        return new SliderWidget(getter, value -> {
            setter.accept(value);
            save();
        }, min, max, step, formatter);
    }

    private <T> DropdownWidget<T> dropdown(
            Supplier<T> getter,
            Consumer<T> setter,
            T[] values,
            Function<T, String> formatter) {
        return new DropdownWidget<>(getter, value -> {
            setter.accept(value);
            save();
        }, values, formatter);
    }

    private void resetPage() {
        WidgetConfig config = WidgetConfig.get();
        switch (page) {
            case GENERAL -> {
                config.volume = 0.25;
                config.musicInfo = new WidgetConfig.WidgetSettings(8f / 1920f, 8f / 1080f, 1, true);
            }
            case LYRICS -> {
                config.musicLyrics = new WidgetConfig.WidgetSettings(0.5f - 225f / 1920f, 1f - 140f / 1080f, 1, false);
                config.lyrics = new WidgetConfig.Lyrics();
            }
            case SPECTRUM -> {
                config.musicSpectrum = new WidgetConfig.WidgetSettings(0, 0, 1, false);
                config.spectrum = new WidgetConfig.Spectrum();
            }
        }
        save();
        rebuildContent();
    }

    private static void save() {
        WidgetConfig.get().save();
    }

    private final class SettingRow extends Panel {

        private final LabelWidget title;
        private final LabelWidget description;
        private final AbstractWidget<?> control;
        private float hoverAnimation;

        private SettingRow(String titleText, String descriptionText, AbstractWidget<?> control) {
            this.title = new LabelWidget(titleText, FontManager.pf14bold);
            this.description = new LabelWidget(descriptionText, FontManager.pf12);
            this.control = control;
            setBounds(720, 40);

            title.setColor(HudSettingsPanel.this.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
//            description.setColor(HudSettingsPanel.this.getColor(NCMScreen.ColorType.SECONDARY_TEXT));
            title.setClickable(false);
//            description.setClickable(false);
            addChild(title/*, description*/, control);

            setBeforeRenderCallback(() -> {
                setWidth(getParentWidth());
                setHeight(Math.max(40, 14 + control.getHeight()));
            });
            title.setBeforeRenderCallback(() -> {
                double titleHeight = FontManager.pf14bold.getStringHeight(title.getLabel());
//                double descriptionHeight = FontManager.pf12.getStringHeight(description.getLabel());
                double blockHeight = titleHeight + 2;
                title.setPosition(16, (40 - blockHeight) * 0.5);
            });
//            description.setBeforeRenderCallback(() -> {
//                double titleHeight = FontManager.pf14bold.getStringHeight(title.getLabel());
//                description.setPosition(16, title.getRelativeY() + titleHeight + 2);
//            });
            control.setBeforeRenderCallback(() -> control.setPosition(
                    control.getParentWidth() - control.getWidth() - 16,
                    (40 - Math.min(control.getHeight(), 22)) * 0.5));
        }

        @Override
        public void onRender(double mouseX, double mouseY) {
            boolean hovered = isHovered(mouseX, mouseY, getX(), getY(), getWidth(), getHeight());
            hoverAnimation = Interpolations.interpolate(hoverAnimation, hovered ? 1f : 0f, 0.25f);
            roundedRect(getX(), getY(), getWidth(), getHeight(), 7,
                    reAlpha(HudSettingsPanel.this.getColor(NCMScreen.ColorType.ELEMENT_BACKGROUND), getAlpha()));
            if (hoverAnimation > 0.004f) {
                roundedRect(
                        getX(),
                        getY(),
                        getWidth(),
                        getHeight(),
                        7,
                        reAlpha(
                                HudSettingsPanel.this.getColor(NCMScreen.ColorType.ELEMENT_HOVER),
                                getAlpha() * hoverAnimation));
            }
        }
    }

    private final class SectionRow extends Panel {

        private final String label;

        private SectionRow(String label) {
            this.label = label;
            setBounds(720, 18);
            setClickable(false);
            setBeforeRenderCallback(() -> setWidth(getParentWidth()));
        }

        @Override
        public void onRender(double mouseX, double mouseY) {
            CFontRenderer font = FontManager.pf14bold;
            double textY = getY() + (getHeight() - font.getStringHeight(label)) * 0.5;
            font.drawString(label, getX() + 4, textY,
                    reAlpha(HudSettingsPanel.this.getColor(NCMScreen.ColorType.SECONDARY_TEXT), getAlpha()));
            double textWidth = font.getStringWidthD(label);
            Rect.draw(getX() + textWidth + 16, getY() + getHeight() * 0.5, getWidth() - textWidth - 16, 1,
                    reAlpha(0xFFFFFFFF, getAlpha() * 0.06f));
        }
    }

    private static double colorComponent(int color, int component) {
        Color source = new Color(color, true);
        float[] hsb = Color.RGBtoHSB(source.getRed(), source.getGreen(), source.getBlue(), null);
        return component == 3 ? source.getAlpha() / 255.0 : hsb[component];
    }

    private static void setColorComponent(
            java.util.function.IntSupplier getter,
            java.util.function.IntConsumer setter,
            int component,
            double value) {
        Color source = new Color(getter.getAsInt(), true);
        float[] hsb = Color.RGBtoHSB(source.getRed(), source.getGreen(), source.getBlue(), null);
        int alpha = source.getAlpha();
        if (component < 3) {
            hsb[component] = (float) value;
        } else {
            alpha = (int) Math.round(value * 255);
        }
        int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
        setter.accept((rgb & 0x00FFFFFF) | (alpha << 24));
    }

    private static String scrollEffectName(MusicLyricsWidget.ScrollEffects effect) {
        return switch (effect) {
            case Scroll -> "滚动";
            case FadeIn -> "淡入";
            case SlideIn -> "滑入";
            case Aurora -> "极光";
        };
    }

    private static String alignName(MusicLyricsWidget.AlignMode alignMode) {
        return switch (alignMode) {
            case Left -> "左对齐";
            case Center -> "居中";
            case Right -> "右对齐";
        };
    }

    private static String percent(double value) {
        return Math.round(value * 100) + "%";
    }

    private static String pixels(double value) {
        return Math.round(value) + " px";
    }

    private static String format(double value, int digits) {
        return String.format("%." + digits + "f", value);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum Page {
        GENERAL("通用"),
        LYRICS("歌词"),
        SPECTRUM("频谱");

        private final String label;

        Page(String label) {
            this.label = label;
        }
    }

}
