package tritium.music.client.screens.ncm;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.Image;
import tritium.music.client.rendering.RGBA;
import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.ScrollText;
import tritium.music.client.rendering.SharedRenderingConstants;
import tritium.music.client.rendering.StencilClipManager;
import tritium.music.client.rendering.animation.Easing;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.animation.spring.SpringAnimation;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.shader.BloomShader;
import tritium.music.client.rendering.shader.Shaders;
import tritium.music.client.rendering.ui.widgets.IconWidget;
import tritium.music.client.util.CursorUtils;
import tritium.music.client.util.MouseUtil;
import tritium.music.core.CloudMusic;
import tritium.music.core.MusicState;
import tritium.music.core.audio.AudioPlayer;
import tritium.music.core.lyric.LyricLine;
import tritium.music.client.util.Mth;
import tritium.music.core.model.Music;
import tritium.music.core.util.Timer;
import tritium.music.platform.Platform;
import tritium.music.platform.TextureHandle;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MusicLyricsPanel implements SharedRenderingConstants {

    static double scrollOffset, scrollTarget;

    double fftScale = 0;
    float musicBgAlpha = 1.0f;
    static Music prevMusic = null;

    public float alpha = 0f;
    boolean closing = false;

    static BloomShader coverBloomShader;

    Timer scrollOffsetResetTimer = new Timer();

    double coverSize = (CloudMusic.player == null || CloudMusic.player.isPausing()) ? this.getCoverSizeMin() : this.getCoverSizeMax();
    float coverAlpha = 1f;

    boolean progressBarDragging = false;
    double progressBarProgressOverride = 0;
    double progressBarHeight = 8, volumeBarHeight = 8;
    double progressThumbAlpha = 0, volumeThumbAlpha = 0;

    boolean prevMouse = false;

    ScrollText stMusicName = new ScrollText(), stArtists = new ScrollText();
    IconWidget playPauseButton = new IconWidget("G", FontManager.music40, 0, 0, 24, 24);
    IconWidget prev = new IconWidget("E", FontManager.music40, 0, 0, 32, 32);
    IconWidget next = new IconWidget("H", FontManager.music40, 0, 0, 32, 32);

    private final Music music;

    public MusicLyricsPanel(Music music) {
        this.music = music;
        updateLyricPositionsImmediate(NCMScreen.getInstance().getPanelWidth() * getLyricWidthFactor());
    }

    public static void resetProgress(float progress) {
        CloudMusic.updateCurrentLyric(progress);
        updateLyricPositionsImmediate(NCMScreen.getInstance().getPanelWidth() * getLyricWidthFactor());
    }

    public static double getLyricWidthFactor() {
        return .48;
    }

    private static double getLyricLineSpacing() {
        return 20;
    }

    private static double lyricFraction() {
        return .25;
    }

    public static void updateLyricPositionsImmediate(double width) {
        if (CloudMusic.currentLyric == null) return;

        double offsetY = RenderSystem.getHeight() * lyricFraction() - getLyricLineSpacing();
        int toIndex = CloudMusic.lyrics.indexOf(CloudMusic.currentLyric);

        if (toIndex == -1 || toIndex >= CloudMusic.lyrics.size()) return;

        synchronized (CloudMusic.lyrics) {
            List<LyricLine> subList = CloudMusic.lyrics.subList(0, toIndex);
            for (int i = subList.size() - 1; i >= 0; i--) {
                LyricLine lyric = subList.get(i);

                if (i == subList.size() - 1) {
                    LyricLayout.computeHeight(lyric, width);
                    offsetY -= lyric.height;
                }

                lyric.posY = offsetY;
                LyricLayout.spring(lyric).setPosition(offsetY);

                LyricLayout.computeHeight(lyric, width);
                offsetY -= lyric.height + getLyricLineSpacing();
            }

            offsetY = RenderSystem.getHeight() * lyricFraction();
            for (LyricLine lyric : CloudMusic.lyrics.subList(toIndex, CloudMusic.lyrics.size())) {
                lyric.posY = offsetY;
                LyricLayout.spring(lyric).setPosition(offsetY);

                LyricLayout.computeHeight(lyric, width);
                offsetY += lyric.height + getLyricLineSpacing();
            }
        }
    }

    public static void updateLyricPositionsImmediate(double width, double playbackProgress) {
        if (CloudMusic.currentLyric == null) return;

        double offsetY = RenderSystem.getHeight() * lyricFraction() - getLyricLineSpacing();
        int toIndex = CloudMusic.lyrics.indexOf(CloudMusic.findCurrentLyric(playbackProgress));

        if (toIndex == -1 || toIndex >= CloudMusic.lyrics.size()) return;

        synchronized (CloudMusic.lyrics) {
            List<LyricLine> subList = CloudMusic.lyrics.subList(0, toIndex);
            for (int i = subList.size() - 1; i >= 0; i--) {
                LyricLine lyric = subList.get(i);

                if (i >= subList.size() - 2) {
                    LyricLayout.computeHeight(lyric, width);
                    offsetY -= lyric.height;
                }

                lyric.posY = offsetY;
                LyricLayout.spring(lyric).setPosition(offsetY);

                LyricLayout.computeHeight(lyric, width);
                offsetY -= lyric.height + getLyricLineSpacing();
            }

            offsetY = RenderSystem.getHeight() * lyricFraction();
            for (LyricLine lyric : CloudMusic.lyrics.subList(toIndex, CloudMusic.lyrics.size())) {
                lyric.posY = offsetY;
                LyricLayout.spring(lyric).setPosition(offsetY);

                LyricLayout.computeHeight(lyric, width);
                offsetY += lyric.height + getLyricLineSpacing();
            }
        }
    }

    private static long getLyricInterpolationWaitTimeMillis() {
        return 75;
    }

    private static void resetLyricStatus() {
        CloudMusic.resetLyricStatus();
    }

    public void onInit() {
        resetLyricStatus();
    }

    public void close() {
        closing = true;
    }

    public boolean shouldClose() {
        return closing && alpha <= 0.02f;
    }

    public void onRender(double mouseX, double mouseY, double posX, double posY, double width, double height, int dWheel) {

        if (prevMouse && !MouseUtil.isLeftDown()) prevMouse = false;

        alpha = Interpolations.interpolate(alpha, closing ? 0.0f : 1f, 0.3f);

        RenderContext.graphics().pose().pushMatrix();
        scaleAtPos(posX + width * .5, posY + height * .5, 1.1 - (alpha * 0.1));

        this.renderBackground(posX, posY, width, height, alpha);
        this.renderControlsPart(mouseX, mouseY, posX, posY, width, height, alpha);
        this.renderLyrics(mouseX, mouseY, posX, posY, width, height, dWheel, alpha);
        RenderContext.graphics().pose().popMatrix();
    }

    private static boolean isShiftDown() {
        long handle = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private void renderLyrics(double mouseX, double mouseY, double posX, double posY, double width, double height, int dWheel, float alpha) {

        if (CloudMusic.lyrics.isEmpty()) return;

        boolean playerNotReady = CloudMusic.player == null;
        float totalTimeMillis = playerNotReady ? 0 : CloudMusic.player.getTotalTimeMillis();
        double overridePlaybackProgress = progressBarProgressOverride * totalTimeMillis;
        double songProgress = playerNotReady ? 0 : (progressBarDragging ? overridePlaybackProgress : CloudMusic.player.getCurrentTimeMillis());

        double lyricsWidth = width * getLyricWidthFactor();
        this.updateLyricPositions(posY, height, lyricsWidth);

        List<Runnable> blurRects = new ArrayList<>();

        boolean hoveringLyrics = isHovered(mouseX, mouseY, posX + width * .5, posY, width * .5, height);

        if (hoveringLyrics && dWheel != 0) {
            double strength = 24;
            if (isShiftDown()) strength *= 2;

            if (dWheel > 0) scrollTarget += strength;
            else scrollTarget -= strength;

            scrollOffsetResetTimer.reset();
        }

        if (scrollOffsetResetTimer.isDelayed(3000)) {
            scrollTarget = 0;
        }

        scrollOffset = Interpolations.interpolate(scrollOffset, scrollTarget, 0.25f);

        double lyricRenderOffsetX = RenderSystem.getWidth() * .48;

        double dividerX = lyricRenderOffsetX - 32;
        double dividerMidY = posY + height * 0.5;
        RenderSystem.drawGradientRectTopToBottom(dividerX, posY + height * 0.16, dividerX + 1, dividerMidY, hexColor(1f, 1f, 1f, 0f), hexColor(1f, 1f, 1f, alpha * 0.06f));
        RenderSystem.drawGradientRectTopToBottom(dividerX, dividerMidY, dividerX + 1, posY + height * 0.84, hexColor(1f, 1f, 1f, alpha * 0.06f), hexColor(1f, 1f, 1f, 0f));

        LyricLine currentLyric = progressBarDragging ? CloudMusic.findCurrentLyric(overridePlaybackProgress) : CloudMusic.currentLyric;
        int currentIndex = CloudMusic.lyrics.indexOf(currentLyric);

        for (int k = 0; k < CloudMusic.lyrics.size(); k++) {
            LyricLine lyric = CloudMusic.lyrics.get(k);

            if (lyric.posY + lyric.height + getLyricLineSpacing() + scrollOffset < posY) {
                continue;
            }

            if (lyric.posY + scrollOffset > posY + height) {
                break;
            }

            boolean isCurrentLyric = lyric == currentLyric;
            lyric.alpha = Interpolations.interpolate(lyric.alpha, isCurrentLyric ? 1f : 0f, isCurrentLyric ? 0.1f : .05f);
            boolean isHovering = isHovered(mouseX, mouseY - scrollOffset, lyricRenderOffsetX, lyric.posY, lyricsWidth, lyric.height);
            lyric.hoveringAlpha = Interpolations.interpolate(lyric.hoveringAlpha, isHovering ? 1f : 0f, 0.2f);
            lyric.blurAlpha = Interpolations.interpolate(lyric.blurAlpha, !hoveringLyrics ? Math.min(1f, Math.abs(k - currentIndex) * .85f) : 0f, 0.05f);

            if (isHovering) {
                CursorUtils.setOverride(CursorUtils.HAND);
            }

            if (isHovering && MouseUtil.isLeftDown() && !prevMouse) {
                prevMouse = true;
                if (CloudMusic.player != null) {
                    CloudMusic.player.setPlaybackTime(lyric.timestamp);
                }

                if (scrollTarget != 0) {
                    updateLyricPositionsImmediate(lyricsWidth);
                }
                scrollTarget = 0;
                resetLyricStatus();
            }

            if (lyric.hoveringAlpha >= .02f)
                roundedRect(lyricRenderOffsetX - 4, lyric.posY + scrollOffset + lyric.reboundAnimation, lyricsWidth + lyric.reboundAnimation, lyric.height + 2, 8, 4 + 2 * Easing.EASE_IN_OUT_QUAD.getFunction().apply((double) lyric.hoveringAlpha), 1, 1, 1, alpha * lyric.hoveringAlpha * .15f);

            double renderX = lyricRenderOffsetX + lyric.reboundAnimation;
            double renderY = lyric.posY + lyric.reboundAnimation + scrollOffset;

            lyric.reboundAnimation = Interpolations.interpolate(lyric.reboundAnimation, isCurrentLyric ? 2f : 0f, 0.1f);

            List<LyricLine.Word> words = lyric.words;
            if (!words.isEmpty()) {
                for (LyricLine.Word word : words) {
                    double wordWidth = FontManager.pf65bold.getStringWidthD(word.word);

                    if (renderX + wordWidth >= lyricRenderOffsetX + lyricsWidth + lyric.reboundAnimation) {
                        renderX = lyricRenderOffsetX + lyric.reboundAnimation;
                        renderY += FontManager.pf65bold.getHeight() * .85 + 4;
                    }

                    if (!lyric.renderEmphasizes) Arrays.fill(word.emphasizes, 2);

                    double emphasizeWholeWord = word.emphasizes[0];

                    char[] charArray = word.word.toCharArray();

                    double emphasizeTarget = 1;
                    double emphasizeSpeed = 0.05;

                    if (isCurrentLyric) {
                        if (charArray.length > 1) {
                            double x = renderX;
                            for (int j = 0; j < charArray.length; j++) {
                                char c = charArray[j];
                                FontManager.pf65bold.drawString(String.valueOf(c), x, renderY - word.emphasizes[j], hexColor(1, 1, 1, alpha * .5f));
                                x += FontManager.pf65bold.getCharWidth(c, j + 1 < charArray.length ? charArray[j + 1] : '\0');
                            }
                        } else {
                            FontManager.pf65bold.drawString(word.word, renderX, renderY - word.emphasizes[0], hexColor(1, 1, 1, alpha * .5f));
                        }
                    } else {
                        FontManager.pf65bold.drawString(word.word, renderX, renderY, hexColor(1, 1, 1, alpha * .35f));
                    }

                    if (currentIndex - k <= 1) {
                        double progress = Mth.limit((songProgress - word.timestamp) / (double) (word.duration), 0, 1);
                        double stringWidthD = wordWidth;
                        boolean shouldClip = progress > 0 && progress < 1;

                        if (progress == 1) {
                            double x = renderX;
                            for (int j = 0; j < charArray.length; j++) {
                                char c = charArray[j];

                                if (lyric.renderEmphasizes)
                                    word.emphasizes[j] = Interpolations.interpolate(word.emphasizes[j], emphasizeTarget, emphasizeSpeed);

                                FontManager.pf65bold.drawString(String.valueOf(c), x, renderY - word.emphasizes[j], hexColor(1, 1, 1, alpha * lyric.alpha));
                                x += FontManager.pf65bold.getCharWidth(c, j + 1 < charArray.length ? charArray[j + 1] : '\0');
                            }
                        }

                        if (shouldClip) {
                            double sungWidth = stringWidthD * progress;
                            double clipX = renderX;
                            double clipY = renderY - FontManager.pf65bold.getHeight();
                            double clipH = FontManager.pf65bold.getHeight() * 2 + 6;

                            StencilClipManager.beginClip(() -> Rect.draw(clipX, clipY, sungWidth, clipH, -1));

                            int prog = (int) (progress * charArray.length);
                            double x = renderX;
                            for (int j = 0; j < charArray.length; j++) {
                                char c = charArray[j];
                                if (j <= prog && lyric.renderEmphasizes) {
                                    word.emphasizes[j] = Interpolations.interpolate(word.emphasizes[j], emphasizeTarget, emphasizeSpeed);
                                }
                                FontManager.pf65bold.drawString(String.valueOf(c), x, renderY - word.emphasizes[j], hexColor(1, 1, 1, alpha * lyric.alpha));
                                x += FontManager.pf65bold.getCharWidth(c, j + 1 < charArray.length ? charArray[j + 1] : '\0');
                            }

                            StencilClipManager.endClip();
                        }
                    } else {
                        FontManager.pf65bold.drawString(word.word, renderX, renderY - emphasizeWholeWord, hexColor(1, 1, 1, alpha * lyric.alpha));
                    }

                    renderX += wordWidth;
                }
            } else {
                String[] strings = FontManager.pf65bold.fitWidth(lyric.lyric, lyricsWidth);

                for (String string : strings) {
                    FontManager.pf65bold.drawString(string, renderX, renderY, hexColor(1, 1, 1, alpha * ((lyric.alpha * .7f) + .3f)));
                    renderY += FontManager.pf65bold.getHeight() * .85 + 4;
                }

                renderY -= FontManager.pf65bold.getHeight() * .85 + 4;
            }

            if (lyric.translationText != null) {
                double translationX = lyricRenderOffsetX + lyric.reboundAnimation;
                double translationY = renderY + FontManager.pf65bold.getHeight() * .85 + 8;

                String[] strings = FontManager.pf34bold.fitWidth(lyric.translationText, lyricsWidth);
                for (String string : strings) {
                    FontManager.pf34bold.drawString(string, translationX, translationY, hexColor(1, 1, 1, alpha * .75f * ((lyric.alpha * .6f) + .4f)));
                    translationY += FontManager.pf34bold.getHeight() + 4;
                }
            }

            if (Shaders.BLUR_SHADER.isAvailable() && alpha * lyric.blurAlpha > 0.004f) {
                double by = lyric.posY + scrollOffset;
                blurRects.add(() -> Rect.draw(lyricRenderOffsetX - 4, by, lyricsWidth, lyric.height + 8, hexColor(1, 1, 1, alpha * lyric.blurAlpha)));
            }
        }

        RenderContext.graphics().pose().pushMatrix();
        this.scaleAtPos(lyricRenderOffsetX, RenderSystem.getHeight() * .5, 1 / (1.1 - (alpha * 0.1)));
        Shaders.BLUR_SHADER.runNoCaching(blurRects);
        RenderContext.graphics().pose().popMatrix();
    }

    private void updateLyricPositions(double posY, double height, double width) {

        if (CloudMusic.currentLyric == null) return;

        int idxCurrent = CloudMusic.lyrics.indexOf(CloudMusic.currentLyric);

        if (idxCurrent < 0 || idxCurrent >= CloudMusic.lyrics.size()) return;

        double offsetY = RenderSystem.getHeight() * lyricFraction();

        synchronized (CloudMusic.lyrics) {
            List<LyricLine> subList = CloudMusic.lyrics.subList(0, idxCurrent);
            double frameDeltaTime = RenderSystem.getFrameDeltaTime() * .0125;
            for (int i = subList.size() - 1; i >= 0; i--) {
                LyricLine lyric = subList.get(i);

                LyricLayout.computeHeight(lyric, width);
                offsetY -= lyric.height + getLyricLineSpacing();

                if ((scrollTarget == 0 && (subList.size() - 1 - i) >= 3) && lyric.posY + lyric.height + getLyricLineSpacing() + 2 + scrollOffset < posY)
                    break;

                SpringAnimation spring = LyricLayout.spring(lyric);
                spring.setTargetPosition(offsetY);
                spring.update(frameDeltaTime);
                lyric.posY = spring.getCurrentPosition();
            }

            offsetY = RenderSystem.getHeight() * lyricFraction();
            List<LyricLine> list = CloudMusic.lyrics.subList(idxCurrent, CloudMusic.lyrics.size());
            int oobCounter = 0;
            int j = idxCurrent - 1;
            for (LyricLine lyric : list) {
                j++;

                LyricLayout.computeHeight(lyric, width);

                LyricLine prevLine = j > 0 ? CloudMusic.lyrics.get(j - 1) : null;

                if (prevLine != null) {
                    if (prevLine.delayTimer.isDelayed(getLyricInterpolationWaitTimeMillis()))
                        lyric.shouldUpdatePosition = true;
                }

                if (prevLine != null && !lyric.shouldUpdatePosition) {
                    lyric.delayTimer.reset();
                    break;
                }

                if (prevLine == null && !lyric.delayTimer.isDelayed(getLyricInterpolationWaitTimeMillis())) break;

                SpringAnimation spring = LyricLayout.spring(lyric);
                spring.setTargetPosition(offsetY);
                spring.update(frameDeltaTime);
                lyric.posY = spring.getCurrentPosition();

                if (offsetY + scrollOffset > posY + height) {
                    oobCounter += 1;
                    if (oobCounter >= 4 && scrollTarget == 0) break;
                }

                offsetY += lyric.height + getLyricLineSpacing();
            }
        }
    }

    private double getCoverSizeMax() {
        return RenderSystem.getHeight() * .5;
    }

    private double getCoverSizeMin() {
        return getCoverSizeMax() * .8;
    }

    private void renderControlsPart(double mouseX, double mouseY, double posX, double posY, double width, double height, float alpha) {
        AudioPlayer player = CloudMusic.player;

        double center = this.getCoverSizeMin();
        coverSize = Interpolations.interpolate(coverSize, player == null || player.isPausing() ? this.getCoverSizeMin() : this.getCoverSizeMax(), 0.2f);

        double xOffset = 0;
        double coverSizePerc = coverSize / this.getCoverSizeMax();
        double coverRadius = 7;

        RenderContext.graphics().pose().pushMatrix();
        this.scaleAtPos(RenderSystem.getWidth() * .5, RenderSystem.getHeight() * .5, (.925 + (alpha * 0.075)));
        if (coverBloomShader == null)
            coverBloomShader = new BloomShader();

        coverBloomShader.run(Collections.singletonList(() -> this.roundedRect(center - coverSize * .5 + xOffset, center - coverSize * .575, coverSize, coverSize, coverRadius * coverSizePerc - 2, -.5, 0, 0, 0, alpha * .4f)));
        RenderContext.graphics().pose().popMatrix();

        if (CloudMusic.currentlyPlaying != null) {
            TextureHandle musicCover = CloudMusic.currentlyPlaying.getCoverLocation();
            if (Platform.hasTexture(musicCover)) {
                coverAlpha = Interpolations.interpolate(coverAlpha, 1.0f, 0.2f);
                RenderSystem.bindTexture(tritium.music.fabric.ui.Identifiers.of(musicCover));
                this.roundedRectTextured(center - coverSize * .5 + xOffset, center - coverSize * .575, coverSize, coverSize, coverRadius * coverSizePerc, alpha * coverAlpha);
            }
        }

        double elementsXOffset = center - this.getCoverSizeMax() * .5 + xOffset;
        double elementsYOffset = center + this.getCoverSizeMax() * .45 + 8;

        String name = CloudMusic.currentlyPlaying == null ? "" : CloudMusic.currentlyPlaying.getName();
        String artists = CloudMusic.currentlyPlaying == null ? "" : CloudMusic.currentlyPlaying.getArtistsName();

        stMusicName.render(FontManager.pf28bold, name, elementsXOffset, elementsYOffset, this.getCoverSizeMax(), RGBA.color(1f, 1f, 1f, alpha));
        stArtists.render(FontManager.pf20bold, artists, elementsXOffset, elementsYOffset + FontManager.pf20bold.getHeight() + 8, this.getCoverSizeMax(), RGBA.color(1f, 1f, 1f, alpha * .8f));

        double progressBarYOffset = elementsYOffset + FontManager.pf20bold.getHeight() + 8 + FontManager.pf20bold.getHeight() + 8;
        double progressBarWidth = this.getCoverSizeMax();

        roundedRect(elementsXOffset, progressBarYOffset - progressBarHeight * .5, progressBarWidth, progressBarHeight, (this.progressBarHeight / 8.0f) * 2.5, hexColor(1, 1, 1, alpha * .22f));

        float currentTimeMillis = player == null ? 0 : player.getCurrentTimeMillis();
        float totalTimeMillis = player == null ? 0.01f : player.getTotalTimeMillis();
        double perc = player == null ? 0 : (progressBarDragging ? progressBarProgressOverride : currentTimeMillis / totalTimeMillis);

        double fElementsXOffset = elementsXOffset, fProgressBarYOffset = progressBarYOffset, fPerc = perc, fProgressBarWidth = progressBarWidth;
        StencilClipManager.beginClip(() -> Rect.draw(fElementsXOffset, fProgressBarYOffset - progressBarHeight * .5, fProgressBarWidth * fPerc, progressBarHeight, -1));
        roundedRect(elementsXOffset, progressBarYOffset - progressBarHeight * .5, progressBarWidth, progressBarHeight, (this.progressBarHeight / 8.0f) * 2.5, hexColor(1, 1, 1, alpha));
        StencilClipManager.endClip();

        boolean hoveringProgressBar = progressBarDragging || this.isHovered(mouseX, mouseY, elementsXOffset, progressBarYOffset - progressBarHeight * .5, progressBarWidth, 8);
        this.progressBarHeight = Interpolations.interpolate(this.progressBarHeight, hoveringProgressBar ? 8 : 5, 0.3f);
        this.progressThumbAlpha = Interpolations.interpolate(this.progressThumbAlpha, hoveringProgressBar ? 1.0 : 0.0, 0.25f);

        boolean lmbDown = MouseUtil.isLeftDown();
        if (hoveringProgressBar && lmbDown && !prevMouse) {
            prevMouse = true;
            progressBarDragging = true;

            double xDelta = Math.max(0, Math.min(progressBarWidth, (mouseX - elementsXOffset)));
            this.progressBarProgressOverride = xDelta / progressBarWidth;
            updateLyricPositionsImmediate(NCMScreen.getInstance().getPanelWidth() * getLyricWidthFactor(), progressBarProgressOverride * totalTimeMillis);
        }

        if (progressBarDragging) {
            if (!lmbDown) {
                progressBarDragging = false;

                double percent = this.progressBarProgressOverride;

                if (player != null) {
                    float progress = (float) (percent * totalTimeMillis);
                    player.setPlaybackTime(progress);
                    MusicLyricsPanel.resetProgress(progress);
                    scrollTarget = scrollOffset = 0;
                }
            } else {
                double xDelta = Math.max(0, Math.min(progressBarWidth, (mouseX - elementsXOffset)));
                this.progressBarProgressOverride = xDelta / progressBarWidth;
                updateLyricPositions(posY, height, NCMScreen.getInstance().getPanelWidth() * getLyricWidthFactor());
            }
        }

        float curTime = progressBarDragging ? (float) (progressBarProgressOverride * totalTimeMillis) : currentTimeMillis;
        FontManager.pf12bold.drawString(formatDuration(curTime), elementsXOffset, progressBarYOffset + 8, hexColor(1, 1, 1, alpha * .5f));
        String remainingTime = "-" + formatDuration(totalTimeMillis - curTime);
        FontManager.pf12bold.drawString(remainingTime, elementsXOffset + progressBarWidth - FontManager.pf12bold.getStringWidthD(remainingTime), progressBarYOffset + 8, hexColor(1, 1, 1, alpha * .5f));

        double volumeBarYOffset = posY + height - (center - getCoverSizeMax() * .575 - NCMScreen.getInstance().getSpacing()) - FontManager.music40.getHeight() * .5 + 2;
        double volumeBarWidth = this.getCoverSizeMax() - FontManager.music40.getStringWidthD("I") - FontManager.music40.getStringWidthD("J");

        double volumeIconY = volumeBarYOffset - FontManager.music40.getHeight() * .5 - .5;
        FontManager.music40.drawString("I", elementsXOffset - 8, volumeIconY, hexColor(1, 1, 1, alpha * .5f));
        FontManager.music40.drawString("J", elementsXOffset + progressBarWidth - FontManager.music40.getStringWidthD("J") + 4, volumeIconY, hexColor(1, 1, 1, alpha * .5f));

        double volumeBarXOffset = elementsXOffset + FontManager.music40.getStringWidthD("I") - 2;
        roundedRect(volumeBarXOffset, volumeBarYOffset - volumeBarHeight * .5, volumeBarWidth, volumeBarHeight, (this.volumeBarHeight / 8.0f) * 2.5, hexColor(1, 1, 1, alpha * .22f));

        double fVolBarX = volumeBarXOffset, fVolBarY = volumeBarYOffset, fVolBarW = volumeBarWidth;
        double vol = player == null ? 0 : player.getVolume();
        StencilClipManager.beginClip(() -> Rect.draw(fVolBarX, fVolBarY - volumeBarHeight * .5, fVolBarW * vol, volumeBarHeight, -1));
        roundedRect(volumeBarXOffset, volumeBarYOffset - volumeBarHeight * .5, volumeBarWidth, volumeBarHeight, (this.volumeBarHeight / 8.0f) * 2.5, hexColor(1, 1, 1, alpha));
        StencilClipManager.endClip();

        boolean hoveringVolumeBar = this.isHovered(mouseX, mouseY, volumeBarXOffset, volumeBarYOffset - volumeBarHeight * .5, volumeBarWidth, 8);
        this.volumeBarHeight = Interpolations.interpolate(this.volumeBarHeight, hoveringVolumeBar ? 8 : 5, 0.3f);
        this.volumeThumbAlpha = Interpolations.interpolate(this.volumeThumbAlpha, hoveringVolumeBar ? 1.0 : 0.0, 0.25f);

        if (hoveringVolumeBar && lmbDown) {
            double xDelta = Math.max(0, Math.min(volumeBarWidth, (mouseX - (volumeBarXOffset))));
            double percent = xDelta / volumeBarWidth;

            MusicState.get().setVolume((float) percent);
            if (player != null) player.setVolume((float) percent);
        }

        if (hoveringProgressBar || hoveringVolumeBar) {
            CursorUtils.setOverride(CursorUtils.HAND);
        }

        playPauseButton.setAlpha(alpha);
        playPauseButton.setWidth(32);
        playPauseButton.setHeight(32);
        playPauseButton.setPosition(volumeBarXOffset + volumeBarWidth * .5 - playPauseButton.getWidth() * .5, progressBarYOffset + (volumeBarYOffset - progressBarYOffset) * .5 - playPauseButton.getHeight() * .5);
        playPauseButton.renderWidget(mouseX, mouseY, 0);
        playPauseButton.setColor(Color.WHITE);

        playPauseButton.setBeforeRenderCallback(() -> {
            if (CloudMusic.player == null || CloudMusic.player.isPausing()) {
                playPauseButton.setIcon("G");
            } else {
                playPauseButton.setIcon("F");
            }
        });

        playPauseButton.setOnClickCallback((x, y, i) -> {
            if (i == 0) {
                if (CloudMusic.player != null && CloudMusic.currentlyPlaying != null) {
                    if (CloudMusic.player.isPausing()) CloudMusic.player.unpause();
                    else CloudMusic.player.pause();
                }
            }
            return true;
        });

        playPauseButton.fontOffsetY = 0;

        prev.setAlpha(alpha);
        prev.setWidth(32);
        prev.setHeight(32);
        prev.setPosition(volumeBarXOffset + volumeBarWidth * .5 - playPauseButton.getWidth() * .5 - 16 - prev.getWidth(), playPauseButton.getY());
        prev.renderWidget(mouseX, mouseY, 0);
        prev.fr = FontManager.music40;
        prev.fontOffsetY = 0;
        prev.setColor(Color.WHITE);

        prev.setOnClickCallback((x, y, i) -> {
            if (i == 0) {
                if (CloudMusic.player != null && CloudMusic.currentlyPlaying != null) CloudMusic.prev();
            }
            return true;
        });

        next.setAlpha(alpha);
        next.setWidth(32);
        next.setHeight(32);
        next.setPosition(volumeBarXOffset + volumeBarWidth * .5 + playPauseButton.getWidth() * .5 + 16, playPauseButton.getY());
        next.renderWidget(mouseX, mouseY, 0);
        next.fr = FontManager.music40;
        next.fontOffsetY = 0;
        next.setColor(Color.WHITE);

        next.setOnClickCallback((x, y, i) -> {
            if (i == 0) {
                if (CloudMusic.player != null && CloudMusic.currentlyPlaying != null) CloudMusic.next();
            }
            return true;
        });
    }

    public void mouseClicked(double mouseX, double mouseY, int mouseButton) {
        playPauseButton.onMouseClickReceived(mouseX, mouseY, mouseButton);
        prev.onMouseClickReceived(mouseX, mouseY, mouseButton);
        next.onMouseClickReceived(mouseX, mouseY, mouseButton);
    }

    private String formatDuration(float totalMillis) {
        float totalSeconds = totalMillis / 1000;

        float hours = totalSeconds / 3600;
        float minutes = (totalSeconds % 3600) / 60;
        float seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();

        if ((int) hours > 0) {
            sb.append(String.format("%02d:", (int) hours));
        }

        sb.append(String.format("%02d:", (int) minutes));
        sb.append(String.format("%02d", (int) seconds));

        return sb.toString();
    }

    private void renderBackground(double posX, double posY, double width, double height, float alpha) {
        TextureHandle musicCoverBlurred = CloudMusic.currentlyPlaying == null ? null : CloudMusic.currentlyPlaying.getBlurredCoverLocation();
        boolean hasBg = musicCoverBlurred != null && Platform.hasTexture(musicCoverBlurred);

        if (CloudMusic.currentlyPlaying != null && CloudMusic.currentlyPlaying != prevMusic) {
            if (prevMusic != null) musicBgAlpha = 0.0f;
            prevMusic = CloudMusic.currentlyPlaying;
            coverAlpha = 0.0f;
        }

        if (hasBg) {
            RenderContext.graphics().pose().pushMatrix();

            float lowFreqEnergy = calculateLowFrequencyEnergy();

            if (!Double.isFinite(fftScale)) fftScale = 0;
            if (!Float.isFinite(lowFreqEnergy) || lowFreqEnergy <= 0.01f) lowFreqEnergy = 0;

            float scaledEnergy = (float) Math.log1p(lowFreqEnergy * 10) * 0.05f;
            float damping = lowFreqEnergy > fftScale ? 0.3f : 0.6f;
            fftScale = Interpolations.interpolate(fftScale, scaledEnergy, damping);

            scaleAtPos(RenderSystem.getWidth() * .5, RenderSystem.getHeight() * .5, 1 + fftScale);

            double bgSize = Math.max(width, height);

            this.musicBgAlpha = Interpolations.interpolate(this.musicBgAlpha, 1.0f, 0.15f);
            Image.draw(musicCoverBlurred, posX + width * .5 - bgSize * .5, posY + height * .5 - bgSize * .5, bgSize, bgSize, Image.Type.NoColor, this.musicBgAlpha * alpha);

            RenderContext.graphics().pose().popMatrix();
        }

        Rect.draw(posX, posY, width, height, hexColor(0f, 0f, 0f, alpha * .42f));

        double fadeH = height * 0.22;
        RenderSystem.drawGradientRectTopToBottom(posX, posY, posX + width, posY + fadeH, hexColor(0f, 0f, 0f, alpha * .5f), hexColor(0f, 0f, 0f, 0f));
        RenderSystem.drawGradientRectTopToBottom(posX, posY + height - fadeH, posX + width, posY + height, hexColor(0f, 0f, 0f, 0f), hexColor(0f, 0f, 0f, alpha * .55f));

        double sideW = width * 0.16;
        RenderSystem.drawGradientRectLeftToRight(posX, posY, posX + sideW, posY + height, hexColor(0f, 0f, 0f, alpha * .35f), hexColor(0f, 0f, 0f, 0f));
        RenderSystem.drawGradientRectLeftToRight(posX + width - sideW, posY, posX + width, posY + height, hexColor(0f, 0f, 0f, 0f), hexColor(0f, 0f, 0f, alpha * .35f));
    }

    private float calculateLowFrequencyEnergy() {
        if (AudioPlayer.bandValues == null || AudioPlayer.bandValues.length == 0) {
            return 0.0f;
        }

        int lowFreqBands = Math.min(12, AudioPlayer.bandValues.length);

        float totalWeight = 0.0f;
        float weightedSum = 0.0f;

        for (int i = 0; i < lowFreqBands; i++) {
            float weight = (float) Math.exp(-i * 0.2f);
            float bandValue = AudioPlayer.bandValues[i];
            bandValue = Math.min(bandValue, 2.0f);
            weightedSum += bandValue * weight;
            totalWeight += weight;
        }

        if (totalWeight <= 0.0f) {
            return 0.0f;
        }

        float weightedAverage = weightedSum / totalWeight;

        float rms = 0.0f;
        for (int i = 0; i < lowFreqBands; i++) {
            float bandValue = AudioPlayer.bandValues[i];
            bandValue = Math.min(bandValue, 2.0f);
            rms += bandValue * bandValue;
        }
        rms = (float) Math.sqrt(rms / lowFreqBands);

        return (weightedAverage * 0.7f + rms * 0.3f);
    }
}
