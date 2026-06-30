package tritium.music.client.rendering.hud;

import tritium.music.client.render.Render;
import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.RGBA;
import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.core.CloudMusic;
import tritium.music.core.audio.AudioPlayer;


public class MusicSpectrumWidget extends HudWidget {

    private float[] renderSpectrum = new float[1];
    private float[] renderSpectrumIndicator = new float[1];

    private long[] indicatorTimeStamp = new long[1];

    public enum Style {
        Rect,
        Oscilloscope
    }

    public MusicSpectrumWidget() {
        super("Music Spectrum");
    }

    @Override
    public tritium.music.client.config.WidgetConfig.WidgetSettings settings() {
        return tritium.music.client.config.WidgetConfig.get().musicSpectrum;
    }

    private tritium.music.client.config.WidgetConfig.Spectrum cfg() {
        return tritium.music.client.config.WidgetConfig.get().spectrum;
    }

    @Override
    public void onRender() {
        Style style = cfg().style;

        boolean compatMode = cfg().compatMode;

        if (CloudMusic.player != null) {

            boolean rect = style == Style.Rect;
            boolean oscilloscope = style == Style.Oscilloscope;

            if (compatMode || oscilloscope) {
                this.setWidth(200);
                this.setHeight(80);
            }

            AudioPlayer.waveMode = oscilloscope ? AudioPlayer.WaveMode.Oscilloscope : AudioPlayer.WaveMode.None;
            AudioPlayer.windowTime = (float) cfg().windowTime;
            AudioPlayer.stereo = cfg().stereo;
            AudioPlayer.waveRegionWidth = this.getWidth();
            AudioPlayer.waveRegionHeight = this.getHeight();

            if (compatMode || oscilloscope) {
                this.roundedRect(this.getX(), this.getY(), this.getWidth(), this.getHeight(), 6, 0, 0, 0, 0.4f);
            }

            if (rect) {
                this.updateSpectrum();
                this.drawBars(compatMode);

                if (!compatMode) {
                    this.setWidth(RenderSystem.getWidth());
                    this.setHeight(RenderSystem.getHeight() * 0.33);
                }
            } else if (oscilloscope) {
                this.drawWaveform();
            }
        }
    }

    private void drawWaveform() {
        AudioPlayer player = CloudMusic.player;
        player.doDetections();

        boolean stereo = cfg().stereo;
        double pWidgetHeight = stereo ? this.getHeight() * 0.5 : this.getHeight();

        if (player.spectrumDataLFilled && player.lockL.tryLock()) {
            try {
                drawWaveSub(pWidgetHeight, false, player.waveVertexes, player.waveVertexes.length / 2);
            } finally {
                player.lockL.unlock();
            }
        }

        if (stereo && player.spectrumDataRFilled && player.lockR.tryLock()) {
            try {
                Rect.draw(this.getX() + 4, this.getY() + pWidgetHeight - 0.25, this.getWidth() - 8, 0.5,
                        RGBA.color(255, 255, 255, 160));
                drawWaveSub(pWidgetHeight, true, player.waveRightVertexes, player.waveRightVertexes.length / 2);
            } finally {
                player.lockR.unlock();
            }
        }
    }

    private void drawWaveSub(double pWidgetHeight, boolean secondHalf, float[] vertexes, int vertCount) {
        if (vertexes == null || vertCount < 2) {
            return;
        }

        double startX = this.getX() + 4;
        double topMargin = 17;
        double bottomMargin = 4;
        double oscHeight = pWidgetHeight - topMargin - bottomMargin;
        double centerY = this.getY() + topMargin + oscHeight * 0.5
                + (secondHalf ? pWidgetHeight : 0) - 6;

        float peak = 0f;
        for (int i = 1; i < vertCount * 2; i += 2) {
            peak = Math.max(peak, Math.abs(vertexes[i]));
        }

        float gain = peak > 1e-6f ? (float) (oscHeight * 0.5 / peak) : 1f;

        int color = RGBA.color(0.92f, 0.98f, 1.0f, 0.95f);

        double prevX = startX + vertexes[0];
        double prevY = centerY + vertexes[1] * gain;

        for (int i = 1; i < vertCount; i++) {
            int a = i * 2;
            double x = startX + vertexes[a];
            double y = centerY + vertexes[a + 1] * gain;

            double left = Math.min(prevX, x);
            double top = Math.min(prevY, y);
            double dx = Math.abs(x - prevX);
            double dy = Math.abs(y - prevY);

            if (dx >= dy) {
                Rect.draw(left, (prevY + y) * 0.5 - 0.5, Math.max(1.0, dx), 1.0, color);
            } else {
                Rect.draw((prevX + x) * 0.5 - 0.5, top, 1.0, Math.max(1.0, dy), color);
            }

            prevX = x;
            prevY = y;
        }
    }

    private void updateSpectrum() {
        int n = AudioPlayer.bandValues.length;

        if (renderSpectrum.length != n) {
            renderSpectrum = new float[n];
            renderSpectrumIndicator = new float[n];
            indicatorTimeStamp = new long[n];
        }

        boolean playing = CloudMusic.player.player.isPlaying();
        float smooth = (float) cfg().smoothing;
        float attackFraction = 1.0f + (1.0f - smooth) * 1.4f;
        float decayFraction = 0.07f + (1.0f - smooth) * 1.6f;

        long now = System.currentTimeMillis();
        boolean indicator = cfg().indicator;

        for (int i = 0; i < n; i++) {
            float target = AudioPlayer.bandValues[i];

            if (!Float.isFinite(target) || !playing) {
                target = 0.0f;
            }

            float previous = renderSpectrum[i];
            float current = Interpolations.interpolate(previous, target, target > previous ? attackFraction : decayFraction);
            renderSpectrum[i] = current;

            if (indicator) {
                if (current >= renderSpectrumIndicator[i]) {
                    renderSpectrumIndicator[i] = current;
                    indicatorTimeStamp[i] = now;
                } else if (now - indicatorTimeStamp[i] > 450) {
                    float fallen = Interpolations.interpolate(renderSpectrumIndicator[i], 0.0f, 0.12f);
                    renderSpectrumIndicator[i] = Math.max(fallen, current);
                }
            }
        }
    }

    private void drawBars(boolean compact) {
        int n = renderSpectrum.length;
        if (n == 0) {
            return;
        }

        double pad = 4;
        double regionX, regionW, baseY, maxH;

        if (compact) {
            regionX = this.getX() + pad;
            regionW = this.getWidth() - pad * 2;
            baseY = this.getY() + this.getHeight() - pad;
            maxH = this.getHeight() - pad * 2;
        } else {
            regionX = 0;
            regionW = RenderSystem.getWidth();
            baseY = RenderSystem.getHeight();
            maxH = RenderSystem.getHeight() * 0.33;
        }

        double mult = cfg().multiplier;
        double pitch = regionW / n;
        double barW = /*compact ? Math.max(1.0, pitch * 0.82) : */pitch;

        int rectColor = cfg().rectColor;
        int rgb = rectColor & 0xFFFFFF;
        int a = (rectColor >>> 24) & 0xFF;

        for (int i = 0; i < n; i++) {
            double h = Math.min(maxH, renderSpectrum[i] * maxH * mult);
            if (h <= 0) {
                continue;
            }

            double x0 = regionX + i * pitch + (pitch - barW) * 0.5;
            double top = baseY - h;

            int topAlpha = (int) (a * (1.0 - 0.8 * (h / maxH)));

            RenderSystem.drawGradientRectTopToBottom(x0, top, x0 + barW, baseY, RGBA.color(rgb, topAlpha), RGBA.color(rgb, a));
        }

        if (cfg().indicator) {
            double capH = compact ? 1.0 : 1.5;

            for (int i = 0; i < n; i++) {
                double ph = Math.min(maxH, renderSpectrumIndicator[i] * maxH * mult);
                if (ph <= capH) {
                    continue;
                }

                double x0 = regionX + i * pitch + (pitch - barW) * 0.5;
                double capY = baseY - ph;

                Rect.draw(x0, capY - capH, barW, capH, RGBA.color(rgb, Math.min(255, a + 64)));
            }
        }
    }

}
