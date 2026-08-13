package tritium.music.client.screens.ncm.panels;

import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.ui.widgets.LabelWidget;
import tritium.music.client.rendering.ui.widgets.RectWidget;
import tritium.music.client.rendering.ui.widgets.RoundedImageWidget;
import tritium.music.client.rendering.ui.widgets.RoundedRectWidget;
import tritium.music.client.screens.ncm.NCMScreen;
import tritium.music.core.CloudMusic;
import tritium.music.core.model.Music;
import tritium.music.core.model.PlayList;
import tritium.music.core.util.Textures;
import tritium.music.platform.Platform;
import tritium.music.platform.TextureHandle;

import java.awt.*;

public class MusicWidget extends RoundedRectWidget {

    public enum Style {
        LIST,
        GRID
    }

    public PlayList playList;
    public Music music;
    boolean coverLoaded = false;
    private final Style style;
    private double emphasizeAnim;
    private double playingAnimation;
    private double pressAnimation;
    private boolean leftPressed;
    private RoundedImageWidget gridCover;

    private final int index;
    private final long revealStart;
    private boolean entranceDone = false;

    private static final long ENTRANCE_BASE_DELAY_MS = 160;
    private static final long ENTRANCE_STAGGER_MS = 42;
    private static final long ENTRANCE_DURATION_MS = 520;
    private static final int ENTRANCE_INDEX_CAP = 16;
    private static final double ENTRANCE_SLIDE = 12;
    private static final double GRID_COVER_SIZE = 100;
    private static final double GRID_EMPHASIZE_MAX = 5;
    private static final double GRID_WIDTH = GRID_COVER_SIZE + GRID_EMPHASIZE_MAX;

    public MusicWidget(Music music, PlayList playList, int index, long revealStart, PlaylistPanel owner) {
        this(music, playList, index, revealStart, owner, Style.LIST);
    }

    public MusicWidget(Music music, PlayList playList, int index, long revealStart, PlaylistPanel owner, Style style) {
        super(0, 0, 0, 30);
        this.music = music;
        this.playList = playList;
        this.index = index;
        this.revealStart = revealStart;
        this.style = style;

        this.setTransformations(() -> {
            float ep = this.entranceProgress();
            RenderContext.graphics().pose().translate(0, (1f - ep) * (float) ENTRANCE_SLIDE);
            if (style == Style.GRID && pressAnimation > .001) {
                this.scaleAtPos(this.getX() + this.getWidth() * .5, this.getY() + this.getHeight() * .5, 1 - pressAnimation * .04);
            }
        });

        if (style == Style.GRID) {
            this.initGrid(owner);
            return;
        }

        RoundedRectWidget rrHoverIndicator = new RoundedRectWidget();
        this.addChild(rrHoverIndicator);
        rrHoverIndicator
                .setAlpha(0f)
                .setClickable(false);
        rrHoverIndicator.setBeforeRenderCallback(() -> rrHoverIndicator
                .setMargin(0)
                .setRadius(this.getRadius())
                .setColor(NCMScreen.getColor(NCMScreen.ColorType.ELEMENT_HOVER)));

        RoundedRectWidget rrPlayingIndicator = new RoundedRectWidget();
        this.addChild(rrPlayingIndicator);
        rrPlayingIndicator
                .setAlpha(0f)
                .setColor(0xFFD60017)
                .setClickable(false);
        if (CloudMusic.currentlyPlaying != null && CloudMusic.currentlyPlaying.getId() == music.getId()) {
            rrPlayingIndicator.setAlpha(1f);
        }
        rrPlayingIndicator.setBeforeRenderCallback(() -> rrPlayingIndicator
                .setMargin(0)
                .setRadius(this.getRadius()));

        this.setBeforeRenderCallback(() -> {

            if (!entranceDone) {
                float ep = this.entranceProgress();
                if (ep >= 1f) {
                    entranceDone = true;
                    this.setAlpha(1f);
                    this.setTransformations(null);
                } else {
                    this.setAlpha(ep);
                }
            }

            if (!coverLoaded) {
                coverLoaded = true;
                this.loadCover();
            }

            this.setBounds(this.getParentWidth(), 30);
            this.setColor(NCMScreen.getColor(index % 2 == 0 ? NCMScreen.ColorType.ELEMENT_BACKGROUND : NCMScreen.ColorType.GENERIC_BACKGROUND));

            if (CloudMusic.currentlyPlaying != null && CloudMusic.currentlyPlaying.getId() == music.getId()) {
                rrPlayingIndicator.setAlpha(Interpolations.interpolate(rrPlayingIndicator.getWidgetAlpha(), .9f, .4f));
                rrPlayingIndicator.setHidden(false);
            } else if (this.isHovering()) {
                rrHoverIndicator.setAlpha(Interpolations.interpolate(rrHoverIndicator.getWidgetAlpha(), 1, .3f));
                rrHoverIndicator.setHidden(false);
            } else {
                rrPlayingIndicator.setAlpha(Interpolations.interpolate(rrPlayingIndicator.getWidgetAlpha(), 0, .4f));
                rrHoverIndicator.setAlpha(Interpolations.interpolate(rrHoverIndicator.getWidgetAlpha(), 0, .3f));

                if (rrPlayingIndicator.getWidgetAlpha() <= .05f)
                    rrPlayingIndicator.setHidden(true);

                if (rrHoverIndicator.getWidgetAlpha() <= .05f)
                    rrHoverIndicator.setHidden(true);
            }

            this.setRadius(2);
        });

        this.setOnClickCallback((x, y, i) -> {
            if (i == 0) {
                CloudMusic.play(playList.getMusics(), index);
            } else if (i == 1) {
                owner.openMusicMenu(this, this.getX() + x, this.getY() + y);
            }
            return true;
        });

        RoundedImageWidget cover = new RoundedImageWidget(this.music.getSmallCoverLocation(), 0, 0, 0, 0);
        this.addChild(cover);
        cover.fadeIn();
        cover.setLinearFilter(true);
        cover.setBeforeRenderCallback(() -> {
            cover.setRadius(2);
            cover.setBounds(24, 24);
            cover.centerVertically();
            cover.setPosition(30, cover.getRelativeY());
        });
        cover.setClickable(false);

        LabelWidget lblMusicIndex = new LabelWidget(String.valueOf(index + 1), FontManager.pf14bold);
        this.addChild(lblMusicIndex);

        lblMusicIndex.setBeforeRenderCallback(() -> {
            if (CloudMusic.currentlyPlaying != null && CloudMusic.currentlyPlaying.getId() == music.getId())
                lblMusicIndex.setColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
            else
                lblMusicIndex.setColor(NCMScreen.getColor(NCMScreen.ColorType.SECONDARY_TEXT));
            lblMusicIndex.centerVertically();
            lblMusicIndex.setPosition(cover.getRelativeX() - 4 - lblMusicIndex.getWidth(), lblMusicIndex.getRelativeY());
        });

        lblMusicIndex.setClickable(false);

        boolean musicDirty = music.isDirty();
        double dirtyIndicatorSize = 8;

        String translatedNames = music.getTranslatedNames();

        LabelWidget lblMusicName = new LabelWidget(music.getName() + (translatedNames.isEmpty() ? "" : "§7" + " (" + translatedNames + ")"), FontManager.pf14bold);
        this.addChild(lblMusicName);

        lblMusicName
                .setWidthLimitType(LabelWidget.WidthLimitType.TRIM_TO_WIDTH)
                .setBeforeRenderCallback(() -> {
                    lblMusicName.setColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
                    lblMusicName.centerVertically();
                    lblMusicName.setPosition(cover.getRelativeX() + cover.getWidth() + 4, lblMusicName.getRelativeY() - lblMusicName.getHeight() * .5 - 2);
                    lblMusicName.setMaxWidth(this.getWidth() - (cover.getRelativeX() + cover.getWidth() + 4 + 32 + (musicDirty ? (dirtyIndicatorSize + 4) : 0)));
                });
        lblMusicName.setClickable(false);

        if (musicDirty) {
            RoundedRectWidget dirtyIndicator = new RoundedRectWidget(0, 0, dirtyIndicatorSize, dirtyIndicatorSize);
            this.addChild(dirtyIndicator);
            dirtyIndicator
                    .setRadius(1.5)
                    .setColor(Color.GRAY);

            dirtyIndicator.setBeforeRenderCallback(() -> dirtyIndicator.setPosition(lblMusicName.getRelativeX() + lblMusicName.getWidth() + 2, lblMusicName.getRelativeY() + lblMusicName.getHeight() * .5 - dirtyIndicatorSize * .5));

            dirtyIndicator.setClickable(false);

            LabelWidget lblDirty = new LabelWidget("E", FontManager.pf12bold);
            dirtyIndicator.addChild(lblDirty);
            lblDirty.setBeforeRenderCallback(() -> {
                lblDirty.setColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
                lblDirty.center();
            });
        }

        LabelWidget lblMusicArtist = new LabelWidget(music.getArtistsName() + " - " + music.getAlbum().getName(), FontManager.pf14);
        this.addChild(lblMusicArtist);

        lblMusicArtist
                .setWidthLimitType(LabelWidget.WidthLimitType.TRIM_TO_WIDTH)
                .setBeforeRenderCallback(() -> {
                    if (CloudMusic.currentlyPlaying != null && CloudMusic.currentlyPlaying.getId() == music.getId())
                        lblMusicArtist.setColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
                    else
                        lblMusicArtist.setColor(NCMScreen.getColor(NCMScreen.ColorType.SECONDARY_TEXT));
                    lblMusicArtist.centerVertically();
                    lblMusicArtist.setPosition(cover.getRelativeX() + cover.getWidth() + 4, lblMusicArtist.getRelativeY() + lblMusicArtist.getHeight() * .5 + 2);
                    lblMusicArtist.setMaxWidth(this.getWidth() - (cover.getRelativeX() + cover.getWidth() + 4 + 32));
                });

        lblMusicArtist.setClickable(false);

        LabelWidget lblMusicDuration = new LabelWidget(formatDuration(music.getDuration()), FontManager.pf14bold);
        this.addChild(lblMusicDuration);
        lblMusicDuration.setBeforeRenderCallback(() -> {
            if (CloudMusic.currentlyPlaying != null && CloudMusic.currentlyPlaying.getId() == music.getId())
                lblMusicDuration.setColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
            else
                lblMusicDuration.setColor(NCMScreen.getColor(NCMScreen.ColorType.SECONDARY_TEXT));
            lblMusicDuration.centerVertically();
            lblMusicDuration.setPosition(this.getWidth() - 8 - lblMusicDuration.getWidth(), lblMusicDuration.getRelativeY());
        });
        lblMusicDuration.setClickable(false);
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        if (style == Style.LIST) {
            super.onRender(mouseX, mouseY);
            return;
        }

        boolean playing = isPlaying();
        pressAnimation = Interpolations.interpolate(pressAnimation, leftPressed ? 1 : 0, leftPressed ? .45f : .28f);
        playingAnimation = Interpolations.interpolate(playingAnimation, playing ? 1 : 0, .3f);
        if (gridCover != null && playingAnimation > .01) {
            int alpha = (int) (255 * this.getAlpha() * playingAnimation);
            this.roundedOutline(
                    gridCover.getX(),
                    gridCover.getY(),
                    gridCover.getWidth() + .5,
                    gridCover.getHeight() + .5,
                    gridCover.getRadius() + 4,
                    1.5,
                    2,
                    new Color(214, 0, 23, alpha)
            );
        }
    }

    private void initGrid(PlaylistPanel owner) {
        String translatedNames = music.getTranslatedNames();
        double gridHeight = translatedNames.isEmpty() ? 121 : 133;
        this.setBounds(GRID_WIDTH, gridHeight);

        this.setBeforeRenderCallback(() -> {
            if (!entranceDone) {
                float ep = this.entranceProgress();
                if (ep >= 1f) {
                    entranceDone = true;
                    this.setAlpha(1f);
                } else {
                    this.setAlpha(ep);
                }
            }

            if (!coverLoaded) {
                coverLoaded = true;
                this.loadCover();
            }

            this.setBounds(GRID_WIDTH, gridHeight);
        });

        this.setOnClickCallback((x, y, mouseButton) -> {
            if (mouseButton == 0) {
                leftPressed = true;
            } else if (mouseButton == 1) {
                owner.openMusicMenu(this, this.getX() + x, this.getY() + y);
            }
            return true;
        });

        gridCover = new RoundedImageWidget(this.music.getGridCoverLocation(), 0, 0, GRID_COVER_SIZE, GRID_COVER_SIZE);
        this.addChild(gridCover);
        gridCover
                .setClickable(false)
                .fadeIn()
                .setLinearFilter(true)
                .setBeforeRenderCallback(() -> {
                    emphasizeAnim = Interpolations.interpolate(emphasizeAnim, gridCover.isHovering() ? GRID_EMPHASIZE_MAX : 0, .2f);
                    gridCover.setBounds(GRID_COVER_SIZE + emphasizeAnim);
                    gridCover.setRadius(4);
                    gridCover.centerHorizontally();
                    gridCover.setPosition(gridCover.getRelativeX(), GRID_WIDTH * .5 - GRID_COVER_SIZE * .5 - emphasizeAnim * .5);
                });

        RoundedRectWidget playingIndicator = new RoundedRectWidget(0, 0, 21, 15);
        this.addChild(playingIndicator);
        playingIndicator
                .setClickable(false)
                .setColor(0xFF18181A)
                .setRadius(4)
                .setAlpha(0f)
                .setBeforeRenderCallback(() -> {
                    playingIndicator.setAlpha(Interpolations.interpolate(playingIndicator.getWidgetAlpha(), isPlaying() ? .92f : 0f, .35f));
                    playingIndicator.setPosition(
                            gridCover.getRelativeX() + gridCover.getWidth() - playingIndicator.getWidth() - 5,
                            gridCover.getRelativeY() + 5
                    );
                });

        for (int i = 0; i < 3; i++) {
            int barIndex = i;
            RectWidget bar = new RectWidget();
            playingIndicator.addChild(bar);
            bar
                    .setClickable(false)
                    .setColor(0xFFD60017)
                    .setBeforeRenderCallback(() -> {
                        boolean active = CloudMusic.player != null && !CloudMusic.player.isPausing();
                        double phase = System.currentTimeMillis() * .012 + barIndex * 1.7;
                        double activity = active ? (Math.sin(phase) + 1) * .5 : 0;
                        double barHeight = 3 + activity * 6;
                        bar.setBounds(2, barHeight);
                        bar.setPosition(5 + barIndex * 4.5, playingIndicator.getHeight() - 3 - barHeight);
                    });
        }

        LabelWidget lblMusicName = new LabelWidget(music.getName(), FontManager.pf14bold);
        this.addChild(lblMusicName);
        lblMusicName
                .setClickable(false)
                .setWidthLimitType(LabelWidget.WidthLimitType.TRIM_TO_WIDTH)
                .setMaxWidth(GRID_COVER_SIZE)
                .setBeforeRenderCallback(() -> {
                    lblMusicName.setColor(isPlaying() ? 0xFFD60017 : NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
                    lblMusicName.setPosition(gridCover.getRelativeX(), gridCover.getRelativeY() + gridCover.getHeight() + 4);
                });

        if (!translatedNames.isEmpty()) {
            LabelWidget lblTranslatedName = new LabelWidget(translatedNames, FontManager.pf12);
            this.addChild(lblTranslatedName);
            lblTranslatedName
                    .setClickable(false)
                    .setWidthLimitType(LabelWidget.WidthLimitType.TRIM_TO_WIDTH)
                    .setMaxWidth(GRID_COVER_SIZE)
                    .setBeforeRenderCallback(() -> {
                        lblTranslatedName.setColor(NCMScreen.getColor(NCMScreen.ColorType.SECONDARY_TEXT));
                        lblTranslatedName.setPosition(
                                gridCover.getRelativeX(),
                                gridCover.getRelativeY() + gridCover.getHeight() + 6 + FontManager.pf14bold.getStringHeight(music.getName())
                        );
                    });
        }
    }

    public void onMouseReleased(double mouseX, double mouseY, int mouseButton, boolean insidePanel) {
        if (style != Style.GRID || mouseButton != 0) return;
        boolean shouldPlay = leftPressed && insidePanel && testHovered(mouseX, mouseY);
        leftPressed = false;
        if (shouldPlay) CloudMusic.play(playList.getMusics(), index);
    }

    private boolean isPlaying() {
        return CloudMusic.currentlyPlaying != null && CloudMusic.currentlyPlaying.getId() == music.getId();
    }

    private float entranceProgress() {
        long delay = ENTRANCE_BASE_DELAY_MS + (Math.min(index, ENTRANCE_INDEX_CAP) * ENTRANCE_STAGGER_MS);
        long elapsed = System.currentTimeMillis() - revealStart - delay;

        if (elapsed <= 0L) {
            return 0f;
        }
        if (elapsed >= ENTRANCE_DURATION_MS) {
            return 1f;
        }

        float t = elapsed / (float) ENTRANCE_DURATION_MS;
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private String formatDuration(long totalMillis) {
        long totalSeconds = totalMillis / 1000;

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();

        if (hours > 0) {
            sb.append(String.format("%02d:", hours));
        }

        sb.append(String.format("%02d:", minutes));
        sb.append(String.format("%02d", seconds));

        return sb.toString();
    }

    private void loadCover() {
        TextureHandle coverLoc = style == Style.GRID ? this.music.getGridCoverLocation() : this.music.getSmallCoverLocation();
        if (Platform.hasTexture(coverLoc))
            return;

        Textures.downloadTextureAndLoadAsync(music.getCoverUrl(style == Style.GRID ? 256 : 64), coverLoc);
    }
}
