package tritium.music.client.rendering.hud;

import net.minecraft.client.Minecraft;
import tritium.music.client.rendering.RGBA;
import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.core.CloudMusic;
import tritium.music.core.audio.AudioPlayer;


public class MusicSpectrumWidget extends HudWidget {

    private static final float REFERENCE_SMOOTHING = 0.55f;
    private static final float REFERENCE_RISE_SECONDS = 0.0123f;
    private static final float REFERENCE_FALL_SECONDS = 0.0253f;
    private static final float MAX_SMOOTHING = 1.0f;

    private float[] renderSpectrum = new float[1];
    private float[] renderSpectrumIndicator = new float[1];

    private long[] indicatorTimeStamp = new long[1];

    public MusicSpectrumWidget() {
        super("tritium-music.ui.widget.music_spectrum");
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
        boolean editorPreview = editorOrPreview();

        if (CloudMusic.player != null) {
            this.updateSpectrum();
            this.drawBars();
            this.setWidth(RenderSystem.getWidth());
            this.setHeight(RenderSystem.getHeight() * 0.33);
        } else if (editorPreview) {
            updateEditorSpectrum();
            drawBars();
            this.setWidth(RenderSystem.getWidth());
            this.setHeight(RenderSystem.getHeight() * 0.33);
        }
    }

    private void updateEditorSpectrum() {
        int count = 96;
        if (renderSpectrum.length != count) {
            renderSpectrum = new float[count];
            renderSpectrumIndicator = new float[count];
            indicatorTimeStamp = new long[count];
        }
        float smoothing = smoothing();
        float deltaSeconds = deltaSeconds();
        double phase = System.currentTimeMillis() * 0.003;
        for (int index = 0; index < count; index++) {
            float energy = (float) (0.18 + Math.abs(Math.sin(index * 0.31 + phase)) * 0.48 + Math.abs(Math.sin(index * 0.11 - phase * 0.7)) * 0.22);
            renderSpectrum[index] = easeToward(renderSpectrum[index], Math.min(1, energy), smoothing, deltaSeconds);
            renderSpectrumIndicator[index] = Math.max(Interpolations.interpolateLinear(renderSpectrumIndicator[index], 0, 0.08f), renderSpectrum[index]);
        }
    }

    private void updateSpectrum() {
        float smoothing = smoothing();
        float[] spectrum = smoothing > 0.0f ? AudioPlayer.sampleSpectrum() : AudioPlayer.bandValues;
        int n = spectrum.length;

        if (renderSpectrum.length != n) {
            renderSpectrum = new float[n];
            renderSpectrumIndicator = new float[n];
            indicatorTimeStamp = new long[n];
        }

        boolean playing = CloudMusic.player.isPlaying();
        float deltaSeconds = deltaSeconds();

        long now = System.currentTimeMillis();
        boolean indicator = cfg().indicator;

        for (int i = 0; i < n; i++) {
            float target = spectrum[i];

            if (!Float.isFinite(target) || !playing) {
                target = 0.0f;
            }

            float previous = renderSpectrum[i];
            float current = easeToward(previous, target, smoothing, deltaSeconds);
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

    private float smoothing() {
        return (float) Math.clamp(cfg().smoothing, 0.0, MAX_SMOOTHING);
    }

    private static float deltaSeconds() {
        return (float) (RenderSystem.getFrameDeltaTime() * 0.01);
    }

    private static float easeToward(float from, float to, float smoothing, float deltaSeconds) {
        if (from == to) {
            return to;
        }

        float scale = smoothing / REFERENCE_SMOOTHING;
        float timeConstant = scale * scale * (to > from ? REFERENCE_RISE_SECONDS : REFERENCE_FALL_SECONDS);

        if (timeConstant <= 0.0f) {
            return to;
        }

        return from + (to - from) * (1.0f - (float) Math.exp(-deltaSeconds / timeConstant));
    }

    private void drawBars() {
        int n = renderSpectrum.length;
        if (n == 0) {
            return;
        }

        double regionX, regionW, baseY, maxH;

        regionX = 0;
        regionW = RenderSystem.getWidth();
        baseY = RenderSystem.getHeight();
        maxH = RenderSystem.getHeight() * 0.33;

        double mult = cfg().multiplier;
        double pitch = regionW / n;

        int rectColor = cfg().rectColor;
        int rgb = rectColor & 0xFFFFFF;
        int a = (rectColor >>> 24) & 0xFF;

        for (int i = 0; i < n; i++) {
            double h = Math.min(maxH, renderSpectrum[i] * maxH * mult);
            if (h <= 0) {
                continue;
            }

            double x0 = regionX + i * pitch;
            double top = baseY - h;

            int topAlpha = (int) (a * (1.0 - 0.8 * (h / maxH)));

            RenderSystem.drawGradientRectTopToBottom(x0, top, x0 + pitch, baseY, RGBA.color(rgb, topAlpha), RGBA.color(rgb, a));
        }

        if (cfg().indicator) {
            double capH = 1.5;

            for (int i = 0; i < n; i++) {
                double ph = Math.min(maxH, renderSpectrumIndicator[i] * maxH * mult);
                if (ph <= capH) {
                    continue;
                }

                double x0 = regionX + i * pitch;
                double capY = baseY - ph;

                Rect.draw(x0, capY - capH, pitch, capH, RGBA.color(rgb, Math.min(255, a + 64)));
            }
        }
    }

}
