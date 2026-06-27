package tritium.music.client.rendering.hud;

import tritium.music.client.rendering.RGBA;
import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.ScrollText;
import tritium.music.client.rendering.StencilClipManager;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.core.CloudMusic;
import tritium.music.core.MusicState;
import tritium.music.core.lyric.LyricLine;
import tritium.music.core.model.Music;
import tritium.music.fabric.ui.Identifiers;
import tritium.music.platform.Platform;
import tritium.music.platform.TextureHandle;

import java.awt.Color;
import java.time.Duration;

public class MusicInfoWidget extends HudWidget {

    private final boolean turnComposerIntoLyric = false;

    private float alpha = 0.0f;

    private final ScrollText musicName = new ScrollText();
    private final ScrollText artists = new ScrollText();

    private double downloadProgHeight = 0;
    private float downloadPanelAlpha = 0.0f;

    private float musicBgAlpha = 0.0f;
    private boolean prevBlurredBg = false;
    private boolean prevBg = false;
    private Music prevMusic = null;

    public MusicInfoWidget() {
        super("Music");
    }

    @Override
    public tritium.music.client.config.WidgetConfig.WidgetSettings settings() {
        return tritium.music.client.config.WidgetConfig.get().musicInfo;
    }

    @Override
    public void onRender() {

        double width = 230;
        double height = 56;

        Music playingMusic = CloudMusic.currentlyPlaying;

        boolean playing = playingMusic != null && CloudMusic.player != null && !CloudMusic.player.isFinished();

        alpha = Interpolations.interpolate(alpha, playing ? 1 : 0, playing ? 0.15f : 0.2f);

        boolean downloading = MusicState.get().isDownloading();
        double downloadProgress = MusicState.get().getDownloadProgress();
        String downloadSpeed = MusicState.get().getDownloadSpeed();

        this.downloadProgHeight = Interpolations.interpolate(this.downloadProgHeight, downloading ? (playing ? 26 : -26) : 0, 0.2f);
        this.downloadPanelAlpha = Interpolations.interpolate(this.downloadPanelAlpha, downloading ? 1.0f : 0.0f, 0.4f);

        if (playingMusic != null) {

            TextureHandle cover = playingMusic.getSmallCoverLocation();

            double imgSpacing = 4;

            double imgX = this.getX() + imgSpacing;

            float y = (float) (this.getY() + downloadProgHeight);
            double imgY = y + imgSpacing;

            double imgSize = height - imgSpacing * 2;

            double coverRound = 6;
            double bgRound = coverRound * 1.75;

            {

                double posX = this.getX();
                double posY = this.getY();

                TextureHandle musicCoverBlured = playingMusic.getBlurredCoverLocation();
                boolean hasBg = Platform.hasTexture(musicCoverBlured);

                if (hasBg || prevBlurredBg) {

                    if (playingMusic != prevMusic) {
                        prevBlurredBg = prevMusic != null && Platform.hasTexture(prevMusic.getBlurredCoverLocation());
                        prevBg = prevMusic != null && Platform.hasTexture(prevMusic.getCoverLocation());
                        prevMusic = playingMusic;
                        musicBgAlpha = 0.0f;
                    }

                    double v = (height) / width;

                    if (prevBlurredBg && musicBgAlpha < 0.99f) {
                        RenderSystem.bindTexture(Identifiers.of(prevMusic.getBlurredCoverLocation()));
                        this.roundedRectTextured(posX, posY, width, height + downloadProgHeight, 0, v, 1, v, bgRound, 1, alpha);
                    }

                    if (hasBg) {
                        this.musicBgAlpha = Interpolations.interpolate(this.musicBgAlpha, 1.0f, 0.3f);
                        RenderSystem.bindTexture(Identifiers.of(musicCoverBlured));
                        this.roundedRectTextured(posX, posY, width, height + downloadProgHeight, 0, .5 - v * .5, 1, v, bgRound, 1, this.musicBgAlpha * alpha);
                    }

                }
            }

            this.roundedRect(this.getX(), this.getY(), width, height + downloadProgHeight, bgRound, 1, 0, 0, 0, alpha * 0.25f);

            if (downloading) {

                double offsetY = this.getY() + imgSpacing;

                CFontRenderer fr = FontManager.pf18bold;

                fr.drawString("Downloading...", imgX, offsetY, new Color(1f, 1f, 1f, downloadPanelAlpha).getRGB());
                fr.drawString(downloadSpeed, imgX + width - imgSpacing * 2 - fr.getStringWidthD(downloadSpeed), offsetY, new Color(1f, 1f, 1f, downloadPanelAlpha).getRGB());

                this.roundedRect(imgX, offsetY + fr.getHeight() + 4, width - imgSpacing * 2, 6, 2, 1, 1, 1, downloadPanelAlpha * 0.25f);

                StencilClipManager.beginClip(() -> Rect.draw(imgX, offsetY + fr.getHeight() + 4, (width - imgSpacing * 2) * downloadProgress, 6, -1));

                this.roundedRect(imgX, offsetY + fr.getHeight() + 4, width - imgSpacing * 2, 6, 2, 1, 1, 1, downloadPanelAlpha);

                StencilClipManager.endClip();
            }

            if (prevBg && prevMusic != null) {
                RenderSystem.bindTexture(Identifiers.of(prevMusic.getCoverLocation()));
                this.roundedRectTextured(imgX, imgY, imgSize, imgSize, coverRound, alpha);
            }

            if (Platform.hasTexture(cover)) {
                RenderSystem.bindTexture(Identifiers.of(cover));
                this.roundedRectTextured(imgX, imgY, imgSize, imgSize, coverRound, this.musicBgAlpha * alpha);
            }

            String secondaryText = playingMusic.getArtistsName();

            if (this.turnComposerIntoLyric && CloudMusic.player != null) {
                LyricLine currentDisplaying = CloudMusic.currentLyric;
                LyricLine next = null;

                if (!CloudMusic.lyrics.isEmpty()) {
                    int currentIndex = CloudMusic.lyrics.indexOf(currentDisplaying);
                    if (currentIndex >= 0 && currentIndex < CloudMusic.lyrics.size() - 1) {
                        next = CloudMusic.lyrics.get(currentIndex + 1);
                    }
                }

                if (currentDisplaying != null) {
                    secondaryText = currentDisplaying.getLyric();
                    artists.setWaitTime(100L);
                    artists.setOneShot(true);

                    if (next != null) {
                        artists.anim.setDuration(Duration.ofMillis(next.timestamp - currentDisplaying.timestamp - 500));
                    } else {
                        artists.anim.setDuration(Duration.ofMillis((long) (CloudMusic.player.getCurrentTimeMillis() - currentDisplaying.timestamp - 500)));
                    }

                } else {
                    artists.setWaitTime(2000L);
                    artists.setOneShot(false);
                    artists.anim.setDuration(Duration.ofMillis(0));
                }
            } else {
                artists.setWaitTime(2000L);
                artists.setOneShot(false);
                artists.anim.setDuration(Duration.ofMillis(0));
            }

            double progressBarWidth = width - (imgSize + imgSpacing * 3.25);

            String name1 = playingMusic.getName();

            double musicNameY = imgY + 3;
            musicName.render(FontManager.pf25bold, name1, imgX + imgSize + imgSpacing, musicNameY, progressBarWidth, new Color(1f, 1f, 1f, alpha).getRGB());

            double progressBarOffsetY = y + height - imgSpacing - 3 - FontManager.pf14bold.getFontHeight() - 8;

            artists.render(FontManager.pf20, secondaryText, imgX + imgSize + imgSpacing, musicNameY + FontManager.pf25bold.getFontHeight() + (progressBarOffsetY - (musicNameY + FontManager.pf25bold.getFontHeight())) * .5 - FontManager.pf20.getFontHeight() * .5, progressBarWidth, new Color(1f, 1f, 1f, alpha * 0.8f).getRGB());

            this.roundedRect(imgX + imgSize + imgSpacing, progressBarOffsetY, progressBarWidth, 5, 1, 1f, 1f, 1f, alpha * 0.3f);

            if (CloudMusic.player != null) {
                StencilClipManager.beginClip(() -> Rect.draw(imgX + imgSize + imgSpacing, progressBarOffsetY, (progressBarWidth) * ((double) CloudMusic.player.getCurrentTimeMillis() / CloudMusic.player.getTotalTimeMillis()), 6, -1));
                this.roundedRect(imgX + imgSize + imgSpacing, progressBarOffsetY, progressBarWidth, 5, 1, 233, 233, 233, (int) (alpha * 255));
                StencilClipManager.endClip();

                int cMin = (int) (CloudMusic.player.getCurrentTimeSeconds() / 60);
                int cSec = (int) (CloudMusic.player.getCurrentTimeSeconds() - cMin * 60);
                String currentTime = (cMin < 10 ? "0" + cMin : cMin) + ":" + (cSec < 10 ? "0" + cSec : cSec);
                int tMin = (int) (CloudMusic.player.getTotalTimeSeconds() / 60);
                int tSec = (int) (CloudMusic.player.getTotalTimeSeconds() - tMin * 60);
                String totalTime = (tMin < 10 ? "0" + tMin : tMin) + ":" + (tSec < 10 ? "0" + tSec : tSec);

                int textColor = RGBA.color(255, 255, 255, (int) (alpha * 128));
                double playbackTimeY = progressBarOffsetY + 9;
                FontManager.pf14bold.drawString(currentTime, imgX + imgSize + imgSpacing, playbackTimeY, textColor);
                FontManager.pf14bold.drawString(totalTime, imgX + imgSize + imgSpacing + progressBarWidth - FontManager.pf14bold.getStringWidthD(totalTime), playbackTimeY, textColor);

            }
        }

        this.setWidth(width);
        this.setHeight(height + downloadProgHeight);
    }
}
