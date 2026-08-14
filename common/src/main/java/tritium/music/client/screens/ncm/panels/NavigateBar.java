package tritium.music.client.screens.ncm.panels;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.resources.language.I18n;
import org.lwjgl.glfw.GLFW;
import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.ui.AbstractWidget;
import tritium.music.client.rendering.ui.container.Panel;
import tritium.music.client.rendering.ui.container.ScrollPanel;
import tritium.music.client.rendering.ui.widgets.*;
import tritium.music.client.screens.ncm.NCMPanel;
import tritium.music.client.screens.ncm.NCMScreen;
import tritium.music.core.CloudMusic;
import tritium.music.core.model.Music;
import tritium.music.core.model.PlayList;
import tritium.music.core.ncm.api.CloudMusicApi;
import tritium.music.core.util.AsyncUtil;
import tritium.music.core.util.JsonUtils;
import tritium.music.platform.Platform;
import tritium.music.platform.TextureHandle;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class NavigateBar extends NCMPanel {

    TextFieldWidget searchField = new TextFieldWidget(FontManager.pf14bold);
    ScrollPanel playlistPanel = new ScrollPanel();
    private final List<AbstractWidget<?>> libraryItems = new ArrayList<>();
    private final AtomicLong suggestionRequest = new AtomicLong();
    private final List<SearchSuggestion> searchSuggestions = new CopyOnWriteArrayList<>();
    private SearchSuggestionPanel suggestionPanel;

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

        this.searchField.setPlaceholder(I18n.get("tritium-music.ui.search.placeholder"));

        this.searchField.setTextChangedCallback(this::requestSearchSuggestions);

        this.searchField.setOnKeyTypedCallback((character, keyCode) -> {
            if (this.searchField.isFocused()) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE)
                    this.searchField.setFocused(false);

                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    submitSearch(this.searchField.getText());
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

        suggestionPanel = new SearchSuggestionPanel();
        suggestionPanel.setBeforeRenderCallback(() -> {
            suggestionPanel.setPosition(searchBar.getRelativeX(), searchBar.getRelativeY() + searchBar.getHeight() + 3);
            double availableWidth = NCMScreen.getInstance().getPanelWidth() - searchBar.getRelativeX() - SearchSuggestionPanel.PANEL_EDGE_SPACING;
            suggestionPanel.setBounds(suggestionPanel.preferredWidth(searchBar.getWidth(), availableWidth), suggestionPanel.preferredHeight());
        });

        this.addChild(playlistPanel);
        this.playlistPanel.setBeforeRenderCallback(() -> {
            this.playlistPanel.setMargin(0);
            this.playlistPanel.setPosition(this.playlistPanel.getRelativeX(), searchBar.getRelativeY() + searchBar.getHeight() + 8);
            this.playlistPanel.setBounds(this.playlistPanel.getWidth(), this.playlistPanel.getHeight() - searchBar.getHeight() - 16 - 32);
        });

        this.playlistPanel.setSpacing(4);

        LabelWidget lbl = new LabelWidget(I18n.get("tritium-music.ui.app_name"), FontManager.pf14bold);
        lbl.setBeforeRenderCallback(() -> {
            lbl.setColor(Color.GRAY);
            lbl.setPosition(6, lbl.getRelativeY());
        });

        this.playlistPanel.addChild(lbl);

        {
            PlaylistItem item = new PlaylistItem("A", () -> 0xFFC30218, () -> I18n.get("tritium-music.ui.navigation.home"), () -> NCMScreen.getInstance().setCurrentPanel(new HomePanel()));
            item.setShouldOverrideMouseCursor(true);
            this.playlistPanel.addChild(item);
        }

        {
            PlaylistItem item = new PlaylistItem("", Color.GRAY::getRGB, () -> I18n.get("tritium-music.ui.navigation.hud_settings"),
                    () -> NCMScreen.getInstance().setCurrentPanel(new HudSettingsPanel()));
            item.setShouldOverrideMouseCursor(true);
            this.playlistPanel.addChild(item);
        }

        populatePlaylists(-1);

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

        LabelWidget lblCreator = new LabelWidget(() -> CloudMusic.profile == null ? I18n.get("tritium-music.ui.account.not_logged_in") : CloudMusic.profile.name(), FontManager.pf16bold);
        this.addChild(lblCreator);

        lblCreator.setBeforeRenderCallback(() -> {
            lblCreator.setPosition(creatorAvatar.getRelativeX() + creatorAvatar.getWidth() + 4, creatorAvatar.getRelativeY() + creatorAvatar.getHeight() * .5 - lblCreator.getHeight() * .5);
            lblCreator.setColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
        });

        suggestionPanel.setParent(this);
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        if (suggestionPanel != null) {
            suggestionPanel.setOpen(searchField.isFocused() && !searchSuggestions.isEmpty());
        }
    }

    private void requestSearchSuggestions(String text) {
        String query = text == null ? "" : text.trim();
        long request = suggestionRequest.incrementAndGet();
        if (query.isEmpty()) {
            updateSearchSuggestions(request, List.of());
            return;
        }
        CompletableFuture.delayedExecutor(250, TimeUnit.MILLISECONDS).execute(() -> {
            if (request != suggestionRequest.get()) return;
            AsyncUtil.runAsync(() -> {
                try {
                    JsonObject response = CloudMusicApi.searchSuggest(query).toJsonObject();
                    List<SearchSuggestion> suggestions = parseSuggestions(response, query);
                    AsyncUtil.runOnRenderThread(() -> updateSearchSuggestions(request, suggestions));
                } catch (Exception e) {
                    Platform.log("[NCM] Search suggestions failed: " + e.getMessage());
                    AsyncUtil.runOnRenderThread(() -> updateSearchSuggestions(request, List.of()));
                }
            });
        });
    }

    private List<SearchSuggestion> parseSuggestions(JsonObject response, String query) {
        if (response == null || !response.has("result") || !response.get("result").isJsonObject()) return List.of();
        JsonObject result = response.getAsJsonObject("result");
        ArrayList<SearchSuggestion> values = new ArrayList<>();
        addSongSuggestions(values, result.getAsJsonArray("songs"));
        addArtistSuggestions(values, result.getAsJsonArray("artists"));
        addAlbumSuggestions(values, result.getAsJsonArray("albums"));
        addPlaylistSuggestions(values, result.getAsJsonArray("playlists"));
        addKeywordSuggestions(values, result.getAsJsonArray("allMatch"), query);
        return values.stream().distinct().limit(6).toList();
    }

    private void addSongSuggestions(List<SearchSuggestion> target, JsonArray array) {
        if (array == null) return;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject song = element.getAsJsonObject();
            String title = text(song, "name");
            String artists = names(song.getAsJsonArray("artists"));
            String album = song.has("album") && song.get("album").isJsonObject() ? text(song.getAsJsonObject("album"), "name") : "";
            if (!title.isBlank()) target.add(new SearchSuggestion(title, artists, album));
        }
    }

    private void addArtistSuggestions(List<SearchSuggestion> target, JsonArray array) {
        if (array == null) return;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject artist = element.getAsJsonObject();
            String title = text(artist, "name");
            String aliases = strings(artist.getAsJsonArray("alias"));
            String count = artist.has("musicSize") ? I18n.get("tritium-music.ui.search.song_count", artist.get("musicSize").getAsInt()) : "";
            if (!title.isBlank()) target.add(new SearchSuggestion(title, "", joinDetails(aliases, count)));
        }
    }

    private void addAlbumSuggestions(List<SearchSuggestion> target, JsonArray array) {
        if (array == null) return;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject album = element.getAsJsonObject();
            String title = text(album, "name");
            String artist = album.has("artist") && album.get("artist").isJsonObject() ? text(album.getAsJsonObject("artist"), "name") : "";
            if (!title.isBlank()) target.add(new SearchSuggestion(title, "", artist));
        }
    }

    private void addPlaylistSuggestions(List<SearchSuggestion> target, JsonArray array) {
        if (array == null) return;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject playlist = element.getAsJsonObject();
            String title = text(playlist, "name");
            String creator = playlist.has("creator") && playlist.get("creator").isJsonObject() ? text(playlist.getAsJsonObject("creator"), "nickname") : "";
            if (!title.isBlank()) target.add(new SearchSuggestion(title, "", creator));
        }
    }

    private void addKeywordSuggestions(List<SearchSuggestion> target, JsonArray array, String query) {
        if (array == null) return;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            String keyword = text(element.getAsJsonObject(), "keyword");
            if (!keyword.isBlank() && !keyword.equalsIgnoreCase(query)) {
                target.add(new SearchSuggestion(keyword, "", ""));
            }
        }
    }

    private String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private String names(JsonArray array) {
        if (array == null) return "";
        ArrayList<String> names = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                String name = text(element.getAsJsonObject(), "name");
                if (!name.isBlank()) names.add(name);
            }
        }
        return String.join(" / ", names);
    }

    private String strings(JsonArray array) {
        if (array == null) return "";
        ArrayList<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) values.add(element.getAsString());
        }
        return String.join(" / ", values);
    }

    private String joinDetails(String first, String second) {
        if (first == null || first.isBlank()) return second == null ? "" : second;
        if (second == null || second.isBlank()) return first;
        return first + " · " + second;
    }

    private void updateSearchSuggestions(long request, List<SearchSuggestion> suggestions) {
        if (request != suggestionRequest.get() || suggestionPanel == null) return;
        searchSuggestions.clear();
        searchSuggestions.addAll(suggestions);
        suggestionPanel.contentChanged();
    }

    public boolean handleSuggestionClick(double mouseX, double mouseY, int mouseButton) {
        if (suggestionPanel == null || !suggestionPanel.contains(mouseX, mouseY)) return false;
        SearchSuggestion suggestion = suggestionPanel.suggestionAt(mouseX, mouseY);
        if (suggestion != null && mouseButton == 0) {
            searchField.setText(suggestion.title());
            submitSearch(suggestion.title());
        }
        return true;
    }

    public void renderSuggestionOverlay(double mouseX, double mouseY) {
        if (suggestionPanel != null) suggestionPanel.renderWidget(mouseX, mouseY, 0);
    }

    private void submitSearch(String text) {
        String query = text == null ? "" : text.trim();
        if (query.isEmpty()) return;
        suggestionRequest.incrementAndGet();
        searchSuggestions.clear();
        if (suggestionPanel != null) suggestionPanel.getChildren().clear();
        searchField.setFocused(false);

        PlayList playList = JsonUtils.parse("{}", PlayList.class);
        playList.setSearchMode(true);
        playList.musics = new CopyOnWriteArrayList<>();
        PlaylistPanel panel = new PlaylistPanel(playList);
        NCMScreen.getInstance().setCurrentPanel(panel);
        this.playlistPanel.getChildren().forEach(child -> {
            if (child instanceof PlaylistItem item) item.setSelected(false);
        });

        AsyncUtil.runAsync(() -> {
            List<Music> search = CloudMusic.search(query);
            AsyncUtil.runOnRenderThread(() -> panel.updateSearchResults(search));
        });
    }

    private record SearchSuggestion(String title, String inlineDetail, String subtitle) {
    }

    private final class SearchSuggestionPanel extends Panel {
        private static final double PANEL_EDGE_SPACING = 8;
        private static final double PANEL_PADDING = 4;
        private static final double ROW_HORIZONTAL_PADDING = 8;
        private static final double ROW_VERTICAL_PADDING = 6;
        private static final double LINE_GAP = 2;
        private boolean open;
        private float visibility;
        private float contentReveal;
        private final float[] hoverAnimations = new float[6];

        private SearchSuggestionPanel() {
            setHidden(true);
            setShouldOverrideMouseCursor(true);
            setTransformations(() -> {
                double scale = .975 + visibility * .025;
                RenderContext.graphics().pose().translate(0, (1 - visibility) * -5);
                scaleAtPos(getX() + getWidth() * .5, getY(), scale);
            });
        }

        private void setOpen(boolean open) {
            this.open = open;
            if (open) setHidden(false);
        }

        private void contentChanged() {
            contentReveal = 0f;
            for (int i = 0; i < hoverAnimations.length; i++) hoverAnimations[i] = 0f;
            if (!searchSuggestions.isEmpty()) setHidden(false);
        }

        @Override
        public void onRender(double mouseX, double mouseY) {
            visibility = Interpolations.interpolate(visibility, open ? 1f : 0f, open ? .38f : .25f);
            contentReveal = Interpolations.interpolate(contentReveal, open ? 1f : 0f, .34f);
            if (!open && visibility <= .01f) {
                visibility = 0f;
                setHidden(true);
                return;
            }

            float alpha = getAlpha() * visibility;
            roundedRect(getX() - 4, getY() - 3, getWidth() + 8, getHeight() + 8, 8, reAlpha(0xFF000000, alpha * .16f));
            roundedRect(getX() - 2, getY() - 1, getWidth() + 4, getHeight() + 4, 6, reAlpha(0xFF000000, alpha * .24f));
            roundedRect(getX(), getY(), getWidth(), getHeight(), 5, reAlpha(0xFF202126, alpha * .98f));

            double itemY = getY() + PANEL_PADDING;
            for (int i = 0; i < searchSuggestions.size(); i++) {
                SearchSuggestion suggestion = searchSuggestions.get(i);
                double itemHeight = rowHeight(suggestion);
                boolean hovered = isHovered(mouseX, mouseY, getX() + 3, itemY, getWidth() - 6, itemHeight);
                hoverAnimations[i] = Interpolations.interpolate(hoverAnimations[i], hovered ? 1f : 0f, .32f);
                float stagger = Math.max(0f, Math.min(1f, (contentReveal - i * .055f) / .72f));
                float rowAlpha = alpha * stagger;
                if (hoverAnimations[i] > .004f) {
                    roundedRect(getX() + 3, itemY, getWidth() - 6, itemHeight, 4,
                            reAlpha(0xFF363840, rowAlpha * hoverAnimations[i]));
                }

                double textX = getX() + ROW_HORIZONTAL_PADDING;
                double availableWidth = getWidth() - ROW_HORIZONTAL_PADDING * 2;
                String title = suggestion.title();
                String inlineDetail = suggestion.inlineDetail();
                double inlineGap = inlineDetail.isBlank() ? 0 : FontManager.pf12.getStringWidthD("  ");
                double titleWidth = FontManager.pf14bold.getStringWidthD(title);
                double inlineWidth = inlineDetail.isBlank() ? 0 : FontManager.pf12.getStringWidthD(inlineDetail);
                double combinedWidth = titleWidth + inlineGap + inlineWidth;
                if (combinedWidth > availableWidth && inlineWidth > 0) {
                    double distributableWidth = Math.max(0, availableWidth - inlineGap);
                    double titleBudget = distributableWidth * titleWidth / (titleWidth + inlineWidth);
                    double inlineBudget = distributableWidth - titleBudget;
                    title = FontManager.pf14bold.trim(title, titleBudget);
                    inlineDetail = FontManager.pf12.trim(inlineDetail, inlineBudget);
                } else {
                    title = FontManager.pf14bold.trim(title, availableWidth);
                }
                double titleHeight = FontManager.pf14bold.getStringHeight(title);
                double inlineHeight = inlineDetail.isBlank() ? 0 : FontManager.pf12.getStringHeight(inlineDetail);
                double primaryLineHeight = Math.max(titleHeight, inlineHeight);
                double subtitleHeight = suggestion.subtitle().isBlank() ? 0 : FontManager.pf12.getStringHeight(suggestion.subtitle());
                double textBlockHeight = primaryLineHeight + (subtitleHeight > 0 ? LINE_GAP + subtitleHeight : 0);
                double textY = itemY + (itemHeight - textBlockHeight) * .5;
                FontManager.pf14bold.drawString(title, textX, textY + (primaryLineHeight - titleHeight) * .5,
                        reAlpha(0xFFF2F3F5, rowAlpha));
                if (!inlineDetail.isBlank()) {
                    double inlineX = textX + FontManager.pf14bold.getStringWidthD(title) + inlineGap;
                    FontManager.pf12.drawString(inlineDetail, inlineX, textY + (primaryLineHeight - inlineHeight) * .5,
                            reAlpha(0xFFB5B7BD, rowAlpha));
                }
                if (!suggestion.subtitle().isBlank()) {
                    String subtitle = FontManager.pf12.trim(suggestion.subtitle(), availableWidth);
                    FontManager.pf12.drawString(subtitle, textX, textY + primaryLineHeight + LINE_GAP,
                            reAlpha(0xFF8A8D94, rowAlpha));
                }
                itemY += itemHeight;
            }
        }

        private SearchSuggestion suggestionAt(double mouseX, double mouseY) {
            if (visibility <= .1f || !contains(mouseX, mouseY)) return null;
            double itemY = getY() + PANEL_PADDING;
            for (SearchSuggestion suggestion : searchSuggestions) {
                double itemHeight = rowHeight(suggestion);
                if (mouseY >= itemY && mouseY <= itemY + itemHeight) return suggestion;
                itemY += itemHeight;
            }
            return null;
        }

        private boolean contains(double mouseX, double mouseY) {
            return visibility > .1f && isHovered(mouseX, mouseY, getX(), getY(), getWidth(), getHeight());
        }

        private double preferredHeight() {
            double height = PANEL_PADDING * 2;
            for (SearchSuggestion suggestion : searchSuggestions) height += rowHeight(suggestion);
            return height;
        }

        private double preferredWidth(double minimumWidth, double maximumWidth) {
            double contentWidth = minimumWidth;
            double inlineGap = FontManager.pf12.getStringWidthD("  ");
            for (SearchSuggestion suggestion : searchSuggestions) {
                double primaryWidth = FontManager.pf14bold.getStringWidthD(suggestion.title());
                if (!suggestion.inlineDetail().isBlank()) {
                    primaryWidth += inlineGap + FontManager.pf12.getStringWidthD(suggestion.inlineDetail());
                }
                double subtitleWidth = suggestion.subtitle().isBlank()
                        ? 0
                        : FontManager.pf12.getStringWidthD(suggestion.subtitle());
                contentWidth = Math.max(contentWidth, Math.max(primaryWidth, subtitleWidth) + ROW_HORIZONTAL_PADDING * 2);
            }
            return Math.min(contentWidth, maximumWidth);
        }

        private double rowHeight(SearchSuggestion suggestion) {
            double contentHeight = Math.max(FontManager.pf14bold.getStringHeight(suggestion.title()),
                    suggestion.inlineDetail().isBlank() ? 0 : FontManager.pf12.getStringHeight(suggestion.inlineDetail()));
            if (!suggestion.subtitle().isBlank()) {
                contentHeight += LINE_GAP + FontManager.pf12.getStringHeight(suggestion.subtitle());
            }
            return contentHeight + ROW_VERTICAL_PADDING * 2;
        }
    }

    public void refreshPlaylists(long selectedPlaylistId) {
        playlistPanel.getChildren().removeAll(libraryItems);
        libraryItems.clear();
        populatePlaylists(selectedPlaylistId);
    }

    private void populatePlaylists(long selectedPlaylistId) {
        LabelWidget lblPlaylists = new LabelWidget(I18n.get("tritium-music.ui.navigation.my_playlists"), FontManager.pf14bold);
        lblPlaylists.setBeforeRenderCallback(() -> {
            lblPlaylists.setColor(Color.GRAY);
            lblPlaylists.setPosition(6, lblPlaylists.getRelativeY());
        });

        addLibraryItem(lblPlaylists);

        List<PlayList> pl = CloudMusic.playLists;

        if (pl != null) {
            List<PlayList> playLists = pl.stream().filter(playList -> !playList.isSubscribed()).toList();
            for (int i = 0; i < playLists.size(); i++) {
                PlayList playList = playLists.get(i);
                PlaylistItem item = new PlaylistItem(i == 0 ? "C" : "D", Color.GRAY::getRGB, playList::getName, () -> NCMScreen.getInstance().setCurrentPanel(new PlaylistPanel(playList)));
                item.setSelected(playList.getId() == selectedPlaylistId);
                item.setShouldOverrideMouseCursor(true);
                addLibraryItem(item);
            }
        }

        LabelWidget lblSubscribed = new LabelWidget(I18n.get("tritium-music.ui.navigation.subscribed_playlists"), FontManager.pf14bold);
        lblSubscribed.setBeforeRenderCallback(() -> {
            lblSubscribed.setColor(Color.GRAY);
            lblSubscribed.setPosition(6, lblSubscribed.getRelativeY());
        });

        addLibraryItem(lblSubscribed);

        if (pl != null) {
            pl.stream().filter(PlayList::isSubscribed).forEach(playList -> {
                PlaylistItem item = new PlaylistItem("D", Color.GRAY::getRGB, playList::getName, () -> NCMScreen.getInstance().setCurrentPanel(new PlaylistPanel(playList)));
                item.setSelected(playList.getId() == selectedPlaylistId);
                item.setShouldOverrideMouseCursor(true);
                addLibraryItem(item);
            });
        }
    }

    private void addLibraryItem(AbstractWidget<?> item) {
        libraryItems.add(item);
        playlistPanel.addChild(item);
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

        tritium.music.core.util.Textures.downloadTextureAndLoadAsync(CloudMusic.profile.avatarUrl() + "?param=32y32", avatarLoc);
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
                float target = selected ? 0.9f : (this.isHovering() ? 0.1f : 0f);
                hoverAnim = Interpolations.interpolate(hoverAnim, target, 0.3f);
                bg.setHidden(hoverAnim <= 0.004f);
                bg.setColor(selected ? NCMScreen.getColor(NCMScreen.ColorType.ELEMENT_HOVER) : Color.BLACK.getRGB());
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
