package tritium.music.client.screens.ncm.panels;

import lombok.Getter;
import lombok.Setter;
import org.lwjgl.glfw.GLFW;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.ui.container.Panel;
import tritium.music.client.rendering.ui.container.ScrollPanel;
import tritium.music.client.rendering.ui.widgets.LabelWidget;
import tritium.music.client.rendering.ui.widgets.RectWidget;
import tritium.music.client.rendering.ui.widgets.RoundedImageWidget;
import tritium.music.client.rendering.ui.widgets.RoundedRectWidget;
import tritium.music.client.rendering.ui.widgets.TextFieldWidget;
import tritium.music.client.screens.ncm.NCMPanel;
import tritium.music.client.screens.ncm.NCMScreen;
import tritium.music.client.util.MouseUtil;
import tritium.music.core.CloudMusic;
import tritium.music.core.model.Music;
import tritium.music.core.model.PlayList;
import tritium.music.core.util.AsyncUtil;
import tritium.music.core.util.JsonUtils;
import tritium.music.platform.Platform;
import tritium.music.platform.TextureHandle;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

public class NavigateBar extends NCMPanel {

    TextFieldWidget searchField = new TextFieldWidget(FontManager.pf14bold);
    ScrollPanel playlistPanel = new ScrollPanel();

    public NavigateBar() {
        this.layout();
    }

    private static boolean isCtrlDown() {
        long handle = net.minecraft.client.Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private void layout() {
        RectWidget bg = new RectWidget();
        this.addChild(bg);

        this.setBeforeRenderCallback(() -> {
            this.setBounds(NCMScreen.getInstance().getPanelWidth() * .15, NCMScreen.getInstance().getPanelHeight());
            this.setPosition(0, 0);

            bg.setMargin(0);
            bg.setColor(this.getColor(NCMScreen.ColorType.GENERIC_BACKGROUND));
            bg.setAlpha(0.9f);
        });

        this.setOnKeyTypedCallback((character, keyCode) -> {
            if (isCtrlDown() && keyCode == GLFW.GLFW_KEY_F) {
                this.searchField.setFocused(true);
                return true;
            }
            return false;
        });

        RoundedRectWidget searchBar = new RoundedRectWidget();
        RoundedRectWidget searchBarFocusAnimation = new RoundedRectWidget();

        this.addChild(searchBarFocusAnimation);
        this.addChild(searchBar);

        searchBarFocusAnimation.setBeforeRenderCallback(() -> {
            if (!searchField.isFocused()) {
                searchBarFocusAnimation.setAlpha(0);
            } else {
                searchBarFocusAnimation.setAlpha(Interpolations.interpolate(searchBarFocusAnimation.getAlpha(), 1f, .3f));
                searchBarFocusAnimation.setRadius(4);
                searchBarFocusAnimation.setColor(0xff780C17);
                searchBarFocusAnimation.setBounds(searchBar.getRelativeX(), searchBar.getRelativeY(), searchBar.getWidth(), searchBar.getHeight());
                searchBarFocusAnimation.expand(1 + 5 * (1 - searchBarFocusAnimation.getAlpha()));
            }
        });

        searchBar
                .setBeforeRenderCallback(() -> {
                    searchBar.setAlpha(1f);
                    searchBar.setColor(0xFF5E5E5E);
                    searchBar.setMargin(8);
                    searchBar.setHeight(16);
                    searchBar.setRadius(3.5);
                });

        RoundedRectWidget searchBarBg = new RoundedRectWidget();
        searchBar.addChild(searchBarBg);

        searchBarBg.setBeforeRenderCallback(() -> {
            searchBarBg.setMargin(.5);
            searchBarBg.setAlpha(.6f);
            searchBar.setColor(0xFF292727);
            searchBarBg.setRadius(searchBar.getRadius() - .5);
        });

        LabelWidget lblSearchIcon = new LabelWidget("K", FontManager.music18);
        searchBar.addChild(lblSearchIcon);

        lblSearchIcon.setBeforeRenderCallback(() -> {
            lblSearchIcon.setColor(hexColor(100, 100, 100));
            lblSearchIcon.centerVertically();
            lblSearchIcon.setPosition(lblSearchIcon.getRelativeY(), lblSearchIcon.getRelativeY());
        });

        searchBar.addChild(searchField);

        this.searchField.setPlaceholder("搜索...");

        this.searchField.setOnKeyTypedCallback((character, keyCode) -> {
            if (this.searchField.isFocused()) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE)
                    this.searchField.setFocused(false);

                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    PlayList playList = JsonUtils.parse("{}", PlayList.class);
                    playList.setSearchMode(true);
                    playList.musics = new CopyOnWriteArrayList<>();
                    PlaylistPanel panel = new PlaylistPanel(playList);
                    NCMScreen.getInstance().setCurrentPanel(panel);
                    this.playlistPanel.getChildren().forEach(child -> {
                        if (child instanceof PlaylistItem item)
                            item.setSelected(false);
                    });

                    AsyncUtil.runAsync(() -> {
                        List<Music> search = CloudMusic.search(this.searchField.getText());
                        playList.musics.addAll(search);
                        panel.onInit();
                    });
                }

                return true;
            }

            return false;
        });

        searchField.setBeforeRenderCallback(() -> {
            searchField.drawUnderline(false);
            searchField.setMargin(2);
            double xSpacing = lblSearchIcon.getRelativeX() + lblSearchIcon.getWidth() + 4;
            searchField.setBounds(xSpacing, searchField.getRelativeY(), searchField.getWidth() - xSpacing, searchField.getHeight());
            searchField.setColor(this.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
            searchField.setDisabledTextColor(RenderSystem.reAlpha(this.getColor(NCMScreen.ColorType.PRIMARY_TEXT), .4f));
        });

        this.addChild(playlistPanel);
        this.playlistPanel.setBeforeRenderCallback(() -> {
            this.playlistPanel.setMargin(0);
            this.playlistPanel.setPosition(this.playlistPanel.getRelativeX(), searchBar.getRelativeY() + searchBar.getHeight() + 8);
            this.playlistPanel.setBounds(this.playlistPanel.getWidth(), this.playlistPanel.getHeight() - searchBar.getHeight() - 16 - 32);
        });

        this.playlistPanel.setSpacing(4);

        LabelWidget lbl = new LabelWidget("Tritium Music", FontManager.pf14bold);
        lbl.setBeforeRenderCallback(() -> {
            lbl.setColor(Color.GRAY);
            lbl.setPosition(6, lbl.getRelativeY());
        });

        this.playlistPanel.addChild(lbl);

        {
            PlaylistItem item = new PlaylistItem("A", () -> 0xFFC30218, () -> "主页", () -> NCMScreen.getInstance().setCurrentPanel(new HomePanel()));
            item.setShouldOverrideMouseCursor(true);
            this.playlistPanel.addChild(item);
        }

        LabelWidget lblPlaylists = new LabelWidget("我的歌单", FontManager.pf14bold);
        lblPlaylists.setBeforeRenderCallback(() -> {
            lblPlaylists.setColor(Color.GRAY);
            lblPlaylists.setPosition(6, lblPlaylists.getRelativeY());
        });

        this.playlistPanel.addChild(lblPlaylists);

        List<PlayList> pl = CloudMusic.playLists;

        if (pl != null) {
            List<PlayList> playLists = pl.stream().filter(playList -> !playList.isSubscribed()).toList();
            for (int i = 0; i < playLists.size(); i++) {
                PlayList playList = playLists.get(i);
                PlaylistItem item = new PlaylistItem(i == 0 ? "C" : "D", Color.GRAY::getRGB, playList::getName, () -> NCMScreen.getInstance().setCurrentPanel(new PlaylistPanel(playList)));
                item.setShouldOverrideMouseCursor(true);
                this.playlistPanel.addChild(item);
            }
        }

        LabelWidget lblSubscribed = new LabelWidget("收藏歌单", FontManager.pf14bold);
        lblSubscribed.setBeforeRenderCallback(() -> {
            lblSubscribed.setColor(Color.GRAY);
            lblSubscribed.setPosition(6, lblSubscribed.getRelativeY());
        });

        this.playlistPanel.addChild(lblSubscribed);

        if (pl != null) {
            pl.stream().filter(PlayList::isSubscribed).forEach(playList -> {
                PlaylistItem item = new PlaylistItem("D", Color.GRAY::getRGB, playList::getName, () -> NCMScreen.getInstance().setCurrentPanel(new PlaylistPanel(playList)));
                item.setShouldOverrideMouseCursor(true);
                this.playlistPanel.addChild(item);
            });
        }

        RoundedImageWidget creatorAvatar = new RoundedImageWidget(this::getUserAvatarLocation, 0, 0, 0, 0);
        this.addChild(creatorAvatar);
        creatorAvatar.fadeIn();
        creatorAvatar.setLinearFilter(true);

        this.loadAvatar();

        creatorAvatar.setBeforeRenderCallback(() -> {
            creatorAvatar.setBounds(16, 16);
            creatorAvatar.setPosition(12, this.getHeight() - 8 - creatorAvatar.getHeight());
            creatorAvatar.setRadius(7.25);
        });

        LabelWidget lblCreator = new LabelWidget(() -> CloudMusic.profile == null ? "未登录" : CloudMusic.profile.getName(), FontManager.pf16bold);
        this.addChild(lblCreator);

        lblCreator.setBeforeRenderCallback(() -> {
            lblCreator.setPosition(creatorAvatar.getRelativeX() + creatorAvatar.getWidth() + 4, creatorAvatar.getRelativeY() + creatorAvatar.getHeight() * .5 - lblCreator.getHeight() * .5);
            lblCreator.setColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
        });
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int mouseButton) {
        return false;
    }

    private void loadAvatar() {
        if (CloudMusic.profile == null) {
            return;
        }

        TextureHandle avatarLoc = this.getUserAvatarLocation();
        if (avatarLoc == null || Platform.hasTexture(avatarLoc))
            return;

        tritium.music.core.util.Textures.downloadTextureAndLoadAsync(CloudMusic.profile.getAvatarUrl() + "?param=32y32", avatarLoc);
    }

    private TextureHandle getUserAvatarLocation() {
        if (CloudMusic.profile == null) {
            return null;
        }

        return CloudMusic.profile.getAvatarLocation();
    }

    @Override
    public void onInit() {
    }

    public static class PlaylistItem extends Panel {

        String icon;
        Supplier<Integer> iconColorSupplier;
        Supplier<String> label;
        Runnable onClick;
        RoundedRectWidget bg = new RoundedRectWidget();

        @Getter
        @Setter
        boolean selected = false;

        float hoverAnim = 0f;

        public PlaylistItem(String icon, Supplier<Integer> iconColorSupplier, Supplier<String> label, Runnable onClick) {
            this.icon = icon;
            this.iconColorSupplier = iconColorSupplier;
            this.label = label;
            this.onClick = onClick;

            this.setBeforeRenderCallback(() -> {
                this.setBounds(this.getParentWidth(), 16);
                this.setPosition(4, this.getRelativeY());
            });

            bg.setClickable(false);

            this.addChild(bg);
            this.bg.setBeforeRenderCallback(() -> {
                bg.setMargin(0);
                float target = selected ? 0.2f : (this.isHovering() ? 0.1f : 0f);
                hoverAnim = Interpolations.interpolate(hoverAnim, target, 0.3f);
                bg.setHidden(hoverAnim <= 0.004f);
                bg.setColor(Color.BLACK);
                bg.setAlpha(hoverAnim);
                bg.setRadius(4);
            });

            LabelWidget lblIcon = new LabelWidget(icon, FontManager.music18);
            this.addChild(lblIcon);
            lblIcon.setBeforeRenderCallback(() -> {
                lblIcon.setColor(iconColorSupplier.get());
                lblIcon.centerVertically();
                lblIcon.setPosition(8, lblIcon.getRelativeY());
            });

            lblIcon.setClickable(false);

            LabelWidget lbl = new LabelWidget(label, FontManager.pf14bold);
            this.addChild(lbl);

            lbl.setBeforeRenderCallback(() -> {
                lbl.centerVertically();
                lbl.setPosition(lblIcon.getRelativeX() + lblIcon.getWidth() + 4, lbl.getRelativeY());
                lbl.setColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
                lbl.setMaxWidth(this.getWidth() - 8 - lblIcon.getWidth() - 12);
            });

            lbl.setClickable(false);

            this.setOnClickCallback(((relativeX, relativeY, mouseButton) -> {
                if (mouseButton == 0) {
                    this.selected = true;
                    bg.setHidden(false);

                    this.onClick.run();

                    NCMScreen.getInstance().getPlaylistsPanel().playlistPanel.getChildren().stream()
                            .filter(it -> it instanceof PlaylistItem && it != this)
                            .forEach(it -> ((PlaylistItem) it).setSelected(false));
                }

                return true;
            }));
        }
    }
}
