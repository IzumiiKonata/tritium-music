package tritium.music.client.rendering.hud;

import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.RGBA;
import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.StencilClipManager;
import tritium.music.client.rendering.animation.Easing;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.util.ClientSettings;
import tritium.music.client.util.Mth;
import tritium.music.core.CloudMusic;
import tritium.music.core.lyric.LyricLine;

public class MusicLyricsWidget extends HudWidget {

    private static double scrollOffset = 0;

    private double fontH, lyricH;

    public enum ScrollEffects {
        Scroll,
        FadeIn,
        SlideIn,
        Aurora
    }

    public enum AlignMode {
        Left,
        Center,
        Right
    }

    private double auroraEnergy = 0;

    public MusicLyricsWidget() {
        super("MusicLyrics");
    }

    @Override
    public tritium.music.client.config.WidgetConfig.WidgetSettings settings() {
        return tritium.music.client.config.WidgetConfig.get().musicLyrics;
    }

    private static tritium.music.client.config.WidgetConfig.Lyrics cfg() {
        return tritium.music.client.config.WidgetConfig.get().lyrics;
    }

    private static int glowColor(double count, int alpha) {
        return RGBA.color(cfg().glowColor & 0xFFFFFF, alpha);
    }

    private static LyricLine currentLyric() {
        return CloudMusic.currentLyricNoEarlyJump != null ? CloudMusic.currentLyricNoEarlyJump : CloudMusic.currentLyric;
    }

    public static void resetProgress(float progress) {
        if (CloudMusic.lyrics.isEmpty()) return;

        try {
            CloudMusic.setLyricsProgress(progress);
            scrollOffset = (CloudMusic.lyrics.indexOf(currentLyric())) * getLyricHeight();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static double getLyricHeight() {
        double baseHeight = getFontRenderer().getHeight();
        double adjustment = CloudMusic.hasSecondaryLyrics() ? 0 : -getSmallFontRenderer().getHeight() - 4;
        return baseHeight + adjustment + cfg().lyricHeight;
    }

    public static boolean hasSecondaryLyrics() {
        return CloudMusic.hasSecondaryLyrics();
    }

    public static String getSecondaryLyrics(LyricLine bean) {
        return CloudMusic.getSecondaryLyrics(bean);
    }

    @Override
    public void onRender() {

        if (!shouldRender()) {
            return;
        }

        this.setWidth(cfg().width);
        this.setHeight(cfg().height);

        this.fontH = getFontRenderer().getHeight();
        this.lyricH = getLyricHeight();

        float songProgress = CloudMusic.player.getCurrentTimeMillis();

        boolean shouldNotDisplayOtherLyrics = cfg().singleLine;

        handleSingleLineMode(shouldNotDisplayOtherLyrics);

        updateScrollOffset(shouldNotDisplayOtherLyrics);

        StencilClipManager.beginClip(() -> Rect.draw(this.getX() - 2, this.getY(), this.getWidth() + 4, this.getHeight(), -1));

        renderAllLyrics(shouldNotDisplayOtherLyrics, songProgress);

        StencilClipManager.endClip();

        if (ClientSettings.DEBUG_MODE.getValue()) {
            LyricLine currentLine = currentLyric();
            if (currentLine != null && !CloudMusic.haveNoWords) {
                WordInfo wordInfo = calculateCurrentWordInfo(currentLine, songProgress);

                LyricLine.Word current = currentLine.words.get(wordInfo.currentIndex);
                FontManager.pf28bold.drawStringWithShadow("Current word: " + current.word, 100, 100, -1);
                double value = (songProgress - current.timestamp) / (double) (current.duration);
                FontManager.pf28bold.drawStringWithShadow("Perc: " + value, 100, 120, -1);
                FontManager.pf28bold.drawStringWithShadow("Dur: " + current.duration, 100, 140, -1);
                FontManager.pf28bold.drawStringWithShadow("Pos: " + (songProgress - current.timestamp), 100, 160, -1);
            }
        }
    }

    private boolean shouldRender() {
        return CloudMusic.player != null && !CloudMusic.player.isFinished() && !CloudMusic.lyrics.isEmpty();
    }

    private void handleSingleLineMode(boolean shouldNotDisplayOtherLyrics) {
        if (shouldNotDisplayOtherLyrics && CloudMusic.currentLyric == null) {
            if (!CloudMusic.lyrics.isEmpty()) {
                CloudMusic.currentLyric = CloudMusic.lyrics.getFirst();
            }
        }
    }

    private void updateScrollOffset(boolean shouldNotDisplayOtherLyrics) {
        int indexOf = CloudMusic.lyrics.indexOf(currentLyric());

        if (!shouldNotDisplayOtherLyrics) {
            if (currentLyric() == null) {
                scrollOffset = 0;
            } else {
                scrollOffset = Interpolations.interpolate(scrollOffset, indexOf * lyricH, 0.2f);
            }
        }
    }

    private void renderAllLyrics(boolean shouldNotDisplayOtherLyrics, float songProgress) {
        double offsetY = this.getY() + this.getHeight() / 2.0 - fontH / 2.0 - scrollOffset;
        int indexOf = CloudMusic.lyrics.indexOf(currentLyric());

        double pivotX = alignPivotX(cfg().alignMode);

        synchronized (CloudMusic.lyrics) {
            for (int i = 0; i < CloudMusic.lyrics.size(); i++) {
                LyricLine line = CloudMusic.lyrics.get(i);

                if (shouldNotDisplayOtherLyrics) {
                    if (i < indexOf) continue;
                    if (i > indexOf) break;
                }

                LyricRenderInfo renderInfo = calculateLyricPosition(
                        line, i, indexOf, offsetY, shouldNotDisplayOtherLyrics
                );

                if (renderInfo.shouldSkip) {
                    offsetY += lyricH;
                    continue;
                }

                if (renderInfo.shouldBreak) {
                    break;
                }

                updateLyricAnimation(line, i == indexOf);

                double focus = Math.max(0f, line.lineAlpha - 0.25f) / 0.75;
                double scale = 1.0 + focus * 0.05;

                RenderContext.graphics().pose().pushMatrix();
                scaleAtPos(pivotX, renderInfo.yPosition + fontH * 0.5, scale);

                if (cfg().scrollEffect == ScrollEffects.Aurora && !line.words.isEmpty()) {
                    updateAuroraLinger(line, renderInfo, line == currentLyric());
                }

                renderLyricText(line, renderInfo, i, indexOf);

                if (line == currentLyric() && !line.words.isEmpty()) {
                    handleScrollEffects(line, renderInfo, songProgress);
                }

                RenderContext.graphics().pose().popMatrix();

                offsetY += lyricH;
            }
        }
    }

    private double alignPivotX(AlignMode alignMode) {
        return switch (alignMode) {
            case Left -> this.getX();
            case Center -> this.getX() + this.getWidth() / 2.0;
            case Right -> this.getX() + this.getWidth();
        };
    }

    private double computeEdgeFade(double yPosition) {
        double height = this.getHeight();
        if (height <= 0) return 1.0;

        double band = Math.min(height * 0.5, lyricH * 1.4);
        if (band <= 0) return 1.0;

        double cy = yPosition + fontH * 0.5;
        double distance = Math.min(cy - this.getY(), this.getY() + height - cy);

        return Easing.EASE_OUT_CUBIC.getFunction().apply(Mth.limit(distance / band, 0, 1));
    }

    private static int withFade(int color, double fade) {
        if (fade >= 1.0) return color;
        int alpha = (int) (((color >>> 24) & 0xFF) * Mth.limit(fade, 0, 1));
        return RGBA.color(color & 0xFFFFFF, alpha);
    }

    private LyricRenderInfo calculateLyricPosition(LyricLine line, int index, int currentIndex,
                                                   double offsetY, boolean singleLineMode) {
        LyricRenderInfo info = new LyricRenderInfo();

        if (!singleLineMode) {
            double dest = this.getY() + this.getHeight() / 2.0 - fontH / 2.0 +
                    index * lyricH - (currentIndex * lyricH);

            if (line.offsetY == Double.MIN_VALUE || Math.abs(line.offsetY - dest) > 100) {
                line.offsetY = dest;
            }

            if (line.offsetY + lyricH < this.getY()) {
                info.shouldSkip = true;
                line.offsetY = dest;
                return info;
            }

            if (offsetY > this.getY() + this.getHeight()) {
                info.shouldBreak = true;
                return info;
            }

            applyGraceScroll(line, index, currentIndex, dest);

            info.yPosition = cfg().graceScroll ? line.offsetY : offsetY;
        } else {
            info.yPosition = this.getY() + this.getHeight() / 2.0 - fontH / 2.0;
            line.offsetY = info.yPosition;
        }

        info.fade = computeEdgeFade(info.yPosition);
        return info;
    }

    private void applyGraceScroll(LyricLine line, int index, int currentIndex, double dest) {
        float speed = 0.15f;
        LyricLine prevLrc = null;

        try {
            if (index > 0) {
                prevLrc = CloudMusic.lyrics.get(index - 1);
            }
        } catch (Exception ignored) {}

        if (prevLrc != null) {
            double prevDest = this.getY() + this.getHeight() / 2.0 - fontH / 2.0 +
                    (index - 1) * lyricH - (currentIndex * lyricH);
            double v = prevLrc.offsetY - prevDest;

            if (v < lyricH * 0.55f) {
                line.offsetY = Interpolations.interpolate(line.offsetY, dest, speed);
            }
        } else {
            line.offsetY = Interpolations.interpolate(line.offsetY, dest, speed);
        }
    }

    private void updateLyricAnimation(LyricLine line, boolean isCurrent) {
        line.lineAlpha = Interpolations.interpolate(
                line.lineAlpha,
                isCurrent ? 1f : .25f,
                0.1f
        );
    }

    private void renderLyricText(LyricLine line, LyricRenderInfo renderInfo,
                                 int index, int currentIndex) {
        boolean hasWords = !line.words.isEmpty();
        ScrollEffects effect = cfg().scrollEffect;
        boolean isCurrent = index == currentIndex;
        boolean slideInSelf = hasWords && effect == ScrollEffects.SlideIn && isCurrent && cfg().alignMode != AlignMode.Left;
        boolean auroraSelf = hasWords && effect == ScrollEffects.Aurora && isCurrent;
        boolean shouldRender = !(slideInSelf || auroraSelf);

        boolean isActive = index <= currentIndex;
        int primaryColor = withFade(RGBA.color(255, 255, 255, calculateAlpha(line, index, currentIndex, hasWords)), renderInfo.fade);
        int secondaryColor = withFade(RGBA.color(255, 255, 255, isActive ? (int) (line.lineAlpha * 255) : 100), renderInfo.fade);

        String secondaryLyric = hasSecondaryLyrics() ? getSecondaryLyrics(line) : "";
        boolean secondaryLyricEmpty = secondaryLyric.isEmpty();

        renderByAlignment(line, renderInfo, secondaryLyric, secondaryLyricEmpty,
                shouldRender, primaryColor, secondaryColor);
    }

    private int calculateAlpha(LyricLine line, int index, int currentIndex, boolean hasWords) {
        if (hasWords) {
            return index != currentIndex ? (int) (line.lineAlpha * 255) : 80;
        } else {
            return (int) (line.lineAlpha * 255);
        }
    }

    private void renderByAlignment(LyricLine line, LyricRenderInfo renderInfo,
                                   String secondaryLyric, boolean secondaryLyricEmpty,
                                   boolean shouldRender, int hexColor, int rgb) {
        AlignMode alignMode = cfg().alignMode;
        double y = renderInfo.yPosition;
        double secondaryY = y + fontH + 2;

        switch (alignMode) {
            case Left:
                if (shouldRender) {
                    bigFrString(line.getLyric(), this.getX(), y, hexColor);
                }
                if (!secondaryLyricEmpty) {
                    smallFrString(secondaryLyric, this.getX(), secondaryY, rgb);
                }
                break;
            case Center:
                double centerX = this.getX() + this.getWidth() / 2.0;
                if (shouldRender) {
                    bigFrStringCentered(line.getLyric(), centerX, y, hexColor);
                }
                if (!secondaryLyricEmpty) {
                    smallFrStringCentered(secondaryLyric, centerX, secondaryY, rgb);
                }
                break;
            case Right:
                if (shouldRender) {
                    bigFrString(line.getLyric(),
                            this.getX() + this.getWidth() - getFontRenderer().getStringWidthD(line.getLyric()), y, hexColor);
                }
                if (!secondaryLyricEmpty) {
                    smallFrString(secondaryLyric,
                            this.getX() + this.getWidth() - getSmallFontRenderer().getStringWidthD(secondaryLyric),
                            secondaryY, rgb);
                }
                break;
        }
    }

    private void handleScrollEffects(LyricLine line, LyricRenderInfo renderInfo, float songProgress) {
        WordInfo wordInfo = calculateCurrentWordInfo(line, songProgress);

        updateScrollWidth(line, wordInfo, songProgress);

        renderScrollEffect(line, renderInfo, wordInfo, songProgress);
    }

    private WordInfo calculateCurrentWordInfo(LyricLine line, float songProgress) {
        WordInfo info = new WordInfo();

        for (int k = 0; k < line.words.size(); k++) {
            LyricLine.Word word = line.words.get(k);

            if (word.timestamp > songProgress) {
                info.currentIndex = Math.max(0, k - 1);
                break;
            } else if (k == line.words.size() - 1) {
                info.currentIndex = k;
            }
        }

        for (int m = 0; m < info.currentIndex; m++) {
            info.textBefore.append(line.words.get(m).word);
        }

        return info;
    }

    private void updateScrollWidth(LyricLine line, WordInfo wordInfo, float songProgress) {
        LyricLine.Word current = line.words.get(wordInfo.currentIndex);

        double value = (songProgress - current.timestamp) / (double) (current.duration);

        double progress = Mth.limit(value, 0, 1);

        double offsetX = progress * getFontRenderer().getStringWidthD(current.word);

        line.scrollWidth = getFontRenderer().getStringWidthD(wordInfo.textBefore.toString()) + offsetX;
    }

    private void renderScrollEffect(LyricLine line, LyricRenderInfo renderInfo, WordInfo wordInfo, float songProgress) {
        switch (cfg().scrollEffect) {
            case Scroll -> renderScrollMode(line, renderInfo);
            case FadeIn -> renderFadeInMode(line, renderInfo, wordInfo, songProgress);
            case SlideIn -> renderSlideInMode(line, renderInfo, wordInfo, songProgress);
            case Aurora -> renderAuroraMode(line, renderInfo);
        }
    }

    private void renderAuroraMode(LyricLine line, LyricRenderInfo renderInfo) {
        CFontRenderer fr = getFontRenderer();
        String text = line.getLyric();
        if (text.isEmpty()) return;

        double leftX = calculateAlignmentX(text, cfg().alignMode);
        double baseY = renderInfo.yPosition;
        double fade = renderInfo.fade;

        double lineWidth = fr.getStringWidthD(text);
        double sungTotal = computeSungTotal(fr, line);
        double sweep = sungTotal > 0 ? Mth.limit(line.scrollWidth / sungTotal, 0.0, 1.0) : 0.0;
        double headX = leftX + sweep * lineWidth;

        double rawEnergy = cfg().audioReactive ? lowFrequencyEnergy() : 0.0;
        auroraEnergy = Interpolations.interpolate(auroraEnergy, rawEnergy, 0.25);
        double beat = auroraEnergy;

        double liftAmount = fontH * (0.10 + beat * 0.05);
        double waveSigma = Math.max(10.0, fontH * 1.1);
        double maxCharScale = 0.11 + beat * 0.05;

        if (cfg().auroraBloom && ClientSettings.RENDER_GLOW.getValue()) {
            renderAuroraGlow(fr, text, leftX, baseY, headX, fade, beat, line.auroraGlow);
        }

        double unsung = cfg().auroraUnsungOpacity;

        char[] chars = text.toCharArray();
        double x = leftX;

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            char next = i + 1 < chars.length ? chars[i + 1] : '\0';
            double cw = fr.getCharWidth(c, next);

            double reveal = Mth.limit((headX - x) / Math.max(1.0E-3, cw), 0.0, 1.0);
            double charAlpha = (unsung + (1.0 - unsung) * reveal) * fade;

            if (charAlpha > 0.003) {
                double cx = x + cw * 0.5;
                double d = headX - cx;
                double wave = Math.exp(-(d * d) / (2.0 * waveSigma * waveSigma));
                double lift = wave * liftAmount;
                double charScale = 1.0 + wave * maxCharScale;

                int accent = RGBA.opaque(glowColor(i * 0.2, 255));
                int tinted = RGBA.srgbLerp((float) (wave * 0.55), 0xFFFFFFFF, accent);
                int color = withFade(tinted, charAlpha);

                RenderContext.graphics().pose().pushMatrix();
                scaleAtPos(cx, baseY + fontH * 0.5 - lift, charScale);
                fr.drawString(String.valueOf(c), x, baseY - lift, color);
                RenderContext.graphics().pose().popMatrix();
            }

            x += cw;
        }

        if (cfg().auroraSpark && sweep > 0.001 && sweep < 0.999) {
            renderAuroraSpark(headX, baseY, fade, beat);
        }
    }

    private double computeSungTotal(CFontRenderer fr, LyricLine line) {
        int n = line.words.size();
        if (n == 0) return fr.getStringWidthD(line.getLyric());

        StringBuilder before = new StringBuilder();
        for (int i = 0; i < n - 1; i++) {
            before.append(line.words.get(i).word);
        }

        return fr.getStringWidthD(before.toString()) + fr.getStringWidthD(line.words.get(n - 1).word);
    }

    private void renderAuroraGlow(CFontRenderer fr, String text, double leftX, double baseY, double headX, double fade, double beat, double glowAlpha) {
        double intensity = Mth.limit(0.45 + beat * 0.5, 0.0, 1.0) * fade * glowAlpha;
        if (intensity <= 0.01) return;

        double centerY = baseY + fontH * 0.5;

        double[][] layers = {
                {1.60, 0.07}, {1.46, 0.11}, {1.33, 0.17}, {1.20, 0.25}, {1.10, 0.34}
        };

        char[] chars = text.toCharArray();
        double x = leftX;

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            char next = i + 1 < chars.length ? chars[i + 1] : '\0';
            double cw = fr.getCharWidth(c, next);

            double reveal = Mth.limit((headX - x) / Math.max(1.0E-3, cw), 0.0, 1.0);

            if (reveal > 0.01 && c != ' ') {
                double cx = x + cw * 0.5;
                int rgb = cfg().glowColor & 0xFFFFFF;
                String s = String.valueOf(c);

                for (double[] layer : layers) {
                    int alpha = (int) (intensity * reveal * layer[1] * 255.0);
                    if (alpha <= 0) continue;

                    RenderContext.graphics().pose().pushMatrix();
                    scaleAtPos(cx, centerY, layer[0]);
                    fr.drawString(s, x, baseY, RGBA.color(rgb, alpha));
                    RenderContext.graphics().pose().popMatrix();
                }
            }

            x += cw;
        }
    }

    private void updateAuroraLinger(LyricLine line, LyricRenderInfo renderInfo, boolean isCurrent) {
        line.auroraGlow = Interpolations.interpolate(line.auroraGlow, isCurrent ? 1f : 0f, isCurrent ? 0.35f : 0.06f);

        if (isCurrent) return;
        if (!cfg().auroraBloom || !ClientSettings.RENDER_GLOW.getValue()) return;
        if (line.auroraGlow <= 0.01f) return;

        CFontRenderer fr = getFontRenderer();
        String text = line.getLyric();
        if (text.isEmpty()) return;

        double leftX = calculateAlignmentX(text, cfg().alignMode);
        double headX = leftX + fr.getStringWidthD(text);
        renderAuroraGlow(fr, text, leftX, renderInfo.yPosition, headX, renderInfo.fade, auroraEnergy, line.auroraGlow);
    }

    private void renderAuroraSpark(double headX, double baseY, double fade, double beat) {
        double centerY = baseY + fontH * 0.5;
        double pulse = 0.6 + 0.4 * beat;
        int rgb = cfg().glowColor & 0xFFFFFF;

        double haloW = Math.max(2.0, fontH * 0.42) * (0.85 + beat * 0.3);
        double haloH = fontH * 1.05;
        roundedRect(headX - haloW * 0.5, centerY - haloH * 0.5, haloW, haloH, haloW * 0.5, RGBA.color(rgb, (int) (fade * pulse * 90.0)));

        double coreW = Math.max(1.0, fontH * 0.13);
        double coreH = fontH * 0.92;
        roundedRect(headX - coreW * 0.5, centerY - coreH * 0.5, coreW, coreH, coreW * 0.5, RGBA.white((int) (fade * pulse * 230.0)));

        double dot = Math.max(1.5, fontH * 0.2) * (0.9 + beat * 0.4);
        roundedRect(headX - dot * 0.5, centerY - dot * 0.5, dot, dot, dot * 0.5, RGBA.white((int) (fade * Math.min(1.0, 0.8 + beat) * 255.0)));
    }

    private double lowFrequencyEnergy() {
        float[] bands = tritium.music.core.audio.AudioPlayer.bandValues;
        if (bands == null || bands.length == 0) return 0.0;

        int count = Math.min(10, bands.length);
        double weightedSum = 0.0, totalWeight = 0.0;

        for (int i = 0; i < count; i++) {
            double weight = Math.exp(-i * 0.25);
            double value = Math.min(bands[i], 2.0);
            weightedSum += value * weight;
            totalWeight += weight;
        }

        if (totalWeight <= 0.0) return 0.0;

        double average = weightedSum / totalWeight;
        return Mth.limit(Math.log1p(average * 4.0) * 0.6, 0.0, 1.0);
    }

    private void renderScrollMode(LyricLine line, LyricRenderInfo renderInfo) {
        AlignMode alignMode = cfg().alignMode;
        double x = calculateAlignmentX(line.getLyric(), alignMode);

        StencilClipManager.beginClip(() -> Rect.draw(x, renderInfo.yPosition, line.scrollWidth + 1, fontH + 4, -1));

        renderAlignedText(line.getLyric(), renderInfo.yPosition, withFade(-1, renderInfo.fade), alignMode);

        StencilClipManager.endClip();
    }

    private void renderFadeInMode(LyricLine line, LyricRenderInfo renderInfo, WordInfo wordInfo, float songProgress) {
        double offsetX = calculateAlignmentX(line.getLyric(), cfg().alignMode);

        for (int m = 0; m <= wordInfo.currentIndex; m++) {
            LyricLine.Word word = line.words.get(m);

            if (m == wordInfo.currentIndex) {
                updateCurrentWordAnimation(word, songProgress);
            } else {
                word.alpha = 1;
            }

            bigFrString(word.word, offsetX, renderInfo.yPosition,
                    RGBA.color(255, 255, 255, (int) (word.alpha * 255 * renderInfo.fade)));

            offsetX += getFontRenderer().getStringWidthD(word.word);
        }
    }

    private void renderSlideInMode(LyricLine line, LyricRenderInfo renderInfo, WordInfo wordInfo, float songProgress) {
        double offsetX = calculateSlideInTargetX(line, cfg().alignMode);
        double targetOffsetX = 0;

        for (int m = 0; m <= wordInfo.currentIndex; m++) {
            LyricLine.Word word = line.words.get(m);
            double stWidth = getFontRenderer().getStringWidthD(word.word);

            if (m == wordInfo.currentIndex) {
                updateCurrentWordAnimation(word, songProgress);
                targetOffsetX += stWidth * Easing.EASE_OUT_CUBIC.getFunction().apply(word.progress);
            } else {
                word.alpha = 1;
                targetOffsetX += stWidth;
            }

            bigFrString(word.word, offsetX, renderInfo.yPosition,
                    RGBA.color(255, 255, 255, (int) (word.alpha * 255 * renderInfo.fade)));

            offsetX += stWidth;
        }

        line.targetOffsetX = targetOffsetX;
    }

    private void updateCurrentWordAnimation(LyricLine.Word word, float songProgress) {
        double progress = Mth.limit((songProgress - word.timestamp) / (double) word.duration, 0, 1);
        word.progress = progress;
        word.alpha = (float) Math.min(1, progress * 1.25f);
    }

    private double calculateAlignmentX(String text, AlignMode alignMode) {
        if (alignMode == AlignMode.Left) {
            return this.getX();
        } else if (alignMode == AlignMode.Center) {
            return this.getX() + this.getWidth() / 2.0f - getFontRenderer().getStringWidthD(text) / 2.0f;
        } else {
            return this.getX() + this.getWidth() - getFontRenderer().getStringWidthD(text);
        }
    }

    private double calculateSlideInTargetX(LyricLine line, AlignMode alignMode) {
        if (alignMode == AlignMode.Left) {
            return this.getX();
        } else if (alignMode == AlignMode.Center) {
            return this.getX() + this.getWidth() / 2.0 - line.targetOffsetX / 2.0;
        } else {
            return this.getX() + this.getWidth() - line.targetOffsetX;
        }
    }

    private void renderAlignedText(String text, double y, int color, AlignMode alignMode) {
        if (alignMode == AlignMode.Left) {
            bigFrString(text, this.getX(), y, color);
        } else if (alignMode == AlignMode.Center) {
            bigFrStringCentered(text, this.getX() + this.getWidth() / 2.0, y, color);
        } else {
            bigFrString(text, this.getX() + this.getWidth() - getFontRenderer().getStringWidthD(text), y, color);
        }
    }

    private static class LyricRenderInfo {
        double yPosition;
        double fade = 1.0;
        boolean shouldSkip = false;
        boolean shouldBreak = false;
    }

    private static class WordInfo {
        int currentIndex = 0;
        StringBuilder textBefore = new StringBuilder();
    }

    private static CFontRenderer getFontRenderer() {
        return FontManager.pf28bold;
    }

    private static CFontRenderer getSmallFontRenderer() {
        return FontManager.pf18bold;
    }

    private void bigFrString(String text, double x, double y, int color) {
        if (cfg().shadow) {
            getFontRenderer().drawStringWithShadow(text, x, y, color);
        } else {
            getFontRenderer().drawString(text, x, y, color);
        }
    }

    private void bigFrStringCentered(String text, double x, double y, int color) {
        if (cfg().shadow) {
            getFontRenderer().drawCenteredStringWithShadow(text, x, y, color);
        } else {
            getFontRenderer().drawCenteredString(text, x, y, color);
        }
    }

    private void smallFrString(String text, double x, double y, int color) {
        if (cfg().shadow) {
            getSmallFontRenderer().drawStringWithShadow(text, x, y, color);
        } else {
            getSmallFontRenderer().drawString(text, x, y, color);
        }
    }

    private void smallFrStringCentered(String text, double x, double y, int color) {
        if (cfg().shadow) {
            getSmallFontRenderer().drawCenteredStringWithShadow(text, x, y, color);
        } else {
            getSmallFontRenderer().drawCenteredString(text, x, y, color);
        }
    }
}
