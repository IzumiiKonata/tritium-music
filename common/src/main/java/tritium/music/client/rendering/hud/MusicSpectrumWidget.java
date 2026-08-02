package tritium.music.client.rendering.hud;

import net.minecraft.client.Minecraft;
import tritium.music.client.rendering.RGBA;
import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.screens.WidgetEditorScreen;
import tritium.music.core.CloudMusic;
import tritium.music.core.audio.AudioPlayer;


public class MusicSpectrumWidget extends HudWidget {

    private float[] renderSpectrum = new float[1];
    private float[] renderSpectrumIndicator = new float[1];

    private long[] indicatorTimeStamp = new long[1];

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
        boolean compatMode = cfg().compatMode;
        boolean editorPreview = Minecraft.getInstance().gui.screen() instanceof WidgetEditorScreen;

        if (CloudMusic.player != null) {
            if (compatMode) {
                this.setWidth(200);
                this.setHeight(80);
                this.roundedRect(this.getX(), this.getY(), this.getWidth(), this.getHeight(), 6, 0, 0, 0, 0.4f);
            }

            this.updateSpectrum();
            this.drawBars(compatMode);
            if (!compatMode) {
                this.setWidth(RenderSystem.getWidth());
                this.setHeight(RenderSystem.getHeight() * 0.33);
            }
        } else if (editorPreview) {
            if (compatMode) {
                this.setWidth(200);
                this.setHeight(80);
                this.roundedRect(this.getX(), this.getY(), this.getWidth(), this.getHeight(), 6, 0, 0, 0, 0.4f);
            }

            updateEditorSpectrum();
            drawBars(compatMode);
            if (!compatMode) {
                this.setWidth(RenderSystem.getWidth());
                this.setHeight(RenderSystem.getHeight() * 0.33);
            }
        }
    }

    private void updateEditorSpectrum() {
        int count = cfg().compatMode ? 32 : 96;
        if (renderSpectrum.length != count) {
            renderSpectrum = new float[count];
            renderSpectrumIndicator = new float[count];
            indicatorTimeStamp = new long[count];
        }
        double phase = System.currentTimeMillis() * 0.003;
        for (int index = 0; index < count; index++) {
            float energy = (float) (0.18
                    + Math.abs(Math.sin(index * 0.31 + phase)) * 0.48
                    + Math.abs(Math.sin(index * 0.11 - phase * 0.7)) * 0.22);
            renderSpectrum[index] = Interpolations.interpolate(renderSpectrum[index], Math.min(1, energy), 0.24f);
            renderSpectrumIndicator[index] = Math.max(
                    Interpolations.interpolateLinear(renderSpectrumIndicator[index], 0, 0.08f),
                    renderSpectrum[index]);
        }
    }

    private void updateSpectrum() {
        int n = AudioPlayer.bandValues.length;

        if (renderSpectrum.length != n) {
            renderSpectrum = new float[n];
            renderSpectrumIndicator = new float[n];
            indicatorTimeStamp = new long[n];
        }

        boolean playing = CloudMusic.player.isPlaying();
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
                    float fallen = Interpolations.interpolateLinear(renderSpectrumIndicator[i], 0.0f, 0.08f);
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
