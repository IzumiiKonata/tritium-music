package tritium.music.client.screens.ncm.panels;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.ui.container.Panel;
import tritium.music.client.rendering.ui.container.ScrollPanel;
import tritium.music.client.rendering.ui.widgets.LabelWidget;
import tritium.music.client.rendering.ui.widgets.RoundedButtonWidget;
import tritium.music.client.rendering.ui.widgets.RoundedImageWidget;
import tritium.music.client.rendering.ui.widgets.RoundedRectWidget;
import tritium.music.client.rendering.ui.widgets.TextFieldWidget;
import tritium.music.client.screens.ncm.NCMPanel;
import tritium.music.client.screens.ncm.NCMScreen;
import tritium.music.core.CloudMusic;
import tritium.music.core.model.Music;
import tritium.music.core.model.PlayList;
import tritium.music.core.util.Textures;
import tritium.music.platform.Platform;
import tritium.music.platform.TextureHandle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PlaylistPanel extends NCMPanel {

    public PlayList playList;

    public PlaylistPanel(PlayList playlist) {
        this.playList = playlist;
    }

    private TextFieldWidget tfSearch;
    private double tfOpenAnimation = 20;

    private static boolean isCtrlDown() {
        long handle = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    @Override
    public void onInit() {

        double musicsContainerOffsetY;

        if (!playList.isSearchMode()) {
            RoundedImageWidget cover = new RoundedImageWidget(this.playList.getCoverLocation(), 0, 0, 0, 0);

            cover.setPosition(24, 24);
            cover.setBounds(128, 128);
            cover.fadeIn();
            cover.setLinearFilter(true);

            this.addChild(cover);
            this.loadCover();

            cover.setBeforeRenderCallback(() -> cover.setRadius(4));

            RoundedButtonWidget btnPlay = new RoundedButtonWidget("播放歌单", FontManager.pf16bold);
            this.addChild(btnPlay);

            btnPlay.setBeforeRenderCallback(() -> {
                btnPlay.setBounds(57, 17);
                btnPlay.setPosition(cover.getRelativeX() + cover.getWidth() + 12, cover.getRelativeY() + cover.getHeight() - btnPlay.getHeight());
                btnPlay.setRadius(3);
                btnPlay.setColor(0xFFd60017);
                btnPlay.setTextColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
            });

            btnPlay.setOnClickCallback((relativeX, relativeY, mouseButton) -> {
                if (mouseButton == 0) {
                    playList.loadMusicsWithCallback(musics -> CloudMusic.play(musics, 0));
                }
                return true;
            });

            RoundedButtonWidget btnPlayRandomOrder = new RoundedButtonWidget("乱序播放歌单", FontManager.pf16bold);
            this.addChild(btnPlayRandomOrder);

            btnPlayRandomOrder.setBeforeRenderCallback(() -> {
                btnPlayRandomOrder.setBounds(57, 17);
                btnPlayRandomOrder.setPosition(cover.getRelativeX() + cover.getWidth() + 12 + btnPlay.getWidth() + 8, cover.getRelativeY() + cover.getHeight() - btnPlayRandomOrder.getHeight());
                btnPlayRandomOrder.setRadius(3);
                btnPlayRandomOrder.setColor(0xFFd60017);
                btnPlayRandomOrder.setTextColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
            });

            btnPlayRandomOrder.setOnClickCallback((relativeX, relativeY, mouseButton) -> {
                if (mouseButton == 0) {
                    playList.loadMusicsWithCallback(musics -> {
                        ArrayList<Music> music = new ArrayList<>(musics);
                        Collections.shuffle(music);
                        CloudMusic.play(music, 0);
                    });
                }
                return true;
            });

            RoundedRectWidget searchBar = new RoundedRectWidget();
            this.addChild(searchBar);

            searchBar
                    .setShouldOverrideMouseCursor(true)
                    .setOnClickCallback((relativeX, relativeY, mouseButton) -> {
                        if (mouseButton == 0) {
                            if (!this.tfSearch.isFocused()) {
                                this.tfSearch.setFocused(true);
                            }
                        }
                        return true;
                    })
                    .setBeforeRenderCallback(() -> {
                        tfOpenAnimation = Interpolations.interpolate(tfOpenAnimation, this.tfSearch.isFocused() ? 80 : 20, .3f);

                        this.tfSearch.setHidden(!this.tfSearch.isFocused() && tfOpenAnimation < 21);

                        searchBar
                                .setAlpha(1f)
                                .setColor(0xFF5E5E5E)
                                .setWidth(tfOpenAnimation)
                                .setHeight(btnPlayRandomOrder.getHeight())
                                .setRadius(7)
                                .setPosition(btnPlayRandomOrder.getRelativeX() + btnPlayRandomOrder.getWidth() + 8, btnPlayRandomOrder.getRelativeY());
                    });

            RoundedRectWidget searchBarBg = new RoundedRectWidget();
            searchBar.addChild(searchBarBg);
            searchBarBg
                    .setClickable(false)
                    .setBeforeRenderCallback(() -> {
                        searchBarBg
                                .setMargin(.5)
                                .setAlpha(.6f)
                                .setRadius(searchBar.getRadius() - .5);
                        searchBar.setColor(0xFF292727);
                    });

            LabelWidget lblSearchIcon = new LabelWidget("K", FontManager.music18);
            searchBar.addChild(lblSearchIcon);
            lblSearchIcon
                    .setClickable(false)
                    .setColor(hexColor(100, 100, 100))
                    .setBeforeRenderCallback(() -> lblSearchIcon
                            .centerVertically()
                            .setPosition(lblSearchIcon.getRelativeY(), lblSearchIcon.getRelativeY()));

            this.tfSearch = new TextFieldWidget(FontManager.pf14bold);
            searchBar.addChild(tfSearch);

            this.tfSearch.setOnKeyTypedCallback((character, keyCode) -> {
                if (this.tfSearch.isFocused()) {
                    if (keyCode == GLFW.GLFW_KEY_ESCAPE)
                        this.tfSearch.setFocused(false);
                    return true;
                }
                return false;
            });

            this.setOnKeyTypedCallback((character, keyCode) -> {
                if (isCtrlDown() && keyCode == GLFW.GLFW_KEY_G) {
                    this.tfSearch.setFocused(true);
                    return true;
                }
                return false;
            });

            tfSearch.setBeforeRenderCallback(() -> {
                tfSearch.drawUnderline(false);
                tfSearch.setMargin(2);
                double xSpacing = lblSearchIcon.getRelativeX() + lblSearchIcon.getWidth() + 4;
                tfSearch.setBounds(xSpacing, tfSearch.getRelativeY(), tfSearch.getWidth() - xSpacing, tfSearch.getHeight());
                tfSearch.setColor(this.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
                tfSearch.setDisabledTextColor(RenderSystem.reAlpha(this.getColor(NCMScreen.ColorType.PRIMARY_TEXT), .4f));
            });

            RoundedImageWidget creatorAvatar = new RoundedImageWidget(this.playList.getCreator().getAvatarLocation(), 0, 0, 0, 0);
            this.addChild(creatorAvatar);
            creatorAvatar.fadeIn();
            creatorAvatar.setLinearFilter(true);

            this.loadAvatar();

            creatorAvatar.setBeforeRenderCallback(() -> {
                creatorAvatar.setBounds(16, 16);
                creatorAvatar.setPosition(cover.getRelativeX() + cover.getWidth() + 12, btnPlay.getRelativeY() - 6 - creatorAvatar.getHeight());
                creatorAvatar.setRadius(7.25);
            });

            LabelWidget lblCreator = new LabelWidget(playList.getCreator().getName(), FontManager.pf16bold);
            this.addChild(lblCreator);

            lblCreator.setBeforeRenderCallback(() -> {
                lblCreator.setPosition(creatorAvatar.getRelativeX() + creatorAvatar.getWidth() + 4, creatorAvatar.getRelativeY() + creatorAvatar.getHeight() * .5 - lblCreator.getHeight() * .5);
                lblCreator.setColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
            });

            LabelWidget lblPlaylistInfo = new LabelWidget(this::getPlayListInfo, FontManager.pf12);
            this.addChild(lblPlaylistInfo);

            lblPlaylistInfo.setBeforeRenderCallback(() -> {
                lblPlaylistInfo.setPosition(cover.getRelativeX() + cover.getWidth() + 12, creatorAvatar.getRelativeY() - 8 - lblPlaylistInfo.getHeight());
                lblPlaylistInfo.setColor(NCMScreen.getColor(NCMScreen.ColorType.SECONDARY_TEXT));
            });

            LabelWidget lblPlaylistName = new LabelWidget(playList.getName(), FontManager.pf32);
            this.addChild(lblPlaylistName);

            lblPlaylistName.setBeforeRenderCallback(() -> {
                lblPlaylistName.setPosition(cover.getRelativeX() + cover.getWidth() + 12, lblPlaylistInfo.getRelativeY() - 4 - lblPlaylistName.getHeight());
                lblPlaylistName.setColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
            });

            musicsContainerOffsetY = cover.getRelativeY() + cover.getHeight() + 24;
        } else {
            musicsContainerOffsetY = 18;
        }

        Panel rwMusicsContainer = new Panel();

        this.addChild(rwMusicsContainer);

        rwMusicsContainer.setBeforeRenderCallback(() -> {
            rwMusicsContainer.setBounds(this.getWidth() - 36, this.getHeight() - (musicsContainerOffsetY));
            rwMusicsContainer.centerHorizontally();
            rwMusicsContainer.setPosition(rwMusicsContainer.getRelativeX(), musicsContainerOffsetY);
        });

        ScrollPanel musicsPanel = new ScrollPanel();

        rwMusicsContainer.addChild(musicsPanel);
        musicsPanel.setSpacing(0);

        musicsPanel.setBeforeRenderCallback(() -> musicsPanel.setMargin(0));

        playList.loadMusicsWithCallback(musics -> {
            long revealStart = System.currentTimeMillis();
            musicsPanel.addChild(musics.stream().map(music -> new MusicWidget(music, playList, playList.getMusics().indexOf(music), revealStart).setShouldOverrideMouseCursor(true)).collect(Collectors.toList()));
        });

        if (this.tfSearch != null) {
            this.tfSearch.setTextChangedCallback(text -> {
                if (text.isEmpty()) {
                    musicsPanel.getChildren().forEach(child -> child.setHidden(false));
                } else {
                    musicsPanel.getChildren()
                            .stream()
                            .filter(child -> child instanceof MusicWidget)
                            .map(child -> (MusicWidget) child)
                            .forEach(widget -> {
                                if (
                                        widget.music.getName().toLowerCase().contains(text.toLowerCase()) ||
                                                widget.music.getTranslatedNames().toLowerCase().contains(text.toLowerCase()) ||
                                                widget.music.getArtists().stream().anyMatch(artist -> artist != null && artist.getName() != null && artist.getName().toLowerCase().contains(text.toLowerCase())) ||
                                                (widget.music.getAlbum() != null && widget.music.getAlbum().getName() != null && widget.music.getAlbum().getName().toLowerCase().contains(text.toLowerCase()))
                                ) {
                                    widget.setHidden(false);
                                } else {
                                    widget.setHidden(true);
                                }
                            });
                }
            });
        }
    }

    private String formatDuration(long totalMillis) {
        long totalSeconds = totalMillis / 1000;

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();

        if (hours > 0) {
            sb.append(String.format("%02d时", hours));
        }

        if (minutes > 0) {
            sb.append(String.format("%02d分", minutes));
        }

        sb.append(String.format("%02d秒", seconds));

        return sb.toString();
    }

    String cached = "";
    int lastSize = -1;

    private String getPlayListInfo() {
        if (!playList.musicsLoaded)
            return "";

        List<Music> musics = playList.musics;

        if (lastSize != musics.size()) {
            lastSize = musics.size();
            if (musics.isEmpty()) {
                cached = playList.getCount() + "首歌曲";
            } else {
                cached = musics.size() + "首歌曲 · " + this.formatDuration(musics.stream().mapToLong(Music::getDuration).sum());
            }
        }

        return cached;
    }

    private void loadCover() {
        TextureHandle coverLoc = this.playList.getCoverLocation();
        if (Platform.hasTexture(coverLoc))
            return;

        Textures.downloadTextureAndLoadAsync(playList.getCoverUrl() + "?param=256y256", coverLoc);
    }

    private void loadAvatar() {
        TextureHandle avatarLoc = this.playList.getCreator().getAvatarLocation();
        if (Platform.hasTexture(avatarLoc))
            return;

        Textures.downloadTextureAndLoadAsync(playList.getCreator().getAvatarUrl() + "?param=32y32", avatarLoc);
    }
}
