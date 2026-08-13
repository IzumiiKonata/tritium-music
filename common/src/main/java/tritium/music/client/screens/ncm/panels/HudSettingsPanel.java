package tritium.music.client.screens.ncm.panels;

import net.minecraft.client.resources.language.I18n;
import tritium.music.client.config.WidgetConfig;
import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.hud.MusicLyricsWidget;
import tritium.music.client.rendering.ui.AbstractWidget;
import tritium.music.client.rendering.ui.container.Panel;
import tritium.music.client.rendering.ui.container.ScrollPanel;
import tritium.music.client.rendering.ui.widgets.*;
import tritium.music.client.screens.WidgetEditorScreen;
import tritium.music.client.screens.ncm.NCMPanel;
import tritium.music.client.screens.ncm.NCMScreen;
import tritium.music.client.screens.widget.ColorPickerWidget;
import tritium.music.core.model.Quality;

import java.awt.*;
import java.util.function.*;

public class HudSettingsPanel extends NCMPanel {

    private final ScrollPanel content = new ScrollPanel();
    private Page page = Page.GENERAL;

    @Override
    public void onInit() {
        getChildren().clear();

        LabelWidget title = new LabelWidget(text("title"), FontManager.pf25bold);
        title.setColor(getColor(NCMScreen.ColorType.PRIMARY_TEXT));
        title.setBeforeRenderCallback(() -> title.setPosition(24, 22));
        addChild(title);

        LabelWidget subtitle = new LabelWidget(text("subtitle"), FontManager.pf12);
        subtitle.setColor(getColor(NCMScreen.ColorType.SECONDARY_TEXT));
        subtitle.setBeforeRenderCallback(() -> {
            double titleHeight = FontManager.pf25bold.getStringHeight(title.getLabel());
            subtitle.setPosition(24, 22 + titleHeight + 5);
        });
        addChild(subtitle);

        double tabWidth = 82;
        double tabSpacing = 6;
        RoundedButtonWidget layoutTab = new RoundedButtonWidget(text("layout"), FontManager.pf14bold);
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
            RoundedButtonWidget tab = new RoundedButtonWidget(target.label(), FontManager.pf14bold);
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

        RoundedButtonWidget reset = new RoundedButtonWidget(text("reset_page"), FontManager.pf14bold);
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
        content.addChild(new SectionRow(text("section.playback")));
        content.addChild(row(
                text("quality.title"),
                text("quality.description"),
                dropdown(
                        () -> config.quality,
                        value -> config.quality = value,
                        Quality.values(),
                        HudSettingsPanel::qualityName)));

        content.addChild(new SectionRow(text("section.music_info")));
        content.addChild(row(
                text("visible.title"),
                text("music_info.visible.description"),
                toggle(() -> config.musicInfo.enabled, value -> config.musicInfo.enabled = value)));
        content.addChild(row(
                text("scale.title"),
                text("music_info.scale.description"),
                slider(() -> config.musicInfo.scale, value -> config.musicInfo.scale = value, 0.5, 2, 0.05, HudSettingsPanel::percent)));
    }

    private void buildLyricsPage() {
        WidgetConfig config = WidgetConfig.get();
        WidgetConfig.Lyrics lyrics = config.lyrics;

        content.addChild(new SectionRow(text("section.general")));
        content.addChild(row(text("visible.title"), text("lyrics.visible.description"),
                toggle(() -> config.musicLyrics.enabled, value -> config.musicLyrics.enabled = value)));
        content.addChild(row(text("scale.title"), text("lyrics.scale.description"),
                slider(() -> config.musicLyrics.scale, value -> config.musicLyrics.scale = value, 0.5, 2, 0.05, HudSettingsPanel::percent)));
        content.addChild(row(text("lyrics.effect.title"), text("lyrics.effect.description"),
                dropdown(
                        () -> lyrics.scrollEffect,
                        value -> lyrics.scrollEffect = value,
                        MusicLyricsWidget.ScrollEffects.values(),
                        HudSettingsPanel::scrollEffectName)));
        content.addChild(row(text("lyrics.alignment.title"), text("lyrics.alignment.description"),
                dropdown(
                        () -> lyrics.alignMode,
                        value -> lyrics.alignMode = value,
                        MusicLyricsWidget.AlignMode.values(),
                        HudSettingsPanel::alignName)));

        content.addChild(new SectionRow(text("section.content")));
        content.addChild(row(text("lyrics.translation.title"), text("lyrics.translation.description"),
                toggle(() -> lyrics.showTranslation, value -> lyrics.showTranslation = value)));
        content.addChild(row(text("lyrics.romanization.title"), text("lyrics.romanization.description"),
                toggle(() -> lyrics.showRoman, value -> lyrics.showRoman = value)));
        content.addChild(row(text("lyrics.shadow.title"), text("lyrics.shadow.description"),
                toggle(() -> lyrics.shadow, value -> lyrics.shadow = value)));
        content.addChild(row(text("lyrics.single_line.title"), text("lyrics.single_line.description"),
                toggle(() -> lyrics.singleLine, value -> lyrics.singleLine = value)));
        content.addChild(row(text("lyrics.smooth_scroll.title"), text("lyrics.smooth_scroll.description"),
                toggle(() -> lyrics.graceScroll, value -> lyrics.graceScroll = value)));

        content.addChild(new SectionRow(text("section.size")));
        content.addChild(row(text("lyrics.font_size.title"), text("lyrics.font_size.description"),
                slider(() -> lyrics.lyricHeight, value -> lyrics.lyricHeight = value, 12, 40, 1, HudSettingsPanel::pixels)));
        content.addChild(row(text("lyrics.width.title"), text("lyrics.width.description"),
                slider(() -> lyrics.width, value -> lyrics.width = (int) value, 220, 900, 10, HudSettingsPanel::pixels)));
        content.addChild(row(text("lyrics.height.title"), text("lyrics.height.description"),
                slider(() -> lyrics.height, value -> lyrics.height = (int) value, 60, 300, 5, HudSettingsPanel::pixels)));

        content.addChild(new SectionRow(text("section.aurora")));
        content.addChild(row(text("lyrics.aurora_bloom.title"), text("lyrics.aurora_bloom.description"),
                toggle(() -> lyrics.auroraBloom, value -> lyrics.auroraBloom = value)));
        content.addChild(row(text("lyrics.audio_reactive.title"), text("lyrics.audio_reactive.description"),
                toggle(() -> lyrics.audioReactive, value -> lyrics.audioReactive = value)));
        content.addChild(row(text("lyrics.unsung_opacity.title"), text("lyrics.unsung_opacity.description"),
                slider(() -> lyrics.auroraUnsungOpacity, value -> lyrics.auroraUnsungOpacity = value, 0.05, 1, 0.05, HudSettingsPanel::percent)));
        addColorRows(text("lyrics.glow_prefix"), () -> lyrics.glowColor, value -> lyrics.glowColor = value);
    }

    private void buildSpectrumPage() {
        WidgetConfig config = WidgetConfig.get();
        WidgetConfig.Spectrum spectrum = config.spectrum;

        content.addChild(new SectionRow(text("section.general")));
        content.addChild(row(text("visible.title"), text("spectrum.visible.description"),
                toggle(() -> config.musicSpectrum.enabled, value -> config.musicSpectrum.enabled = value)));
        content.addChild(row(text("scale.title"), text("spectrum.scale.description"),
                slider(() -> config.musicSpectrum.scale, value -> config.musicSpectrum.scale = value, 0.5, 2, 0.05, HudSettingsPanel::percent)));
        content.addChild(row(text("spectrum.compact.title"), text("spectrum.compact.description"),
                toggle(() -> spectrum.compatMode, value -> spectrum.compatMode = value)));
        content.addChild(row(text("spectrum.indicator.title"), text("spectrum.indicator.description"),
                toggle(() -> spectrum.indicator, value -> spectrum.indicator = value)));

        content.addChild(new SectionRow(text("section.audio_analysis")));
        content.addChild(row(text("spectrum.multiplier.title"), text("spectrum.multiplier.description"),
                slider(() -> spectrum.multiplier, value -> spectrum.multiplier = value, 0.1, 4, 0.1, value -> format(value, 1) + "×")));
        content.addChild(row(text("spectrum.smoothing.title"), text("spectrum.smoothing.description"),
                slider(() -> spectrum.smoothing, value -> spectrum.smoothing = value, 0, 0.95, 0.05, HudSettingsPanel::percent)));
        content.addChild(row(text("spectrum.tilt.title"), text("spectrum.tilt.description"),
                slider(() -> spectrum.spectrumTilt, value -> spectrum.spectrumTilt = value, 0, 8, 0.25, value -> format(value, 2))));
        content.addChild(row(text("spectrum.absolute_volume.title"), text("spectrum.absolute_volume.description"),
                toggle(() -> spectrum.absVol, value -> spectrum.absVol = value)));

        content.addChild(new SectionRow(text("section.color")));
        content.addChild(row(text("spectrum.color.title"), text("spectrum.color.description"),
                colorPicker(() -> spectrum.rectColor, value -> spectrum.rectColor = value, true)));
    }

    private void addColorRows(String prefix, java.util.function.IntSupplier getter, java.util.function.IntConsumer setter) {
        content.addChild(row(prefix + text("color.hue"), text("color.hue.description"),
                slider(() -> colorComponent(getter.getAsInt(), 0), value -> setColorComponent(getter, setter, 0, value), 0, 1, 0.01, HudSettingsPanel::percent)));
        content.addChild(row(prefix + text("color.saturation"), text("color.saturation.description"),
                slider(() -> colorComponent(getter.getAsInt(), 1), value -> setColorComponent(getter, setter, 1, value), 0, 1, 0.01, HudSettingsPanel::percent)));
        content.addChild(row(prefix + text("color.brightness"), text("color.brightness.description"),
                slider(() -> colorComponent(getter.getAsInt(), 2), value -> setColorComponent(getter, setter, 2, value), 0, 1, 0.01, HudSettingsPanel::percent)));
        content.addChild(row(prefix + text("color.opacity"), text("color.opacity.description"),
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

    private ColorPickerWidget colorPicker(
            java.util.function.IntSupplier getter,
            java.util.function.IntConsumer setter,
            boolean withAlpha) {
        return new ColorPickerWidget(getter, value -> {
            setter.accept(value);
            save();
        }, withAlpha);
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
                config.quality = Quality.STANDARD;
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
            case Scroll -> text("effect.scroll");
            case FadeIn -> text("effect.fade_in");
            case SlideIn -> text("effect.slide_in");
            case Aurora -> text("effect.aurora");
        };
    }

    private static String alignName(MusicLyricsWidget.AlignMode alignMode) {
        return switch (alignMode) {
            case Left -> text("alignment.left");
            case Center -> text("alignment.center");
            case Right -> text("alignment.right");
        };
    }

    private static String qualityName(Quality quality) {
        return switch (quality) {
            case STANDARD -> text("quality.standard");
            case HIGHER -> text("quality.higher");
            case EXHIGH -> text("quality.exhigh");
            case LOSSLESS -> text("quality.lossless");
            case HIRES -> text("quality.hires");
            case JYEFFECT -> text("quality.jyeffect");
            case SKY -> text("quality.sky");
            case JYMASTER -> text("quality.jymaster");
        };
    }

    private static String percent(double value) {
        return Math.round(value * 100) + "%";
    }

    private static String pixels(double value) {
        return I18n.get("tritium-music.ui.unit.pixels", Math.round(value));
    }

    private static String format(double value, int digits) {
        return String.format("%." + digits + "f", value);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum Page {
        GENERAL("page.general"),
        LYRICS("page.lyrics"),
        SPECTRUM("page.spectrum");

        private final String labelKey;

        Page(String labelKey) {
            this.labelKey = labelKey;
        }

        private String label() {
            return text(labelKey);
        }
    }

    private static String text(String key) {
        return I18n.get("tritium-music.ui.settings." + key);
    }

}
