package tritium.music.client.rendering.hud;

import tritium.music.client.config.WidgetConfig;
import tritium.music.client.render.Render;
import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.RGBA;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.hud.dance.DanceStyle;
import tritium.music.client.rendering.hud.dance.DanceStyleRegistry;
import tritium.music.core.CloudMusic;
import tritium.music.core.audio.MusicBeatGrid;
import tritium.music.core.audio.MusicBeatTracker;
import tritium.music.fabric.ui.Identifiers;
import tritium.music.platform.TextureHandle;

public class DanceWidget extends HudWidget {

    private double shadowScale = 1;
    private double smoothPulse;

    public DanceWidget() {
        super("tritium-music.ui.widget.music_dance");
    }

    @Override
    public WidgetConfig.WidgetSettings settings() {
        return WidgetConfig.get().musicDance;
    }

    @Override
    public void onRender() {
        WidgetConfig.Dance config = WidgetConfig.get().dance;
        DanceStyle style = DanceStyleRegistry.active();

        if (style == null) {
            setWidth(0);
            setHeight(0);
            return;
        }

        double width = style.frameWidth() * style.scale();
        double height = style.frameHeight() * style.scale();
        setWidth(width);
        setHeight(height);

        if (!style.isLoaded() || style.frameCount() == 0 || width <= 0 || height <= 0) {
            return;
        }

        double x = getX();
        double y = getY();
        double anchorX = x + width * style.anchorX();
        double anchorY = y + height * style.anchorY();

        long position = playbackPosition();
        MusicBeatGrid grid = MusicBeatTracker.currentGrid();
        double pulse = grid == null ? 0 : grid.pulseAt(position, WidgetConfig.get().groove.beatShift);
        smoothPulse = Interpolations.interpolate(smoothPulse, pulse, 0.5f);

        double opacity = config.opacity * style.opacity();
        double pulseScale = 1 + smoothPulse * config.beatPulse;

        TextureHandle frame = style.frame(frameIndex(config, style, position, grid));
        if (frame == null) {
            return;
        }

        var pose = RenderContext.graphics().pose();
        pose.pushMatrix();
        try {
            pose.translate((float) anchorX, (float) anchorY);
            pose.scale((float) pulseScale, (float) pulseScale);
            pose.translate((float) -anchorX, (float) -anchorY);
            Render.texture(RenderContext.graphics(), Identifiers.of(frame), (float) x, (float) y,
                    (float) width, (float) height,
                    config.mirror ? 1f : 0f, 0f, config.mirror ? 0f : 1f, 1f,
                    (float) opacity);
        } finally {
            pose.popMatrix();
        }
    }

    private static int frameIndex(WidgetConfig.Dance config, DanceStyle style, long position, MusicBeatGrid grid) {
        double frameDuration = frameDuration(config, style, grid, position);
        double cycles = position * config.speed * style.speed() / frameDuration;
        return (int) Math.floor(cycles % style.frameCount());
    }

    private static double frameDuration(WidgetConfig.Dance config, DanceStyle style, MusicBeatGrid grid, long position) {
        if (config.frameDurationMs > 0) {
            return config.frameDurationMs;
        }
        if (config.beatSync && grid != null && grid.beatIntervalMillis() > 0 && grid.covers(position)) {
            double cycle = grid.beatIntervalMillis() * style.beatsPerCycle();
            return Math.max(8, cycle / Math.max(1, style.frameCount()));
        }
        return style.frameDurationMs();
    }

    private static long playbackPosition() {
        if (CloudMusic.player == null) {
            return System.currentTimeMillis() % 600_000L;
        }
        return Math.max(0, (long) CloudMusic.player.getCurrentTimeMillis());
    }
}
