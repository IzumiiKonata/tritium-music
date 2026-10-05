package tritium.music.client.rendering.hud;

import net.minecraft.client.resources.language.I18n;
import tritium.music.client.config.WidgetConfig;
import tritium.music.client.rendering.RGBA;
import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.SongGroove;
import tritium.music.client.rendering.StencilClipManager;
import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.core.CloudMusic;
import tritium.music.core.audio.MusicBeatGrid;
import tritium.music.core.audio.MusicBeatTracker;

import java.util.Locale;

public class GrooveDebugHud extends HudWidget {

    private static final int WINDOW_BEATS = 10;
    private static final double BOX_LEFT = 8;
    private static final double BOX_TOP = 8;
    private static final double BOX_WIDTH = 360;
    private static final double BOX_GAP = 8;
    private static final double BOX_PADDING = 6;
    private static final double MARKER_BOX_HEIGHT = 112;
    private static final double MARKER_CIRCLE_RADIUS = 7;
    private static final double MARKER_CIRCLE_SPACING = 26;
    private static final double MARKER_CIRCLE_Y = 30;
    private static final double MARKER_TIMELINE_INSET = 10;
    private static final double MARKER_TIMELINE_Y = 74;
    private static final WidgetConfig.WidgetSettings PLACEMENT = new WidgetConfig.WidgetSettings(0, 0, 1, false);

    public GrooveDebugHud() {
        super("tritium-music.ui.widget.groove_debug");
    }

    public static double stackedHeight(WidgetConfig config) {
        if (FontManager.pf14bold == null) {
            return 0;
        }
        double height = 0;
        if (config.grooveInfo) {
            height += (FontManager.pf14bold.getStringHeight("A") + 4) * 7 + 10 + BOX_GAP;
        }
        if (config.grooveMarkers) {
            height += MARKER_BOX_HEIGHT;
        }
        return height;
    }

    @Override
    public WidgetConfig.WidgetSettings settings() {
        return PLACEMENT;
    }

    @Override
    public boolean isEnabled() {
        WidgetConfig config = WidgetConfig.get();
        return config.grooveInfo || config.grooveMarkers;
    }

    @Override
    public void onRender() {
        WidgetConfig config = WidgetConfig.get();
        MusicBeatGrid grid = MusicBeatTracker.currentGrid();
        long position = CloudMusic.player == null ? 0 : (long) CloudMusic.player.getCurrentTimeMillis();

        double top = BOX_TOP;
        if (config.grooveInfo) {
            top = renderInfo(grid, top) + BOX_GAP;
        }
        if (config.grooveMarkers) {
            renderMarkers(grid, position, top);
        }

        this.setWidth(RenderSystem.getWidth());
        this.setHeight(RenderSystem.getHeight());
    }

    private double renderInfo(MusicBeatGrid grid, double top) {
        CFontRenderer font = FontManager.pf14bold;
        double lineHeight = font.getStringHeight("A") + 4;
        int lines = grid == null ? 2 : 7;
        double height = lineHeight * lines + 10;

        Rect.draw(BOX_LEFT, top, BOX_WIDTH, height, RGBA.color(0, 0, 0, 150));

        double x = BOX_LEFT + BOX_PADDING;
        double y = top + BOX_PADDING;

        font.drawString(I18n.get("tritium-music.ui.groove.info.state", stateText(grid)), (float) x, (float) y, -1);
        y += lineHeight;

        if (grid == null) {
            font.drawString(I18n.get("tritium-music.ui.groove.info.hint"), (float) x, (float) y, -1);
            return top + height;
        }

        font.drawString(I18n.get("tritium-music.ui.groove.info.tempo",
                String.format(Locale.ROOT, "%.1f", grid.bpm()),
                grid.beatsPerBar(),
                String.format(Locale.ROOT, "%.2f", grid.confidence() * 100)), (float) x, (float) y, -1);
        y += lineHeight;

        font.drawString(I18n.get("tritium-music.ui.groove.info.phase",
                grid.downbeatPhase(),
                grid.beatIntervalMillis() <= 0 ? "-" : String.format(Locale.ROOT, "%.0f", grid.beatIntervalMillis())), (float) x, (float) y, -1);
        y += lineHeight;

        font.drawString(I18n.get("tritium-music.ui.groove.info.coverage",
                String.format(Locale.ROOT, "%.1fs", grid.coverageEndMillis() / 1000.0),
                grid.beatCount(),
                grid.downbeats().size()), (float) x, (float) y, -1);
        y += lineHeight;

        font.drawString(I18n.get("tritium-music.ui.groove.info.beats",
                grid.beats().isEmpty() ? "-" : grid.beats().getFirst(),
                grid.beats().isEmpty() ? "-" : grid.beats().getLast()), (float) x, (float) y, -1);
        y += lineHeight;

        font.drawString(I18n.get("tritium-music.ui.groove.info.pulse",
                String.format(Locale.ROOT, "%.3f", SongGroove.sample()),
                WidgetConfig.get().groove.beatShift), (float) x, (float) y, -1);
        y += lineHeight;

        font.drawString(I18n.get("tritium-music.ui.groove.info.source", sourceText(), prefetchText()),
                (float) x, (float) y, -1);

        return top + height;
    }

    private static String sourceText() {
        String source = MusicBeatTracker.gridSource();
        if (source == null) {
            return I18n.get("tritium-music.ui.groove.source.none");
        }
        return I18n.get("tritium-music.ui.groove.source." + source);
    }

    private static String prefetchText() {
        long songId = MusicBeatTracker.prefetchSongId();
        if (songId >= 0) {
            return I18n.get("tritium-music.ui.groove.prefetch.running", songId);
        }
        int pending = MusicBeatTracker.prefetchPending();
        if (pending > 0) {
            return I18n.get("tritium-music.ui.groove.prefetch.queued", pending);
        }
        return I18n.get("tritium-music.ui.groove.prefetch.idle");
    }

    private static String stateText(MusicBeatGrid grid) {
        if (CloudMusic.currentlyPlaying == null) {
            return I18n.get("tritium-music.ui.groove.state.idle");
        }
        if (grid != null) {
            return I18n.get("tritium-music.ui.groove.state.ready");
        }
        String rejection = MusicBeatTracker.currentRejection();
        if (rejection != null) {
            String key = "tritium-music.ui.groove.reason." + rejection.replace(' ', '_');
            String translated = I18n.get(key);
            return translated.equals(key) ? rejection : translated;
        }
        return MusicBeatTracker.analyzing()
                ? I18n.get("tritium-music.ui.groove.state.analyzing")
                : I18n.get("tritium-music.ui.groove.state.unavailable");
    }

    private void renderMarkers(MusicBeatGrid grid, long position, double top) {
        Rect.draw(BOX_LEFT, top, BOX_WIDTH, MARKER_BOX_HEIGHT, RGBA.color(0, 0, 0, 150));

        if (grid == null || grid.beatCount() == 0 || grid.beatsPerBar() <= 0) {
            CFontRenderer font = FontManager.pf14bold;
            font.drawString(I18n.get("tritium-music.ui.groove.info.hint"), (float) (BOX_LEFT + BOX_PADDING),
                    (float) (top + BOX_PADDING), -1);
            return;
        }

        int beatsPerBar = grid.beatsPerBar();
        int currentBeat = Math.max(0, grid.beatIndexAt(position));
        int beatInBar = Math.floorMod(currentBeat - grid.downbeatPhase(), beatsPerBar);
        double accent = grid.accentAt(currentBeat);
        double pulse = grid.pulseAt(position, WidgetConfig.get().groove.beatShift);
        boolean onDownbeat = grid.isDownbeat(currentBeat);
        double circleCenterY = top + MARKER_CIRCLE_Y;

        renderBeatCircles(circleCenterY, beatsPerBar, beatInBar);
        drawBeatArrow(BOX_LEFT + BOX_PADDING + MARKER_CIRCLE_RADIUS + beatInBar * MARKER_CIRCLE_SPACING + 6,
                circleCenterY - MARKER_CIRCLE_RADIUS - 2, accent, pulse, onDownbeat);
        renderTimeline(grid, position, top, currentBeat);

        String meter = beatsPerBar + "/4";
        CFontRenderer font = FontManager.pf12bold;
        font.drawString(meter, (float) (BOX_LEFT + BOX_WIDTH - BOX_PADDING - font.getStringWidthD(meter)),
                (float) (circleCenterY - font.getStringHeight(meter) * 0.5), RGBA.color(255, 255, 255, 130));
    }

    private void renderBeatCircles(double centerY, int beatsPerBar, int currentBeatInBar) {
        CFontRenderer font = FontManager.pf12bold;
        double startX = BOX_LEFT + BOX_PADDING + MARKER_CIRCLE_RADIUS + 6;

        for (int index = 0; index < beatsPerBar; index++) {
            double centerX = startX + index * MARKER_CIRCLE_SPACING;
            boolean current = index == currentBeatInBar;
            boolean downbeat = index == 0;
            double radius = current ? MARKER_CIRCLE_RADIUS + 1.5 : MARKER_CIRCLE_RADIUS;

            int fill;
            int textColor;
            if (current) {
                fill = downbeat ? RGBA.color(255, 96, 96, 235) : RGBA.color(120, 200, 255, 235);
                textColor = RGBA.color(12, 12, 16, 235);
            } else {
                fill = downbeat ? RGBA.color(255, 96, 96, 46) : RGBA.color(255, 255, 255, 26);
                textColor = RGBA.color(255, 255, 255, downbeat ? 190 : 140);
            }

            roundedRect(centerX - radius, centerY - radius, radius * 2, radius * 2, radius - 1, fill);

            String label = String.valueOf(index + 1);
            font.drawCenteredString(label, centerX, centerY - font.getStringHeight(label) * 0.5, textColor);
        }
    }

    private void drawBeatArrow(double centerX, double tipY, double accent, double pulse, boolean downbeat) {
        CFontRenderer font = FontManager.pf28bold;
        String arrow = "↓";
        double strength = Math.min(1.0, 0.25 + 0.75 * Math.max(accent, pulse));
//        double scale = 0.85 + 0.8 * strength;
        double x = centerX - font.getStringWidthD(arrow) * 0.5;
        double y = tipY - font.getStringHeight(arrow) - 2;
        int color = downbeat
                ? RGBA.color(255, 96, 96, (int) (170 + 85 * strength))
                : RGBA.color(255, 255, 255, (int) (170 + 85 * strength));

//        scaleAtPos(centerX, tipY, scale);
        font.drawString(arrow, (float) x, (float) y, color);
    }

    private void renderTimeline(MusicBeatGrid grid, long position, double top, int currentBeat) {
        double innerLeft = BOX_LEFT + MARKER_TIMELINE_INSET;
        double innerWidth = BOX_WIDTH - MARKER_TIMELINE_INSET * 2;
        double centerX = BOX_LEFT + BOX_WIDTH * 0.5;
        double lineY = top + MARKER_TIMELINE_Y;
        double pixelsPerMillis = grid.beatIntervalMillis() > 0
                ? innerWidth / (WINDOW_BEATS * grid.beatIntervalMillis())
                : 0.24;

        int from = Math.max(0, currentBeat - WINDOW_BEATS / 2);
        int to = Math.min(grid.beatCount(), from + WINDOW_BEATS);

        StencilClipManager.beginClip(BOX_LEFT, top, BOX_WIDTH, MARKER_BOX_HEIGHT);
        try {
            Rect.draw(innerLeft, lineY, innerWidth, 1, RGBA.color(255, 255, 255, 28));

            for (int index = from; index < to; index++) {
                double deltaMillis = grid.beatTime(index) - position;
                double x = centerX + deltaMillis * pixelsPerMillis;
                boolean downbeat = grid.isDownbeat(index);
                double height = (downbeat ? 22 : 10) + grid.accentAt(index) * 26;
                double closeness = Math.max(0, 1 - Math.abs(deltaMillis) / 700.0);
                double alpha = Math.max(0.15, Math.min(1, 0.45 + closeness * 0.55));
                Rect.draw(x, lineY - height * 0.5, downbeat ? 3 : 2, height,
                        downbeat ? RGBA.color(255, 96, 96, (int) (255 * alpha)) : RGBA.color(255, 255, 255, (int) (200 * alpha)));
            }

            double pulse = SongGroove.sample();
            if (pulse > 0.01) {
                double size = 4 + pulse * 12;
                Rect.draw(centerX - size * 0.5, lineY - size * 0.5, size, size, RGBA.color(120, 200, 255, 180));
            }
        } finally {
            StencilClipManager.endClip();
        }
    }
}
