package tritium.music.client.screens.ncm.panels;

import net.minecraft.client.resources.language.I18n;
import tritium.music.client.config.WidgetConfig;
import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.StencilClipManager;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.hud.DanceWidget;
import tritium.music.client.rendering.hud.HudWidget;
import tritium.music.client.rendering.hud.MusicInfoWidget;
import tritium.music.client.rendering.hud.MusicLyricsWidget;
import tritium.music.client.rendering.hud.MusicSpectrumWidget;
import tritium.music.client.rendering.hud.dance.DanceStyleRegistry;
import tritium.music.client.rendering.ui.AbstractWidget;
import tritium.music.client.rendering.ui.container.Panel;
import tritium.music.client.rendering.ui.container.ScrollPanel;
import tritium.music.client.rendering.ui.widgets.*;
import tritium.music.client.screens.WidgetEditorScreen;
import tritium.music.client.screens.ncm.NCMPanel;
import tritium.music.client.screens.ncm.NCMScreen;
import tritium.music.client.screens.widget.ColorPickerWidget;
import tritium.music.core.audio.AudioPlayer;
import tritium.music.core.audio.AutoMixSupport;
import tritium.music.core.audio.MusicBeatTracker;
import tritium.music.core.model.Quality;
import tritium.music.platform.Platform;

import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.*;

public class HudSettingsPanel extends NCMPanel {

    private static final double ROW_HEIGHT = 24;
    private static final double ROW_INSET = 12;
    private static final double CONTROL_GAP = 12;
    private static final double SECTION_HEIGHT = 16;
    private static final double TAB_WIDTH = 78;
    private static final double TAB_SPACING = 6;
    private static final double TAB_ROW_Y = 52;
    private static final double CONTENT_TOP = 86;
    private static final double PANEL_INSET = 24;
    private static final double PREVIEW_MAX_WIDTH = 360;
    private static final double PREVIEW_MIN_WIDTH = 200;
    private static final double PREVIEW_GAP = 12;
    private static final double CONTENT_MIN_WIDTH = 400;

    private final ScrollPanel content = new ScrollPanel();
    private final FontSettingsPage fontPage = new FontSettingsPage();
    private final PreviewPanel preview = new PreviewPanel();

    private final MusicInfoWidget previewMusicInfo = new MusicInfoWidget();
    private final MusicLyricsWidget previewLyrics = new MusicLyricsWidget();
    private final MusicSpectrumWidget previewSpectrum = new MusicSpectrumWidget();
    private final DanceWidget previewDance = new DanceWidget();

    private Page page = Page.GENERAL;
    private HudWidget previewTarget;
    private HudWidget pagePreview;
    private boolean pageHasPreview;

    @Override
    public void onInit() {
        getChildren().clear();

        LabelWidget title = new LabelWidget(text("title"), FontManager.pf34bold);
        title.setColor(getColor(NCMScreen.ColorType.PRIMARY_TEXT));
        title.setBeforeRenderCallback(() -> title.setPosition(24, 18));
        addChild(title);

        RoundedButtonWidget layoutTab = new RoundedButtonWidget(text("layout"), FontManager.pf14bold);
        layoutTab.setRadius(5);
        layoutTab.setBounds(TAB_WIDTH, 24);
        layoutTab.setBeforeRenderCallback(() -> {
            layoutTab.setPosition(24, TAB_ROW_Y);
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

        List<Page> pages = tabs();
        if (!pages.contains(page)) {
            page = Page.GENERAL;
        }

        for (int index = 0; index < pages.size(); index++) {
            Page target = pages.get(index);
            int tabIndex = index;
            RoundedButtonWidget tab = new RoundedButtonWidget(target.label(), FontManager.pf14bold);
            tab.setRadius(5);
            tab.setBounds(TAB_WIDTH, 24);
            tab.setBeforeRenderCallback(() -> {
                tab.setPosition(24 + (tabIndex + 1) * (TAB_WIDTH + TAB_SPACING), TAB_ROW_Y);
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
        reset.setBounds(84, 24);
        reset.setBeforeRenderCallback(() -> {
            reset.setPosition(reset.getParentWidth() - reset.getWidth() - 24, TAB_ROW_Y);
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

        content.setSpacing(2);
        content.setBeforeRenderCallback(() -> content.setBounds(
                PANEL_INSET,
                CONTENT_TOP,
                content.getParentWidth() - PANEL_INSET * 2 - previewReserve(),
                content.getParentHeight() - CONTENT_TOP - 36));
        addChild(content);

        fontPage.setContentInsets(24, CONTENT_TOP, 24, 32);
        addChild(fontPage);

        preview.setClickable(false);
        preview.setBeforeRenderCallback(() -> preview.setBounds(
                preview.getParentWidth() - PANEL_INSET - previewWidth(),
                CONTENT_TOP,
                previewWidth(),
                Math.max(0, preview.getParentHeight() - CONTENT_TOP - 36)));
        addChild(preview);

        rebuildContent();
    }

    private double previewWidth() {
        if (!pageHasPreview) {
            return 0;
        }
        double available = getWidth() - PANEL_INSET * 2 - PREVIEW_GAP - CONTENT_MIN_WIDTH;
        return available < PREVIEW_MIN_WIDTH ? 0 : Math.min(PREVIEW_MAX_WIDTH, available);
    }

    private double previewReserve() {
        double width = previewWidth();
        return width <= 0 ? 0 : width + PREVIEW_GAP;
    }

    private boolean previewEnabled() {
        return previewWidth() > 0;
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        previewTarget = null;
        if (pageHasPreview) {
            AudioPlayer.spectrumEnabled = true;
            AudioPlayer.spectrumTilt = (float) WidgetConfig.get().spectrum.spectrumTilt;
            AudioPlayer.absoluteVolume = WidgetConfig.get().spectrum.absVol;
        }
    }

    private void rebuildContent() {
        content.getChildren().clear();
        content.actualScrollOffset = 0;
        content.targetScrollOffset = 0;
        pageHasPreview = false;
        pagePreview = null;

        boolean fontPageVisible = page == Page.FONT;
        content.setHidden(fontPageVisible);
        fontPage.setHidden(!fontPageVisible);

        if (fontPageVisible) {
            fontPage.onInit();
            return;
        }

        fontPage.onRemoved();

        switch (page) {
            case GENERAL -> buildGeneralPage();
            case LYRICS -> buildLyricsPage();
            case SPECTRUM -> buildSpectrumPage();
            case DANCE -> buildDancePage();
            case MYSTERY -> buildMysteryPage();
            default -> {
            }
        }
    }

    private static List<Page> tabs() {
        List<Page> pages = new ArrayList<>();
        for (Page candidate : Page.values()) {
            if (candidate == Page.MYSTERY && !chineseLanguage()) {
                continue;
            }
            pages.add(candidate);
        }
        return pages;
    }

    private static boolean chineseLanguage() {
        try {
            return Platform.gameLanguage().toLowerCase(Locale.ROOT).startsWith("zh");
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void onRemoved() {
        fontPage.onRemoved();
    }

    private void buildGeneralPage() {
        WidgetConfig config = WidgetConfig.get();
        WidgetConfig.Groove groove = config.groove;
        boolean autoMixAvailable = AutoMixSupport.isAvailable();
        content.addChild(new SectionRow(text("section.playback")));
        content.addChild(row(
                text("automix.title"),
                autoMixAvailable ? text("automix.description") : autoMixUnavailableDescription(),
                autoMixToggle(autoMixAvailable, () -> config.autoMix, value -> config.autoMix = value),
                null));
        content.addChild(row(
                text("automix.tune_whenever_possible.title"),
                text("automix.tune_whenever_possible.description"),
                autoMixToggle(autoMixAvailable,
                        () -> config.autoMixTuneWheneverPossible,
                        value -> config.autoMixTuneWheneverPossible = value),
                null));
        content.addChild(row(
                text("quality.title"),
                text("quality.description"),
                dropdown(
                        () -> config.quality,
                        value -> config.quality = value,
                        Quality.values(),
                        HudSettingsPanel::qualityName),
                null));

        content.addChild(new SectionRow(text("section.groove")));
        content.addChild(row(text("groove.title"), text("groove.description"),
                toggle(() -> config.songGroove, value -> config.songGroove = value), previewLyrics));
        content.addChild(subRow(text("groove.cover.title"), text("groove.cover.description"),
                toggle(() -> groove.cover, value -> groove.cover = value)));
        content.addChild(subRow(text("groove.cover_strength.title"), text("groove.cover_strength.description"),
                slider(() -> groove.coverStrength, value -> groove.coverStrength = value,
                        WidgetConfig.Groove.MIN_STRENGTH, WidgetConfig.Groove.MAX_STRENGTH, 0.005, HudSettingsPanel::percent)));
        content.addChild(subRow(text("groove.cover_glow.title"), text("groove.cover_glow.description"),
                toggle(() -> groove.coverGlow, value -> groove.coverGlow = value)));
        content.addChild(subRow(text("groove.cover_glow_strength.title"), text("groove.cover_glow_strength.description"),
                slider(() -> groove.coverGlowStrength, value -> groove.coverGlowStrength = value,
                        0.01, 0.3, 0.01, HudSettingsPanel::percent)));
        content.addChild(subRow(text("groove.lyric_lift.title"), text("groove.lyric_lift.description"),
                toggle(() -> groove.lyricLift, value -> groove.lyricLift = value)));
        content.addChild(subRow(text("groove.lyric_lift_strength.title"), text("groove.lyric_lift_strength.description"),
                slider(() -> groove.lyricLiftStrength, value -> groove.lyricLiftStrength = value,
                        0.01, 0.25, 0.01, HudSettingsPanel::percent)));
        content.addChild(subRow(text("groove.aurora.title"), text("groove.aurora.description"),
                toggle(() -> groove.auroraPulse, value -> groove.auroraPulse = value)));
        content.addChild(subRow(text("groove.aurora_strength.title"), text("groove.aurora_strength.description"),
                slider(() -> groove.auroraPulseStrength, value -> groove.auroraPulseStrength = value,
                        0.1, 1, 0.05, HudSettingsPanel::percent)));
        content.addChild(subRow(text("groove.dots.title"), text("groove.dots.description"),
                toggle(() -> groove.breakDots, value -> groove.breakDots = value)));
        content.addChild(subRow(text("groove.dots_strength.title"), text("groove.dots_strength.description"),
                slider(() -> groove.breakDotsStrength, value -> groove.breakDotsStrength = value,
                        0.05, 0.8, 0.05, HudSettingsPanel::percent)));
        content.addChild(subRow(text("groove.beat_shift.title"), text("groove.beat_shift.description"),
                slider(() -> groove.beatShift, value -> groove.beatShift = (int) Math.round(value),
                        WidgetConfig.Groove.MIN_BEAT_SHIFT, WidgetConfig.Groove.MAX_BEAT_SHIFT, 1, HudSettingsPanel::beats)));

        content.addChild(new SectionRow(text("section.music_info")));
        content.addChild(row(
                text("visible.title"),
                text("music_info.visible.description"),
                toggle(() -> config.musicInfo.enabled, value -> config.musicInfo.enabled = value),
                previewMusicInfo));
        content.addChild(row(
                text("scale.title"),
                text("music_info.scale.description"),
                slider(() -> config.musicInfo.scale, value -> config.musicInfo.scale = value, 0.5, 2, 0.05, HudSettingsPanel::percent),
                previewMusicInfo));
    }

    private void buildLyricsPage() {
        WidgetConfig config = WidgetConfig.get();
        WidgetConfig.Lyrics lyrics = config.lyrics;

        content.addChild(new SectionRow(text("section.general")));
        content.addChild(row(text("visible.title"), text("lyrics.visible.description"),
                toggle(() -> config.musicLyrics.enabled, value -> config.musicLyrics.enabled = value), previewLyrics));
        content.addChild(row(text("scale.title"), text("lyrics.scale.description"),
                slider(() -> config.musicLyrics.scale, value -> config.musicLyrics.scale = value, 0.5, 2, 0.05, HudSettingsPanel::percent), previewLyrics));
        content.addChild(row(text("lyrics.effect.title"), text("lyrics.effect.description"),
                dropdown(
                        () -> lyrics.scrollEffect,
                        value -> lyrics.scrollEffect = value,
                        MusicLyricsWidget.ScrollEffects.values(),
                        HudSettingsPanel::scrollEffectName), previewLyrics));
        content.addChild(row(text("lyrics.alignment.title"), text("lyrics.alignment.description"),
                dropdown(
                        () -> lyrics.alignMode,
                        value -> lyrics.alignMode = value,
                        MusicLyricsWidget.AlignMode.values(),
                        HudSettingsPanel::alignName)
                        .setDisabled(value -> value == MusicLyricsWidget.AlignMode.Karaoke && lyrics.singleLine()), previewLyrics));

        content.addChild(new SectionRow(text("section.content")));
        content.addChild(row(text("lyrics.translation.title"), text("lyrics.translation.description"),
                toggle(() -> lyrics.showTranslation, value -> lyrics.showTranslation = value), previewLyrics));
        content.addChild(row(text("lyrics.romanization.title"), text("lyrics.romanization.description"),
                toggle(() -> lyrics.showRoman, value -> lyrics.showRoman = value), previewLyrics));
        content.addChild(row(text("lyrics.shadow.title"), text("lyrics.shadow.description"),
                toggle(() -> lyrics.shadow, value -> lyrics.shadow = value), previewLyrics));
        content.addChild(row(text("lyrics.smooth_scroll.title"), text("lyrics.smooth_scroll.description"),
                toggle(() -> lyrics.graceScroll, value -> lyrics.graceScroll = value), previewLyrics));

        content.addChild(new SectionRow(text("section.size")));
        content.addChild(row(text("lyrics.lines.title"), text("lyrics.lines.description"),
                slider(() -> lyrics.lines, value -> {
                    lyrics.lines = (int) value;
                    lyrics.sanitize();
                }, WidgetConfig.Lyrics.MIN_LINES, WidgetConfig.Lyrics.MAX_LINES, WidgetConfig.Lyrics.LINE_STEP,
                        HudSettingsPanel::lines), previewLyrics));
        content.addChild(row(text("lyrics.font_size.title"), text("lyrics.font_size.description"),
                slider(() -> lyrics.lyricHeight, value -> lyrics.lyricHeight = value, 12, 40, 1, HudSettingsPanel::pixels), previewLyrics));

        content.addChild(new SectionRow(text("section.aurora")));
        content.addChild(row(text("lyrics.aurora_bloom.title"), text("lyrics.aurora_bloom.description"),
                toggle(() -> lyrics.auroraBloom, value -> lyrics.auroraBloom = value), previewLyrics));
        content.addChild(row(text("lyrics.audio_reactive.title"), text("lyrics.audio_reactive.description"),
                toggle(() -> lyrics.audioReactive, value -> lyrics.audioReactive = value), previewLyrics));
        content.addChild(row(text("lyrics.unsung_opacity.title"), text("lyrics.unsung_opacity.description"),
                slider(() -> lyrics.auroraUnsungOpacity, value -> lyrics.auroraUnsungOpacity = value, 0.05, 1, 0.05, HudSettingsPanel::percent), previewLyrics));
        content.addChild(row(text("lyrics.glow_color.title"), text("lyrics.glow_color.description"),
                colorPicker(() -> lyrics.glowColor, value -> lyrics.glowColor = value, true), previewLyrics));
    }

    private void buildSpectrumPage() {
        WidgetConfig config = WidgetConfig.get();
        WidgetConfig.Spectrum spectrum = config.spectrum;

        content.addChild(new SectionRow(text("section.general")));
        content.addChild(row(text("visible.title"), text("spectrum.visible.description"),
                toggle(() -> config.musicSpectrum.enabled, value -> config.musicSpectrum.enabled = value), previewSpectrum));
        content.addChild(row(text("scale.title"), text("spectrum.scale.description"),
                slider(() -> config.musicSpectrum.scale, value -> config.musicSpectrum.scale = value, 0.5, 2, 0.05, HudSettingsPanel::percent), previewSpectrum));
        content.addChild(row(text("spectrum.indicator.title"), text("spectrum.indicator.description"),
                toggle(() -> spectrum.indicator, value -> spectrum.indicator = value), previewSpectrum));

        content.addChild(new SectionRow(text("section.audio_analysis")));
        content.addChild(row(text("spectrum.multiplier.title"), text("spectrum.multiplier.description"),
                slider(() -> spectrum.multiplier, value -> spectrum.multiplier = value, 0.1, 4, 0.1, value -> format(value, 1) + "×"), previewSpectrum));
        content.addChild(row(text("spectrum.smoothing.title"), text("spectrum.smoothing.description"),
                slider(() -> spectrum.smoothing, value -> spectrum.smoothing = value, 0, 0.95, 0.05, HudSettingsPanel::percent), previewSpectrum));
        content.addChild(row(text("spectrum.tilt.title"), text("spectrum.tilt.description"),
                slider(() -> spectrum.spectrumTilt, value -> spectrum.spectrumTilt = value, 0, 8, 0.25, value -> format(value, 2)), previewSpectrum));

        content.addChild(new SectionRow(text("section.color")));
        content.addChild(row(text("spectrum.color.title"), text("spectrum.color.description"),
                colorPicker(() -> spectrum.rectColor, value -> spectrum.rectColor = value, true), previewSpectrum));
    }

    private void buildDancePage() {
        WidgetConfig config = WidgetConfig.get();
        WidgetConfig.Dance dance = config.dance;

        List<DanceStyleRegistry.StyleInfo> styles = DanceStyleRegistry.styles();

        content.addChild(new SectionRow(text("section.dance")));
        content.addChild(row(text("visible.title"), text("dance.visible.description"),
                toggle(() -> config.musicDance.enabled, value -> config.musicDance.enabled = value), previewDance));

        if (styles.isEmpty()) {
            content.addChild(row(text("dance.style.title"), danceEmptyDescription(), beatCacheStyleButton("dance.rescan"), null));
        } else {
            content.addChild(row(text("dance.style.title"), text("dance.style.description"),
                    dropdown(
                            () -> currentStyle(styles),
                            value -> dance.style = value.id(),
                            styles.toArray(new DanceStyleRegistry.StyleInfo[0]),
                            DanceStyleRegistry.StyleInfo::name), previewDance));
        }

        content.addChild(row(text("scale.title"), text("dance.scale.description"),
                slider(() -> config.musicDance.scale, value -> config.musicDance.scale = value, 0.2, 3, 0.05, HudSettingsPanel::percent), previewDance));

        content.addChild(new SectionRow(text("section.dance_animation")));
        content.addChild(row(text("dance.beat_sync.title"), text("dance.beat_sync.description"),
                toggle(() -> dance.beatSync, value -> dance.beatSync = value), previewDance));
        content.addChild(row(text("dance.speed.title"), text("dance.speed.description"),
                slider(() -> dance.speed, value -> dance.speed = value,
                        WidgetConfig.Dance.MIN_SPEED, WidgetConfig.Dance.MAX_SPEED, 0.05, value -> format(value, 2) + "×"), previewDance));
        content.addChild(row(text("dance.pulse.title"), text("dance.pulse.description"),
                slider(() -> dance.beatPulse, value -> dance.beatPulse = value,
                        0, WidgetConfig.Dance.MAX_PULSE, 0.01, HudSettingsPanel::percent), previewDance));
        content.addChild(row(text("dance.shadow.title"), text("dance.shadow.description"),
                toggle(() -> dance.shadow, value -> dance.shadow = value), previewDance));
        content.addChild(row(text("dance.mirror.title"), text("dance.mirror.description"),
                toggle(() -> dance.mirror, value -> dance.mirror = value), previewDance));
        content.addChild(row(text("dance.opacity.title"), text("dance.opacity.description"),
                slider(() -> dance.opacity, value -> dance.opacity = value, 0.1, 1, 0.05, HudSettingsPanel::percent), previewDance));

        content.addChild(new SectionRow(text("section.dance_styles")));
        content.addChild(row(text("dance.style_folder.title"), text("dance.style_folder.description"),
                beatCacheStyleButton("dance.open_folder"), null));
        content.addChild(row(text("dance.rescan.title"), text("dance.rescan.description"),
                beatCacheStyleButton("dance.rescan"), null));
    }

    private static DanceStyleRegistry.StyleInfo currentStyle(List<DanceStyleRegistry.StyleInfo> styles) {
        String configured = WidgetConfig.get().dance.style;
        if (configured != null && !configured.isBlank()) {
            for (DanceStyleRegistry.StyleInfo info : styles) {
                if (info.id().equals(configured)) {
                    return info;
                }
            }
        }
        return styles.get(0);
    }

    private String danceEmptyDescription() {
        return I18n.get("tritium-music.ui.settings.dance.style.empty", DanceStyleRegistry.directory().getAbsolutePath());
    }

    private RoundedButtonWidget beatCacheStyleButton(String key) {
        double width = key.equals("dance.open_folder") ? 96 : 78;
        RoundedButtonWidget button = new RoundedButtonWidget(text(key), FontManager.pf14bold);
        button.setRadius(5);
        button.setBounds(width, 18);
        button.setColor(getColor(NCMScreen.ColorType.ELEMENT_HOVER));
        button.setTextColor(getColor(NCMScreen.ColorType.PRIMARY_TEXT));
        button.setOnClickCallback((x, y, mouseButton) -> {
            if (mouseButton != 0) {
                return false;
            }
            if (key.equals("dance.open_folder")) {
                openDanceFolder();
            } else {
                DanceStyleRegistry.refresh();
                rebuildContent();
            }
            return true;
        });
        return button;
    }

    private void openDanceFolder() {
        File directory = DanceStyleRegistry.directory();
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(directory);
                return;
            }
        } catch (Throwable ignored) {
        }
        Platform.sendChatMessage("§a" + directory.getAbsolutePath());
    }

    private void buildMysteryPage() {
        WidgetConfig config = WidgetConfig.get();

        content.addChild(new SectionRow(text("section.debug")));
        content.addChild(row(text("widget_boundary.title"), text("widget_boundary.description"),
                toggle(() -> config.showWidgetBoundary, value -> config.showWidgetBoundary = value), null));
        content.addChild(row(text("debug_mode.title"), text("debug_mode.description"),
                toggle(() -> config.debugMode, value -> config.debugMode = value), null));
        content.addChild(row(text("groove_info.title"), text("groove_info.description"),
                toggle(() -> config.grooveInfo, value -> config.grooveInfo = value), null));
        content.addChild(row(text("groove_markers.title"), text("groove_markers.description"),
                toggle(() -> config.grooveMarkers, value -> config.grooveMarkers = value), null));

        content.addChild(new SectionRow(text("section.beat_cache")));
        content.addChild(row(text("beat_cache.title"), text("beat_cache.description"), beatCacheButton(), null));
    }

    private RoundedButtonWidget beatCacheButton() {
        RoundedButtonWidget button = new RoundedButtonWidget(
                () -> I18n.get("tritium-music.ui.settings.beat_cache.clear", MusicBeatTracker.cachedEntryCount()),
                FontManager.pf14bold);
        button.setRadius(5);
        button.setBounds(120, 18);
        button.setColor(getColor(NCMScreen.ColorType.ELEMENT_HOVER));
        button.setTextColor(getColor(NCMScreen.ColorType.PRIMARY_TEXT));
        button.setOnClickCallback((x, y, mouseButton) -> {
            if (mouseButton != 0) {
                return false;
            }
            long bytes = MusicBeatTracker.cachedCacheBytes();
            int removed = MusicBeatTracker.clearCache();
            String message = removed == 0
                    ? text("beat_cache.none")
                    : I18n.get("tritium-music.ui.settings.beat_cache.cleared", removed, megabytes(bytes));
            Platform.sendChatMessage("§a" + message);
            return true;
        });
        return button;
    }

    private static String megabytes(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
    }

    private SettingRow row(String title, String description, AbstractWidget<?> control, HudWidget preview) {
        if (preview != null) {
            pageHasPreview = true;
            if (pagePreview == null) {
                pagePreview = preview;
            }
        }
        return new SettingRow(title, description, control, 0, preview);
    }

    private SettingRow subRow(String title, String description, AbstractWidget<?> control) {
        return new SettingRow(title, description, control, 16, null);
    }

    private ToggleWidget toggle(BooleanSupplier getter, Consumer<Boolean> setter) {
        return new ToggleWidget(getter, value -> {
            setter.accept(value);
            save();
        });
    }

    private ToggleWidget autoMixToggle(boolean available, BooleanSupplier getter, Consumer<Boolean> setter) {
        ToggleWidget widget = toggle(getter, value -> {
            if (available) {
                setter.accept(value);
            }
        });
        if (!available) {
            widget.setClickable(false);
            widget.setAlpha(0.4f);
        }
        return widget;
    }

    private String autoMixUnavailableDescription() {
        AutoMixSupport.State state = AutoMixSupport.state();
        String reason = state.reasonKey() == null
                ? text("automix.unavailable.unknown")
                : I18n.get(state.reasonKey(), state.detail() == null || state.detail().isBlank() ? "-" : state.detail());
        return text("automix.description") + "  " + I18n.get("tritium-music.ui.settings.automix.unavailable", reason);
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
                config.autoMix = false;
                config.musicInfo = new WidgetConfig.WidgetSettings(8f / 1920f, 8f / 1080f, 1, true);
            }
            case LYRICS -> {
                config.musicLyrics = new WidgetConfig.WidgetSettings(0.5f - 225f / 1920f, 1f - 140f / 1080f, 1, false);
                config.lyrics = new WidgetConfig.Lyrics();
            }
            case SPECTRUM -> {
                config.musicSpectrum = new WidgetConfig.WidgetSettings(0, 1 - MusicSpectrumWidget.HEIGHT_RATIO, 1, false);
                config.spectrum = new WidgetConfig.Spectrum();
            }
            case DANCE -> {
                config.musicDance = new WidgetConfig.WidgetSettings(0.5f - 102f / 1920f, 1f - 250f / 1080f, 1, false);
                config.dance = new WidgetConfig.Dance();
            }
            case MYSTERY -> {
                config.songGroove = false;
                config.groove = new WidgetConfig.Groove();
                config.showWidgetBoundary = false;
                config.debugMode = false;
                config.grooveInfo = false;
                config.grooveMarkers = false;
            }
            case FONT -> {
                fontPage.resetToDefault();
                return;
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
        private final HudWidget previewWidget;
        private final int indent;
        private float hoverAnimation;

        private SettingRow(String titleText, String descriptionText, AbstractWidget<?> control, int indent,
                           HudWidget previewWidget) {
            this.title = new LabelWidget(titleText, FontManager.pf14bold);
            this.previewWidget = previewWidget;
            this.indent = indent;
            setBounds(720, ROW_HEIGHT);

            title.setColor(HudSettingsPanel.this.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
            title.setClickable(false);
            addChild(title, control);

            setBeforeRenderCallback(() -> {
                double controlHeight = Math.max(control.getHeight(), 18);
                setWidth(getParentWidth());
                setHeight(Math.max(ROW_HEIGHT, controlHeight + 8));
            });
            title.setBeforeRenderCallback(() -> {
                double titleHeight = FontManager.pf14bold.getStringHeight(title.getLabel());
                title.setPosition(ROW_INSET + indent, (getHeight() - titleHeight) * 0.5);
            });
            control.setBeforeRenderCallback(() -> control.setPosition(
                    control.getParentWidth() - control.getWidth() - CONTROL_GAP,
                    (getHeight() - control.getHeight()) * 0.5));
        }

        @Override
        public void onRender(double mouseX, double mouseY) {
            boolean hovered = isHovered(mouseX, mouseY, getX(), getY(), getWidth(), getHeight());
            hoverAnimation = Interpolations.interpolate(hoverAnimation, hovered ? 1f : 0f, 0.25f);
            roundedRect(getX(), getY(), getWidth(), getHeight(), 6,
                    reAlpha(HudSettingsPanel.this.getColor(NCMScreen.ColorType.ELEMENT_BACKGROUND), getAlpha()));
            if (hoverAnimation > 0.004f) {
                roundedRect(getX(), getY(), getWidth(), getHeight(), 6,
                        reAlpha(HudSettingsPanel.this.getColor(NCMScreen.ColorType.ELEMENT_HOVER),
                                getAlpha() * hoverAnimation));
            }
            if (indent > 0) {
                Rect.draw(getX() + indent * 0.5, getY() + getHeight() * 0.5 - 6, 1, 12,
                        reAlpha(0xFFFFFFFF, getAlpha() * 0.08f));
            }
            if (hovered && previewWidget != null) {
                previewTarget = previewWidget;
            }
        }
    }

    private final class SectionRow extends Panel {

        private final String label;

        private SectionRow(String label) {
            this.label = label;
            setBounds(720, SECTION_HEIGHT);
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
            Rect.draw(getX() + textWidth + 14, getY() + getHeight() * 0.5, getWidth() - textWidth - 14, 1,
                    reAlpha(0xFFFFFFFF, getAlpha() * 0.06f));
        }
    }

    private final class PreviewPanel extends Panel {

        private static final double CARD_HEIGHT = 236;
        private static final double HEADER_HEIGHT = 26;
        private static final double INNER_PADDING = 8;

        @Override
        public void onRender(double mouseX, double mouseY) {
            HudWidget target = previewEnabled() ? (previewTarget != null ? previewTarget : pagePreview) : null;
            if (target == null) {
                return;
            }
            double cardHeight = cardHeight();
            if (cardHeight <= HEADER_HEIGHT) {
                return;
            }
            drawCard(getX(), getY(), cardHeight, target);
        }

        private double cardHeight() {
            return Math.min(CARD_HEIGHT, getHeight());
        }

        private void drawCard(double x, double y, double height, HudWidget target) {
            float alpha = getAlpha();
            roundedRect(x - 1, y - 1, getWidth() + 2, height + 2, 10, reAlpha(0xFFFFFFFF, alpha * 0.08f));
            roundedRect(x, y, getWidth(), height, 9, reAlpha(0xF0121316, alpha));

            CFontRenderer font = FontManager.pf14bold;
            font.drawString(target.getName(), x + 12,
                    y + (HEADER_HEIGHT - font.getStringHeight(target.getName())) * 0.5 - 1,
                    reAlpha(0xFFF2F3F5, alpha));
            String label = text("preview");
            font.drawString(label, x + getWidth() - 12 - font.getStringWidthD(label),
                    y + (HEADER_HEIGHT - font.getStringHeight(label)) * 0.5 - 1,
                    reAlpha(HudSettingsPanel.this.getColor(NCMScreen.ColorType.SECONDARY_TEXT), alpha));

            double innerX = x + INNER_PADDING;
            double innerY = y + HEADER_HEIGHT;
            double innerWidth = getWidth() - INNER_PADDING * 2;
            double innerHeight = height - HEADER_HEIGHT - INNER_PADDING;
            if (innerHeight <= 1) {
                return;
            }
            roundedRect(innerX, innerY, innerWidth, innerHeight, 6, reAlpha(0xFF07080A, alpha));
            Rect.draw(innerX, innerY + innerHeight * 0.5, innerWidth, 1, reAlpha(0xFFFFFFFF, alpha * 0.04f));

            double widgetWidth = Math.max(1, target.getWidth() > 1 ? target.getWidth() : 320);
            double widgetHeight = Math.max(1, target.getHeight() > 1 ? target.getHeight() : 120);
            double scale = Math.min(1.0, Math.min(innerWidth / widgetWidth, innerHeight / widgetHeight));
            double centerX = innerX + innerWidth * 0.5;
            double centerY = innerY + innerHeight * 0.5;
            double anchorX = target.getX() + widgetWidth * target.scaleFactor() * 0.5;
            double anchorY = target.getY() + widgetHeight * target.scaleFactor() * 0.5;

            StencilClipManager.beginClip(innerX, innerY, innerWidth, innerHeight);
            var pose = RenderContext.graphics().pose();
            pose.pushPose();
            try {
                pose.translate(centerX, centerY, 0);
                pose.scale((float) scale, (float) scale, 1f);
                pose.translate(-anchorX, -anchorY, 0);
                target.setPreview(true);
                target.render();
            } finally {
                target.setPreview(false);
                pose.popPose();
                StencilClipManager.endClip();
            }
        }
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
            case Karaoke -> text("alignment.karaoke");
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

    private static String lines(double value) {
        return I18n.get("tritium-music.ui.unit.lines", Math.round(value));
    }

    private static String beats(double value) {
        return I18n.get("tritium-music.ui.unit.beats", Math.round(value));
    }

    private static String format(double value, int digits) {
        return String.format("%." + digits + "f", value);
    }

    private enum Page {
        GENERAL("page.general"),
        LYRICS("page.lyrics"),
        SPECTRUM("page.spectrum"),
        DANCE("page.dance"),
        FONT("page.font"),
        MYSTERY("page.mystery");

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
