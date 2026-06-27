package tritium.music.client.rendering.hud;

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
        Waveform,
        Oscilloscope,
        Line
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
            boolean line = style == Style.Line;
            boolean waveform = style == Style.Waveform;
            boolean oscilloscope = style == Style.Oscilloscope;

            if (compatMode || waveform || oscilloscope) {
                this.setWidth(200);
                this.setHeight(80);
            }

            AudioPlayer.waveMode = waveform ? AudioPlayer.WaveMode.Waveform
                    : oscilloscope ? AudioPlayer.WaveMode.Oscilloscope
                    : AudioPlayer.WaveMode.None;
            AudioPlayer.windowTime = (float) cfg().windowTime;
            AudioPlayer.stereo = cfg().stereo;
            AudioPlayer.waveRegionWidth = this.getWidth();
            AudioPlayer.waveRegionHeight = this.getHeight();

            if (compatMode || waveform || oscilloscope) {
                this.roundedRect(this.getX(), this.getY(), this.getWidth(), this.getHeight(), 6, 0, 0, 0, 0.4f);
            }

            if (rect || line) {
                this.updateSpectrum();
            }

            if (rect) {
                this.drawBars(compatMode);
            }

            if (waveform || oscilloscope) {
                this.drawWaveform();
            }

            if (line) {
                this.drawLine(compatMode);
            }

            if (rect) {
                this.setWidth(-1);
            }
        }
    }

    private static long lastDbg = 0;

    private void drawWaveform() {
        CloudMusic.player.doDetections();

        if (System.currentTimeMillis() - lastDbg > 1000) {
            lastDbg = System.currentTimeMillis();
            AudioPlayer p = CloudMusic.player;
            float vmax = 0;
            if (p.waveVertexes != null) {
                for (int i = 1; i < p.waveVertexes.length; i += 2) vmax = Math.max(vmax, Math.abs(p.waveVertexes[i]));
            }
            System.out.println("[WAVE] enabled=" + AudioPlayer.spectrumEnabled + " mode=" + AudioPlayer.waveMode
                    + " pausing=" + p.isPausing() + " Lfilled=" + p.spectrumDataLFilled
                    + " vlen=" + (p.waveVertexes == null ? -1 : p.waveVertexes.length) + " vmaxY=" + vmax
                    + " region=" + AudioPlayer.waveRegionWidth + "x" + AudioPlayer.waveRegionHeight
                    + " pos=" + this.getX() + "," + this.getY());
        }

        boolean stereo = cfg().stereo;
        double pWidgetHeight = stereo ? this.getHeight() * 0.5 : this.getHeight();

        AudioPlayer player = CloudMusic.player;

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
        double startY = this.getY() + pWidgetHeight * 0.5 + (secondHalf ? pWidgetHeight : 0);

        int color = RGBA.color(0.92f, 0.98f, 1.0f, 0.95f);

        for (int i = 0; i < vertCount - 1; i++) {
            int a = i * 2;
            int b = (i + 1) * 2;
            double x0 = startX + vertexes[a];
            double y0 = startY + vertexes[a + 1];
            double x1 = startX + vertexes[b];
            double y1 = startY + vertexes[b + 1];
            RenderSystem.drawLine(x0, y0, x1, y1, 1.4, color);
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
        double barW = compact ? Math.max(1.0, pitch * 0.82) : pitch;

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

    private void drawLine(boolean compact) {
        int n = renderSpectrum.length;
        if (n < 2) {
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
        double pitch = regionW / (n - 1);

        for (int i = 0; i < n; i++) {
            double h = Math.min(maxH, renderSpectrum[i] * maxH * mult);
            double x = regionX + i * pitch;

            RenderSystem.drawGradientRectTopToBottom(x, baseY - h, x + Math.max(1.0, pitch), baseY,
                    RGBA.white((int) (0.34f * 255)), RGBA.white((int) (0.16f * 255)));
        }

        double lineThickness = compact ? 1.0 : 1.5;

        for (int i = 0; i < n - 1; i++) {
            double h0 = Math.min(maxH, renderSpectrum[i] * maxH * mult);
            double h1 = Math.min(maxH, renderSpectrum[i + 1] * maxH * mult);
            double x0 = regionX + i * pitch;
            double x1 = regionX + (i + 1) * pitch;

            RenderSystem.drawLine(x0, baseY - h0, x1, baseY - h1, lineThickness, RGBA.white((int) (0.9f * 255)));
        }
    }
}
