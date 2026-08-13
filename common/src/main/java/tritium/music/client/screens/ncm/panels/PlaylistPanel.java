package tritium.music.client.screens.ncm.panels;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import org.lwjgl.glfw.GLFW;
import tritium.music.client.config.WidgetConfig;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.ui.container.Panel;
import tritium.music.client.rendering.ui.container.ScrollPanel;
import tritium.music.client.rendering.ui.widgets.*;
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

public class PlaylistPanel extends NCMPanel {

    public PlayList playList;

    public PlaylistPanel(PlayList playlist) {
        this.playList = playlist;
    }

    private TextFieldWidget tfSearch;
    private final ContextMenuWidget contextMenu = new ContextMenuWidget();
    private double tfOpenAnimation = 20;
    private ScrollPanel musicsPanel;
    private List<Music> loadedMusics = List.of();

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

            RoundedButtonWidget btnPlay = new RoundedButtonWidget(I18n.get("tritium-music.ui.playlist.play"), FontManager.pf16bold);
            this.addChild(btnPlay);

            btnPlay.setBeforeRenderCallback(() -> {
                btnPlay.setBounds(Math.max(57, FontManager.pf16bold.getStringWidthD(I18n.get("tritium-music.ui.playlist.play")) + 12), 17);
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

            RoundedButtonWidget btnPlayRandomOrder = new RoundedButtonWidget(I18n.get("tritium-music.ui.playlist.shuffle"), FontManager.pf16bold);
            this.addChild(btnPlayRandomOrder);

            btnPlayRandomOrder.setBeforeRenderCallback(() -> {
                btnPlayRandomOrder.setBounds(Math.max(57, FontManager.pf16bold.getStringWidthD(I18n.get("tritium-music.ui.playlist.shuffle")) + 12), 17);
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

            addViewModeControls(btnPlayRandomOrder);

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

            LabelWidget lblCreator = new LabelWidget(playList.getCreator().name(), FontManager.pf16bold);
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

        musicsPanel = new ScrollPanel();

        rwMusicsContainer.addChild(musicsPanel);
        applyViewMode();

        musicsPanel.setBeforeRenderCallback(() -> musicsPanel.setMargin(0));

        playList.loadMusicsWithCallback(musics -> {
            loadedMusics = List.copyOf(musics);
            rebuildMusicWidgets();
        });

        if (this.tfSearch != null) {
            this.tfSearch.setTextChangedCallback(text -> {
                filterMusics(text);
            });
        }

        this.addChild(contextMenu);
    }

    private void addViewModeControls(RoundedButtonWidget rowButton) {
        double controlWidth = 36;
        double controlSpacing = 6;
        double rightMargin = 24;

        RoundedButtonWidget btnListView = new RoundedButtonWidget(I18n.get("tritium-music.ui.playlist.list_view"), FontManager.pf14bold);
        RoundedButtonWidget btnGridView = new RoundedButtonWidget(I18n.get("tritium-music.ui.playlist.grid_view"), FontManager.pf14bold);
        this.addChild(btnListView, btnGridView);

        btnListView.setBeforeRenderCallback(() -> {
            boolean selected = getViewMode() == WidgetConfig.PlaylistViewMode.LIST;
            btnListView.setBounds(controlWidth, rowButton.getHeight());
            btnListView.setPosition(this.getWidth() - rightMargin - controlWidth * 2 - controlSpacing, rowButton.getRelativeY());
            btnListView.setRadius(4);
            btnListView.setColor(selected ? 0xFFD60017 : NCMScreen.getColor(NCMScreen.ColorType.ELEMENT_BACKGROUND));
            btnListView.setTextColor(NCMScreen.getColor(selected ? NCMScreen.ColorType.PRIMARY_TEXT : NCMScreen.ColorType.SECONDARY_TEXT));
        });
        btnGridView.setBeforeRenderCallback(() -> {
            boolean selected = getViewMode() == WidgetConfig.PlaylistViewMode.GRID;
            btnGridView.setBounds(controlWidth, rowButton.getHeight());
            btnGridView.setPosition(this.getWidth() - rightMargin - controlWidth, rowButton.getRelativeY());
            btnGridView.setRadius(4);
            btnGridView.setColor(selected ? 0xFFD60017 : NCMScreen.getColor(NCMScreen.ColorType.ELEMENT_BACKGROUND));
            btnGridView.setTextColor(NCMScreen.getColor(selected ? NCMScreen.ColorType.PRIMARY_TEXT : NCMScreen.ColorType.SECONDARY_TEXT));
        });
        btnListView.setOnClickCallback((relativeX, relativeY, mouseButton) -> {
            if (mouseButton == 0) setViewMode(WidgetConfig.PlaylistViewMode.LIST);
            return true;
        });
        btnGridView.setOnClickCallback((relativeX, relativeY, mouseButton) -> {
            if (mouseButton == 0) setViewMode(WidgetConfig.PlaylistViewMode.GRID);
            return true;
        });
    }

    private WidgetConfig.PlaylistViewMode getViewMode() {
        return WidgetConfig.get().playlistViewMode;
    }

    private void setViewMode(WidgetConfig.PlaylistViewMode viewMode) {
        WidgetConfig config = WidgetConfig.get();
        if (config.playlistViewMode == viewMode) return;
        config.playlistViewMode = viewMode;
        config.save();
        rebuildMusicWidgets();
    }

    private void applyViewMode() {
        boolean grid = getViewMode() == WidgetConfig.PlaylistViewMode.GRID;
        musicsPanel.setAlignment(grid ? ScrollPanel.Alignment.VERTICAL_WITH_HORIZONTAL_FILL : ScrollPanel.Alignment.VERTICAL);
        musicsPanel.setSpacing(grid ? 12 : 0);
        musicsPanel.setVerticalSpacing(grid ? 2 : 0);
    }

    private void rebuildMusicWidgets() {
        if (musicsPanel == null) return;

        applyViewMode();
        musicsPanel.getChildren().clear();
        musicsPanel.actualScrollOffset = 0;
        musicsPanel.targetScrollOffset = 0;

        MusicWidget.Style style = getViewMode() == WidgetConfig.PlaylistViewMode.GRID
                ? MusicWidget.Style.GRID
                : MusicWidget.Style.LIST;
        long revealStart = System.currentTimeMillis();
        for (int i = 0; i < loadedMusics.size(); i++) {
            Music music = loadedMusics.get(i);
            int playlistIndex = playList.getMusics().indexOf(music);
            if (playlistIndex < 0) playlistIndex = i;
            musicsPanel.addChild(new MusicWidget(music, playList, playlistIndex, revealStart, this, style)
                    .setShouldOverrideMouseCursor(true));
        }

        filterMusics(tfSearch == null ? "" : tfSearch.getText());
    }

    private void filterMusics(String text) {
        if (musicsPanel == null) return;
        String query = text == null ? "" : text.toLowerCase();
        musicsPanel.getChildren()
                .stream()
                .filter(child -> child instanceof MusicWidget)
                .map(child -> (MusicWidget) child)
                .forEach(widget -> widget.setHidden(!query.isEmpty() &&
                        !widget.music.getName().toLowerCase().contains(query) &&
                        !widget.music.getTranslatedNames().toLowerCase().contains(query) &&
                        widget.music.getArtists().stream().noneMatch(artist -> artist != null && artist.name() != null && artist.name().toLowerCase().contains(query)) &&
                        (widget.music.getAlbum() == null || widget.music.getAlbum().getName() == null || !widget.music.getAlbum().getName().toLowerCase().contains(query))));
    }

    public void openMusicMenu(MusicWidget widget, double mouseX, double mouseY) {
        List<ContextMenuWidget.Item> items = new ArrayList<>();
        boolean liked = CloudMusic.likeList != null && CloudMusic.likeList.contains(widget.music.getId());
        items.add(new ContextMenuWidget.Item(I18n.get("tritium-music.ui.menu.play"), () -> {
            int index = playList.getMusics().indexOf(widget.music);
            if (index >= 0) CloudMusic.play(playList.getMusics(), index);
        }));
        items.add(new ContextMenuWidget.Item(I18n.get("tritium-music.ui.menu.play_next"), () -> CloudMusic.playNext(widget.music)));
        items.add(new ContextMenuWidget.Item(I18n.get(liked ? "tritium-music.ui.menu.unlike" : "tritium-music.ui.menu.like"),
                () -> runLibraryOperation(() -> widget.music.setLike(!liked))));
        items.add(new ContextMenuWidget.Item(I18n.get("tritium-music.ui.menu.add_to_playlist"), () -> openAddToPlaylistMenu(widget, mouseX, mouseY)));
        items.add(new ContextMenuWidget.Item(I18n.get("tritium-music.ui.menu.copy_id"), () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(String.valueOf(widget.music.getId()));
        }));
        if (!playList.isSearchMode()) {
            items.add(new ContextMenuWidget.Item(I18n.get("tritium-music.ui.menu.remove_from_playlist"), () -> removeMusic(widget)));
        }
        contextMenu.open(mouseX - getX(), mouseY - getY(), items);
    }

    private void openAddToPlaylistMenu(MusicWidget widget, double mouseX, double mouseY) {
        List<PlayList> playlists = CloudMusic.playLists == null
                ? List.of()
                : CloudMusic.playLists.stream().filter(candidate -> !candidate.isSubscribed()).toList();
        if (playlists.isEmpty()) {
            contextMenu.open(mouseX - getX(), mouseY - getY(), List.of(
                    new ContextMenuWidget.Item(I18n.get("tritium-music.ui.menu.no_playlists"), null, false, false)));
            return;
        }
        contextMenu.open(mouseX - getX(), mouseY - getY(), playlists.stream()
                .map(target -> new ContextMenuWidget.Item(target.getName(),
                        () -> runLibraryOperation(() -> target.addToList(widget.music.getId()))))
                .toList());
    }

    private void removeMusic(MusicWidget widget) {
        runLibraryOperation(() -> {
            playList.removeFromList(widget.music.getId());
            playList.getMusics().remove(widget.music);
        });
    }

    private void runLibraryOperation(Runnable operation) {
        Platform.runAsync(() -> {
            try {
                operation.run();
            } finally {
                CloudMusic.refreshLibrary();
                Platform.runOnRenderThread(() -> NCMScreen.getInstance().refreshLibraryView());
            }
        });
    }

    @Override
    public void onMouseClickReceived(double mouseX, double mouseY, int mouseButton) {
        if (contextMenu.handleClick(mouseX, mouseY, mouseButton)) return;

        if (this.tfSearch != null && this.tfSearch.isFocused()) {
            var searchBar = this.tfSearch.getParent();
            if (searchBar == null || !RenderSystem.isHovered(mouseX, mouseY,
                    searchBar.getX(), searchBar.getY(), searchBar.getWidth(), searchBar.getHeight())) {
                this.tfSearch.setFocused(false);
            }
        }

        super.onMouseClickReceived(mouseX, mouseY, mouseButton);
    }

    @Override
    public void renderWidget(double mouseX, double mouseY, int dWheel) {
        if (contextMenu.handleWheel(mouseX, mouseY, dWheel)) dWheel = 0;
        super.renderWidget(mouseX, mouseY, dWheel);
    }

    private String formatDuration(long totalMillis) {
        long totalSeconds = totalMillis / 1000;

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();

        if (hours > 0) {
            sb.append(I18n.get("tritium-music.ui.duration.hours", String.format("%02d", hours)));
        }

        if (minutes > 0) {
            sb.append(I18n.get("tritium-music.ui.duration.minutes", String.format("%02d", minutes)));
        }

        sb.append(I18n.get("tritium-music.ui.duration.seconds", String.format("%02d", seconds)));

        return sb.toString();
    }

    int lastSize = -1;
    long cachedDuration;

    private String getPlayListInfo() {
        if (!playList.musicsLoaded)
            return "";

        List<Music> musics = playList.musics;

        if (lastSize != musics.size()) {
            lastSize = musics.size();
            cachedDuration = musics.stream().mapToLong(Music::getDuration).sum();
        }

        int count = musics.isEmpty() ? playList.getCount() : musics.size();
        String songCount = I18n.get("tritium-music.ui.playlist.song_count", count);
        return musics.isEmpty() ? songCount : songCount + " · " + this.formatDuration(cachedDuration);
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

        Textures.downloadTextureAndLoadAsync(playList.getCreator().avatarUrl() + "?param=32y32", avatarLoc);
    }
}
