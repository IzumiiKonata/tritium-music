package tritium.music.client.rendering.hud;

import net.minecraft.client.resources.language.I18n;
import tritium.music.client.config.WidgetConfig;
import tritium.music.client.rendering.RGBA;
import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.SongGroove;
import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.core.CloudMusic;
import tritium.music.core.audio.MusicBeatGrid;
import tritium.music.core.audio.MusicBeatTracker;

import java.util.Locale;

public class GrooveDebugHud extends HudWidget {

    private static final int WINDOW_BEATS = 10;
    private static final WidgetConfig.WidgetSettings PLACEMENT = new WidgetConfig.WidgetSettings(0, 0, 1, false);

    public GrooveDebugHud() {
        super("tritium-music.ui.widget.groove_debug");
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

        if (config.grooveInfo) {
            renderInfo(grid, position);
        }
        if (config.grooveMarkers) {
            renderMarkers(grid, position);
        }

        this.setWidth(RenderSystem.getWidth());
        this.setHeight(RenderSystem.getHeight());
    }

    private void renderInfo(MusicBeatGrid grid, long position) {
        CFontRenderer font = FontManager.pf14bold;
        double x = 14;
        double y = 14;
        double lineHeight = font.getStringHeight("A") + 4;
        int lines = grid == null ? 2 : 7;

        Rect.draw(x - 6, y - 6, 360, lineHeight * lines + 10, RGBA.color(0, 0, 0, 150));

        font.drawString(I18n.get("tritium-music.ui.groove.info.state", stateText(grid)), (float) x, (float) y, -1);
        y += lineHeight;

        if (grid == null) {
            font.drawString(I18n.get("tritium-music.ui.groove.info.hint"), (float) x, (float) y, -1);
            return;
        }

        font.drawString(I18n.get("tritium-music.ui.groove.info.tempo",
                String.format(Locale.ROOT, "%.1f", grid.bpm()),
                grid.beatsPerBar(),
                String.format(Locale.ROOT, "%.2f", grid.confidence())), (float) x, (float) y, -1);
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
                grid.beats().isEmpty() ? "-" : grid.beats().get(0),
                grid.beats().isEmpty() ? "-" : grid.beats().get(grid.beats().size() - 1)), (float) x, (float) y, -1);
        y += lineHeight;

        font.drawString(I18n.get("tritium-music.ui.groove.info.pulse",
                String.format(Locale.ROOT, "%.3f", SongGroove.sample()),
                WidgetConfig.get().groove.beatShift), (float) x, (float) y, -1);
        y += lineHeight;

        font.drawString(I18n.get("tritium-music.ui.groove.info.source", sourceText(), prefetchText()),
                (float) x, (float) y, -1);
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

    private void renderMarkers(MusicBeatGrid grid, long position) {
        double width = RenderSystem.getWidth();
        double baseY = RenderSystem.getHeight() * 0.5;
        Rect.draw(0, baseY, width, 1, RGBA.color(255, 255, 255, 28));
        if (grid == null || grid.beatCount() == 0) {
            return;
        }

        double centerX = width * 0.5;
        int current = Math.max(0, grid.beatIndexAt(position));
        int from = Math.max(0, current - WINDOW_BEATS / 2);
        int to = Math.min(grid.beatCount(), from + WINDOW_BEATS);

        for (int index = from; index < to; index++) {
            double deltaMillis = grid.beatTime(index) - position;
            double x = centerX + deltaMillis * 0.24;
            if (x < 40 || x > width - 40) {
                continue;
            }
            boolean downbeat = grid.isDownbeat(index);
            double height = (downbeat ? 30 : 14) + grid.accentAt(index) * 40;
            double closeness = Math.max(0, 1 - Math.abs(deltaMillis) / 700.0);
            double alpha = Math.max(0.15, Math.min(1, 0.45 + closeness * 0.55));
            Rect.draw(x, baseY - height * 0.5, downbeat ? 3 : 2, height,
                    downbeat ? RGBA.color(255, 96, 96, (int) (255 * alpha)) : RGBA.color(255, 255, 255, (int) (200 * alpha)));
        }

        double pulse = SongGroove.sample();
        if (pulse > 0.01) {
            double size = 6 + pulse * 26;
            Rect.draw(centerX - size * 0.5, baseY - size * 0.5, size, size, RGBA.color(120, 200, 255, 180));
        }
    }
}
