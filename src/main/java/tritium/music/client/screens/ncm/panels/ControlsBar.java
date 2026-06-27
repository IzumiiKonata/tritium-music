package tritium.music.client.screens.ncm.panels;

import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.ui.widgets.IconWidget;
import tritium.music.client.rendering.ui.widgets.LabelWidget;
import tritium.music.client.rendering.ui.widgets.RectWidget;
import tritium.music.client.rendering.ui.widgets.RoundedImageWidget;
import tritium.music.client.rendering.ui.widgets.RoundedRectWidget;
import tritium.music.client.screens.ncm.MusicLyricsPanel;
import tritium.music.client.screens.ncm.NCMPanel;
import tritium.music.client.screens.ncm.NCMScreen;
import tritium.music.client.util.MouseUtil;
import tritium.music.core.CloudMusic;
import tritium.music.core.audio.AudioPlayer;

public class ControlsBar extends NCMPanel {

    private float coverHoverAnim = 0f;

    public ControlsBar() {
    }

    @Override
    public void onInit() {
        RectWidget bg = new RectWidget();

        this.addChild(bg);

        bg.setColor(NCMScreen.getColor(NCMScreen.ColorType.GENERIC_BACKGROUND))
                .setAlpha(.96f)
                .setBeforeRenderCallback(() -> bg.setMargin(0));

        RoundedImageWidget playingCover = new RoundedImageWidget(() -> {
            if (CloudMusic.currentlyPlaying == null)
                return null;

            return CloudMusic.currentlyPlaying.getSmallCoverLocation();
        }, 0, 0, 0, 0);

        this.addChild(playingCover);

        playingCover
                .fadeIn()
                .setLinearFilter(true)
                .setShouldOverrideMouseCursor(true)
                .setBeforeRenderCallback(() -> {
                    coverHoverAnim = Interpolations.interpolate(coverHoverAnim, playingCover.isHovering() ? 1f : 0f, 0.3f);
                    playingCover
                            .setMargin(5)
                            .setBounds(playingCover.getHeight(), playingCover.getHeight())
                            .setRadius(2);
                })
                .setOnClickCallback((relativeX, relativeY, mouseButton) -> {
                    if (CloudMusic.currentlyPlaying != null) {
                        NCMScreen.getInstance().musicLyricsPanel = new MusicLyricsPanel(CloudMusic.currentlyPlaying);
                    }

                    return true;
                });

        playingCover.setTransformations(() -> {
            if (coverHoverAnim > 0.001f) {
                playingCover.scaleAtPos(playingCover.getX() + playingCover.getWidth() * 0.5, playingCover.getY() + playingCover.getHeight() * 0.5, 1 + coverHoverAnim * 0.08);
            }
        });

        double buttonsYOffset = -4;

        IconWidget playPause = new IconWidget("B", FontManager.icon30, 0, 0, 20, 20);

        this.addChild(playPause);

        playPause
                .setBeforeRenderCallback(() -> {
                    boolean showPausingIcon = CloudMusic.player == null || CloudMusic.player.isPausing();

                    playPause
                            .center()
                            .setIcon(showPausingIcon ? "B" : "A")
                            .setPosition(playPause.getRelativeX(), playPause.getRelativeY() + buttonsYOffset)
                            .setColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
                })
                .setOnClickCallback((x, y, i) -> {
                    boolean hasCurrentlyPlaying = CloudMusic.player != null && CloudMusic.currentlyPlaying != null;
                    if (hasCurrentlyPlaying) {
                        if (CloudMusic.player.isPausing())
                            CloudMusic.player.unpause();
                        else
                            CloudMusic.player.pause();
                    }
                    return true;
                });

        IconWidget prev = new IconWidget("H", FontManager.icon30, 0, 0, 20, 20);

        this.addChild(prev);

        prev
                .setOnClickCallback((x, y, i) -> {
                    if (CloudMusic.player != null && CloudMusic.currentlyPlaying != null)
                        CloudMusic.prev();

                    return true;
                })
                .setBeforeRenderCallback(() -> prev
                        .center()
                        .setColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT))
                        .setPosition(prev.getRelativeX() - 20 - prev.getWidth() * .5, prev.getRelativeY() + buttonsYOffset));

        IconWidget next = new IconWidget("E", FontManager.icon30, 0, 0, 20, 20);
        this.addChild(next);

        next
                .setOnClickCallback((x, y, i) -> {
                    if (CloudMusic.player != null && CloudMusic.currentlyPlaying != null)
                        CloudMusic.next();

                    return true;
                })
                .setBeforeRenderCallback(() -> next
                        .center()
                        .setPosition(next.getRelativeX() + next.getWidth() * .5 + 20, next.getRelativeY() + buttonsYOffset)
                        .setColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT)));

        RoundedRectWidget progressBarBg = new RoundedRectWidget() {

            boolean prevMouse = false;

            @Override
            public void onRender(double mouseX, double mouseY) {
                super.onRender(mouseX, mouseY);

                if (prevMouse && !MouseUtil.isLeftDown())
                    prevMouse = false;

                if (this.testHovered(mouseX, mouseY, 1) && MouseUtil.isLeftDown() && !prevMouse) {
                    prevMouse = true;
                    double xDelta = Math.max(0, Math.min(this.getWidth(), (mouseX - this.getX())));
                    double percent = xDelta / this.getWidth();

                    if (CloudMusic.player != null) {
                        float progress = (float) (percent * CloudMusic.player.getTotalTimeMillis());
                        CloudMusic.player.setPlaybackTime(progress);
                        MusicLyricsPanel.resetProgress(progress);
                    }
                }
            }
        };

        this.addChild(progressBarBg);

        progressBarBg
                .setColor(0xFFFFFFFF)
                .setAlpha(0.2f)
                .setRadius(1)
                .setBounds(135, 3)
                .setShouldOverrideMouseCursor(true)
                .setBeforeRenderCallback(() -> progressBarBg
                        .center()
                        .setPosition(progressBarBg.getRelativeX(), progressBarBg.getRelativeY() + 8));

        RoundedRectWidget progressBar = new RoundedRectWidget();

        progressBarBg.addChild(progressBar);
        progressBar
                .setColor(-1)
                .setWidth(0)
                .setClickable(false)
                .setBeforeRenderCallback(() -> {
                    progressBar.setMargin(0);

                    AudioPlayer player = CloudMusic.player;
                    if (player == null)
                        return;

                    float perc = player.getCurrentTimeMillis() / player.getTotalTimeMillis();
                    progressBar
                            .setWidth(perc * progressBarBg.getWidth())
                            .setRadius(perc);
                });

        LabelWidget lblCurTime = new LabelWidget(
                () -> {
                    if (CloudMusic.player == null)
                        return "00:00";
                    return formatDuration(CloudMusic.player.getCurrentTimeMillis());
                },
                FontManager.pf12
        );
        this.addChild(lblCurTime);

        lblCurTime
                .setClickable(false)
                .setBeforeRenderCallback(() -> lblCurTime
                        .setColor(NCMScreen.getColor(NCMScreen.ColorType.SECONDARY_TEXT))
                        .setPosition(
                                progressBarBg.getRelativeX() - lblCurTime.getWidth() - 4,
                                progressBarBg.getRelativeY() + progressBarBg.getHeight() * .5 - lblCurTime.getHeight() * .5
                        ));

        LabelWidget lblRemainingTime = new LabelWidget(
                () -> {
                    if (CloudMusic.player == null)
                        return "00:00";
                    return "-" + formatDuration(CloudMusic.player.getTotalTimeMillis() - CloudMusic.player.getCurrentTimeMillis());
                },
                FontManager.pf12
        );
        this.addChild(lblRemainingTime);

        lblRemainingTime
                .setClickable(false)
                .setBeforeRenderCallback(() -> lblRemainingTime
                        .setColor(NCMScreen.getColor(NCMScreen.ColorType.SECONDARY_TEXT))
                        .setPosition(progressBarBg.getRelativeX() + progressBarBg.getWidth() + 4, lblCurTime.getRelativeY()));

        LabelWidget lblMusicName = new LabelWidget(() -> CloudMusic.currentlyPlaying == null ? "未在播放" : CloudMusic.currentlyPlaying.getName(), FontManager.pf14bold);
        this.addChild(lblMusicName);

        lblMusicName
                .setClickable(false)
                .setBeforeRenderCallback(() -> lblMusicName
                        .centerVertically()
                        .setColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT))
                        .setMaxWidth(lblCurTime.getRelativeX() - lblMusicName.getRelativeX() - 4)
                        .setPosition(
                                playingCover.getRelativeX() + playingCover.getWidth() + 4,
                                lblMusicName.getRelativeY() - lblMusicName.getHeight() * .5 - 2
                        ));

        LabelWidget lblMusicArtist = new LabelWidget(
                () -> {
                    if (CloudMusic.currentlyPlaying == null)
                        return "无";
                    return CloudMusic.currentlyPlaying.getArtistsName() + " - " + CloudMusic.currentlyPlaying.getAlbum().getName();
                },
                FontManager.pf14bold
        );
        this.addChild(lblMusicArtist);

        lblMusicArtist
                .setClickable(false)
                .setBeforeRenderCallback(() -> lblMusicArtist
                        .centerVertically()
                        .setColor(NCMScreen.getColor(NCMScreen.ColorType.SECONDARY_TEXT))
                        .setMaxWidth(lblCurTime.getRelativeX() - lblMusicArtist.getRelativeX() - 4)
                        .setPosition(
                                playingCover.getRelativeX() + playingCover.getWidth() + 4,
                                lblMusicArtist.getRelativeY() + lblMusicArtist.getHeight() * .5 + 2
                        ));
    }

    private String formatDuration(float totalMillis) {
        int totalSeconds = (int) (totalMillis / 1000);
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        String result = "";

        if (hours > 0) {
            result += (hours < 10 ? "0" : "") + hours + ":";
        }

        result += (minutes < 10 ? "0" : "") + minutes + ":";
        result += (seconds < 10 ? "0" : "") + seconds;

        return result;
    }
}
