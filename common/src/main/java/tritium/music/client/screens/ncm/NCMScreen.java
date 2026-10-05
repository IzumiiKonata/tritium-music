package tritium.music.client.screens.ncm;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import org.lwjgl.glfw.GLFW;
import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.StencilClipManager;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.ui.container.Panel;
import tritium.music.client.rendering.ui.widgets.RectWidget;
import tritium.music.client.screens.BaseScreen;
import tritium.music.client.screens.clickgui.music.LoginRenderer;
import tritium.music.client.screens.ncm.panels.ControlsBar;
import tritium.music.client.screens.ncm.panels.HomePanel;
import tritium.music.client.screens.ncm.panels.NavigateBar;
import tritium.music.client.screens.ncm.panels.PlaylistPanel;
import tritium.music.core.CloudMusic;
import tritium.music.core.MusicState;
import tritium.music.core.assets.AssetFormat;
import tritium.music.core.assets.AssetManager;
import tritium.music.core.assets.AssetRoute;
import tritium.music.core.audio.AutoMixSupport;
import tritium.music.core.ncm.OptionsUtil;
import tritium.music.core.util.AsyncUtil;
import tritium.music.platform.Platform;

import java.util.ArrayList;
import java.util.List;

public class NCMScreen extends BaseScreen {

    @Getter
    private static final NCMScreen instance = new NCMScreen();

    float alpha = 0f;
    boolean closing = false;

    @Override
    protected float screenAlpha() {
        return alpha;
    }

    Panel basePanel = new Panel();

    @Getter
    NavigateBar playlistsPanel;

    RectWidget currentPanelBg = new RectWidget();

    float prevAnimatingPanelAlpha = 0f;
    NCMPanel prevAnimatingPanel = null;
    NCMPanel currentPanel = null;
    float curPanelAlphaAnimation = 0f;

    @Getter
    ControlsBar controlsBar;

    public MusicLyricsPanel musicLyricsPanel = null;

    private boolean dirty = true;
    private NCMPanel pendingPanel;
    private Screen returnScreen;
    private boolean showLogin = true;

    public NCMScreen() {
    }

    public static void open() {
        instance.pendingPanel = null;
        instance.returnScreen = null;
        instance.showLogin = true;
        Minecraft.getInstance().setScreenAndShow(instance);
    }

    public static NCMScreen withPanel(NCMPanel panel, Screen returnScreen) {
        instance.pendingPanel = panel;
        instance.returnScreen = returnScreen;
        instance.showLogin = false;
        return instance;
    }

    @Override
    protected void init() {
        alpha = 0f;
        closing = false;
        this.musicLyricsPanel = null;
        if (!showLogin) {
            loginRenderer = null;
        }

        this.checkDirty();
        if (pendingPanel != null) {
            NCMPanel panel = pendingPanel;
            pendingPanel = null;
            setCurrentPanel(panel);
        }
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void checkDirty() {
        if (this.dirty) {
            this.dirty = false;
            this.layout();

            if (CloudMusic.profile != null)
                this.setCurrentPanel(new HomePanel());
        }
    }

    public void layout() {
        this.basePanel.getChildren().clear();

        RectWidget bg = new RectWidget();
        this.basePanel.addChild(bg);

        this.basePanel.setBeforeRenderCallback(() -> this.basePanel.center());

        this.playlistsPanel = new NavigateBar();
        this.basePanel.addChild(this.playlistsPanel);

        this.basePanel.addChild(this.currentPanelBg);

        this.currentPanelBg.setBeforeRenderCallback(() -> {
            this.currentPanelBg.setBounds(playlistsPanel.getWidth(), 0, this.currentPanelBg.getParentWidth() - playlistsPanel.getWidth(), this.getPanelHeight() * 0.93);
            this.currentPanelBg.setColor(getColor(ColorType.ELEMENT_BACKGROUND));
        });

        this.controlsBar = new ControlsBar();
        this.controlsBar.onInit();
    }

    public double getSpacing() {
        return 16.0;
    }

    public double getPanelWidth() {
        return RenderSystem.getWidth() - this.getSpacing() * 2;
    }

    public double getPanelHeight() {
        return RenderSystem.getHeight() - this.getSpacing() * 2;
    }

    @Override
    public void drawScreen(double mouseX, double mouseY) {
        if (closing && alpha <= 0.02f) {
            Screen target = returnScreen;
            returnScreen = null;
            Minecraft.getInstance().setScreen(target);
            return;
        }

        alpha = Interpolations.interpolate(alpha, closing ? 0f : 1f, 0.4f);

        this.checkDirty();

        int dWheel = consumeWheel();

        double bleedX = RenderSystem.getFullBleedX(), bleedY = RenderSystem.getFullBleedY();
        double bleedW = RenderSystem.getFullBleedWidth(), bleedH = RenderSystem.getFullBleedHeight();
        double screenH = RenderSystem.getHeight();
        Rect.draw(bleedX, bleedY, bleedW, bleedH, hexColor(0f, 0f, 0f, alpha * 0.55f));
        double vignetteH = screenH * 0.35;
        RenderSystem.drawGradientRectTopToBottom(bleedX, bleedY, bleedX + bleedW, vignetteH, hexColor(0f, 0f, 0f, alpha * 0.28f), hexColor(0f, 0f, 0f, 0f));
        RenderSystem.drawGradientRectTopToBottom(bleedX, screenH - vignetteH, bleedX + bleedW, bleedY + bleedH, hexColor(0f, 0f, 0f, 0f), hexColor(0f, 0f, 0f, alpha * 0.32f));

        RenderContext.graphics().pose().pushMatrix();
        this.scaleAtPos(RenderSystem.getWidth() * .5, RenderSystem.getHeight() * .5, 0.9 + (alpha * 0.1));

        this.basePanel.setBounds(this.getPanelWidth(), this.getPanelHeight());

        if (this.musicLyricsPanel == null || this.musicLyricsPanel.alpha <= .9f) {
            this.basePanel.setAlpha(alpha);
            this.basePanel.renderWidget(mouseX, mouseY, dWheel);

            float alphaInterpolateSpeed = 0.4f;
            if (this.prevAnimatingPanel != null) {
                this.prevAnimatingPanel.setAlpha(this.prevAnimatingPanelAlpha = Interpolations.interpolate(this.prevAnimatingPanelAlpha, 0f, alphaInterpolateSpeed));
                this.prevAnimatingPanel.setBounds(this.currentPanelBg.getX(), this.currentPanelBg.getY(), this.currentPanelBg.getWidth(), this.currentPanelBg.getHeight());

                RenderContext.graphics().pose().pushMatrix();
                this.scaleAtPos(this.currentPanelBg.getX() + this.currentPanelBg.getWidth() * .5, this.currentPanelBg.getY() + this.currentPanelBg.getHeight() * .5, 0.9 + (this.prevAnimatingPanel.getAlpha() * 0.1));
                this.prevAnimatingPanel.renderWidget(mouseX, mouseY, dWheel);
                RenderContext.graphics().pose().popMatrix();

                if (this.prevAnimatingPanelAlpha <= 0.02f)
                    this.prevAnimatingPanel = null;
            } else if (this.currentPanel != null) {
                curPanelAlphaAnimation = Interpolations.interpolate(curPanelAlphaAnimation, 1f, alphaInterpolateSpeed);
                this.currentPanel.setAlpha(Math.min(this.basePanel.getAlpha(), curPanelAlphaAnimation));
                this.currentPanel.setBounds(this.currentPanelBg.getX(), this.currentPanelBg.getY(), this.currentPanelBg.getWidth(), this.currentPanelBg.getHeight());

                StencilClipManager.beginClip(this.currentPanelBg.getX(), this.currentPanelBg.getY(), this.currentPanelBg.getWidth(), this.currentPanelBg.getHeight());

                RenderContext.graphics().pose().pushMatrix();
                this.scaleAtPos(this.currentPanelBg.getX() + this.currentPanelBg.getWidth() * .5, this.currentPanelBg.getY() + this.currentPanelBg.getHeight() * .5, 1.1 - (curPanelAlphaAnimation * 0.1));

                this.currentPanel.renderWidget(mouseX, mouseY, dWheel);
                RenderContext.graphics().pose().popMatrix();

                StencilClipManager.endClip();
            }

            this.controlsBar.setAlpha(alpha);
            this.controlsBar.setBounds(this.currentPanelBg.getX(), this.currentPanelBg.getY() + this.currentPanelBg.getHeight(), this.currentPanelBg.getWidth(), this.getPanelHeight() - this.currentPanelBg.getHeight());
            this.controlsBar.renderWidget(mouseX, mouseY, dWheel);

            int hairline = hexColor(1f, 1f, 1f, alpha * 0.05f);
            double sepX = this.currentPanelBg.getX();
            Rect.draw(sepX, this.basePanel.getY(), 1, this.basePanel.getHeight(), hairline);
            Rect.draw(sepX, this.currentPanelBg.getY() + this.currentPanelBg.getHeight(), this.currentPanelBg.getWidth(), 1, hairline);

            this.playlistsPanel.renderSuggestionOverlay(mouseX, mouseY);
        }

        if (this.musicLyricsPanel != null) {
            StencilClipManager.beginClip(basePanel.getX(), basePanel.getY(), basePanel.getWidth(), basePanel.getHeight());
            Rect.draw(basePanel.getX(), basePanel.getY(), basePanel.getWidth(), basePanel.getHeight(), getColor(ColorType.GENERIC_BACKGROUND) | ((int) (this.musicLyricsPanel.alpha * 255)) << 24);
            this.musicLyricsPanel.onRender(mouseX, mouseY, basePanel.getX(), basePanel.getY(), basePanel.getWidth(), basePanel.getHeight(), dWheel);
            StencilClipManager.endClip();

            if (this.musicLyricsPanel.shouldClose())
                this.musicLyricsPanel = null;
        }

        boolean loggedIn = OptionsUtil.hasAuthentication();

        if (showLogin && !loggedIn && this.loginRenderer == null) {
            this.loginRenderer = new LoginRenderer();
        }

        if (this.loginRenderer != null) {
            this.loginRenderer.render(mouseX, mouseY, basePanel.getX(), basePanel.getY(), basePanel.getWidth(), basePanel.getHeight(), basePanel.getAlpha());

            if (this.loginRenderer.canClose() && OptionsUtil.hasAuthentication()) {
                this.loginRenderer = null;
                AsyncUtil.runAsync(() -> {
                    CloudMusic.loadNCM(OptionsUtil.getCookie());

                    AsyncUtil.runOnRenderThread(() -> {
                        this.layout();

                        if (CloudMusic.profile != null)
                            this.setCurrentPanel(new HomePanel());
                    });
                });
            }
        }

        this.renderDownloadingPanel();

        RenderContext.graphics().pose().popMatrix();
    }

    private float downloadPanelAlpha = 0.0f;

    private void renderDownloadingPanel() {
        double assetPanelHeight = this.renderAssetDownloadPanel();
        this.renderSongDownloadPanel(8 + assetPanelHeight);
    }

    private static final double PROGRESS_BAR_HEIGHT = 8;
    private static final double PROGRESS_PADDING = 8;
    private static final double PROGRESS_PANEL_WIDTH = 240;

    private float assetPanelAlpha = 0.0f;
    private double assetPanelX;
    private double assetPanelY;
    private double assetPanelWidth;
    private double assetPanelHeight;
    private boolean assetPanelFailed;
    private boolean assetPanelLogged;

    private double renderAssetDownloadPanel() {
        AssetManager.Snapshot snapshot = AssetManager.get().snapshot();
        boolean failed = snapshot.failed();
        boolean visible = snapshot.active() || failed;

        this.assetPanelAlpha = Interpolations.interpolate(this.assetPanelAlpha, visible ? 1f : 0f, 0.3f);
        this.assetPanelFailed = failed;

        if (this.assetPanelAlpha <= 0.02f)
            return 0;

        if (!this.assetPanelLogged) {
            this.assetPanelLogged = true;
            Platform.log("[asset] progress panel rendered, phase " + snapshot.phase()
                    + ", " + snapshot.readyCount() + "/" + snapshot.files().size() + " ready");
        }

        CFontRenderer titleFont = FontManager.pf34bold;
        CFontRenderer detailFont = FontManager.pf25bold;
        CFontRenderer hintFont = FontManager.pf20;
        if (titleFont == null || detailFont == null || hintFont == null)
            return 0;

        String title;
        String detail;
        String hint;
        if (snapshot.unavailable()) {
            title = I18n.get("tritium-music.ui.assets.failed");
            detail = I18n.get("tritium-music.ui.assets.route.unreachable");
            hint = I18n.get("tritium-music.ui.assets.unavailable_hint", disabledFeatures());
        } else if (snapshot.retrying()) {
            title = I18n.get("tritium-music.ui.assets.failed");
            detail = I18n.get("tritium-music.ui.assets.retry_in",
                    AssetFormat.duration(snapshot.retrySeconds()), (snapshot.attempt() + 1) + "/" + snapshot.maxAttempts());
            hint = I18n.get("tritium-music.ui.assets.route.unreachable");
        } else if (snapshot.phase() == AssetManager.Phase.RESOLVING) {
            title = I18n.get("tritium-music.ui.assets.title");
            detail = I18n.get("tritium-music.ui.assets.resolving");
            hint = I18n.get("tritium-music.ui.assets.route.pending");
        } else {
            title = I18n.get("tritium-music.ui.assets.title");
            detail = Math.round(snapshot.fraction() * 100) + "%  ·  " + AssetFormat.speed(snapshot.bytesPerSecond());
            hint = transitionText(snapshot);
        }

        float alpha = this.assetPanelAlpha * this.alpha;
        double textHeight = titleFont.getHeight() + detailFont.getHeight() + hintFont.getHeight();
        double panelHeight = PROGRESS_PADDING + textHeight + PROGRESS_PADDING
                + PROGRESS_BAR_HEIGHT + PROGRESS_PADDING;

        this.assetPanelWidth = PROGRESS_PANEL_WIDTH;
        this.assetPanelHeight = panelHeight;
        this.assetPanelX = RenderSystem.getWidth() * .5 - PROGRESS_PANEL_WIDTH * .5;
        this.assetPanelY = 8 + -(8 + panelHeight) * (1 - this.assetPanelAlpha);

        Rect.draw(this.assetPanelX, this.assetPanelY, PROGRESS_PANEL_WIDTH, panelHeight, RenderSystem.reAlpha(0x202020, alpha));

        double centerX = RenderSystem.getWidth() * .5;
        double textY = this.assetPanelY + PROGRESS_PADDING;

        titleFont.drawCenteredString(title, centerX, textY, hexColor(1f, 1f, 1f, alpha));
        textY += titleFont.getHeight();
        detailFont.drawCenteredString(detail, centerX, textY, hexColor(1f, 1f, 1f, alpha));
        textY += detailFont.getHeight();
        hintFont.drawCenteredString(hint, centerX, textY, hexColor(1f, 1f, 1f, alpha * 0.65f));

        double barX = this.assetPanelX + PROGRESS_PADDING;
        double barWidth = PROGRESS_PANEL_WIDTH - PROGRESS_PADDING * 2;
        double barY = this.assetPanelY + panelHeight - PROGRESS_PADDING - PROGRESS_BAR_HEIGHT;
        roundedRect(barX, barY, barWidth, PROGRESS_BAR_HEIGHT, 3, hexColor(1f, 1f, 1f, 0.5f * alpha));

        double progress = snapshot.fraction();
        if (progress > 0.001) {
            StencilClipManager.beginClip(barX, barY, barWidth * progress, PROGRESS_BAR_HEIGHT);
            roundedRect(barX, barY, barWidth, PROGRESS_BAR_HEIGHT, 3, hexColor(1f, 1f, 1f, alpha));
            StencilClipManager.endClip();
        }

        return panelHeight + 6;
    }

    private String disabledFeatures() {
        List<String> disabled = new ArrayList<>();
        if (!AutoMixSupport.isAvailable()) {
            disabled.add(I18n.get("tritium-music.ui.feature.automix.name"));
        }
        if (!AutoMixSupport.isAvailable() || !AssetManager.get().snapshot().essentialReady()) {
            disabled.add(I18n.get("tritium-music.ui.feature.fonts.name"));
        }
        return disabled.isEmpty() ? I18n.get("tritium-music.ui.common.none") : String.join(" / ", disabled);
    }

    private String transitionText(AssetManager.Snapshot snapshot) {
        String route;
        AssetRoute resolved = snapshot.route();
        if (resolved == null) {
            route = I18n.get("tritium-music.ui.assets.route.pending");
        } else if (resolved.mirror()) {
            route = I18n.get("tritium-music.ui.assets.route.mirror", resolved.host());
        } else {
            route = I18n.get("tritium-music.ui.assets.route.direct");
        }

        for (AssetManager.FileStatus status : snapshot.files()) {
            if (status.state() == AssetManager.State.DOWNLOADING || status.state() == AssetManager.State.VERIFYING) {
                return status.asset().fileName() + "  ·  " + route;
            }
        }
        return route;
    }

    private void renderSongDownloadPanel(double topOffset) {
        MusicState state = MusicState.get();
        this.downloadPanelAlpha = Interpolations.interpolate(this.downloadPanelAlpha, state.isDownloading() ? 1f : 0f, 0.3f);

        if (this.downloadPanelAlpha <= 0.02f)
            return;

        double downloadProgress = state.getDownloadProgress();
        String downloadSpeed = state.getDownloadSpeed();

        double downloadPanelWidth = PROGRESS_PANEL_WIDTH;
        double downloadPanelHeight = 60;
        double progressBarWidth = downloadPanelWidth - PROGRESS_PADDING * 2;
        double progressBarHeight = PROGRESS_BAR_HEIGHT;

        double offsetY = topOffset + -(8 + downloadPanelHeight) * (1 - this.downloadPanelAlpha);
        Rect.draw(RenderSystem.getWidth() * .5 - downloadPanelWidth * .5, offsetY, downloadPanelWidth, downloadPanelHeight, RenderSystem.reAlpha(0x202020, downloadPanelAlpha * alpha));
        FontManager.pf34bold.drawCenteredString(I18n.get("tritium-music.ui.download.downloading"), RenderSystem.getWidth() * .5, offsetY + PROGRESS_PADDING, hexColor(1f, 1f, 1f, downloadPanelAlpha * alpha));
        FontManager.pf25bold.drawCenteredString(String.valueOf(downloadSpeed), RenderSystem.getWidth() * .5, offsetY + PROGRESS_PADDING + FontManager.pf34bold.getHeight(), hexColor(1f, 1f, 1f, downloadPanelAlpha * alpha));
        roundedRect(RenderSystem.getWidth() * .5 - progressBarWidth * .5, offsetY + downloadPanelHeight - PROGRESS_PADDING - progressBarHeight, progressBarWidth, progressBarHeight, 3, hexColor(1f, 1f, 1f, .5f * downloadPanelAlpha * alpha));

        StencilClipManager.beginClip(RenderSystem.getWidth() * .5 - progressBarWidth * .5,
                offsetY + downloadPanelHeight - PROGRESS_PADDING - progressBarHeight, progressBarWidth * downloadProgress, progressBarHeight);
        roundedRect(RenderSystem.getWidth() * .5 - progressBarWidth * .5, offsetY + downloadPanelHeight - PROGRESS_PADDING - progressBarHeight, progressBarWidth, progressBarHeight, 3, hexColor(1f, 1f, 1f, downloadPanelAlpha * alpha));
        StencilClipManager.endClip();
    }

    public LoginRenderer loginRenderer = null;

    int currentActionPointer = 0;
    List<Runnable> actions = new ArrayList<>();

    public void setCurrentPanel(NCMPanel panel) {
        this.innerSetCurrentPanel(panel, true);

        if (panel != null) {
            Runnable action = () -> this.innerSetCurrentPanel(panel, false);

            if (actions.isEmpty()) {
                currentActionPointer = 0;
                actions.add(action);
            } else {
                ++currentActionPointer;

                while (actions.size() > currentActionPointer + 1)
                    actions.removeLast();

                if (currentActionPointer < actions.size()) {
                    actions.set(currentActionPointer, action);
                } else {
                    actions.add(action);
                }
            }
        }
    }

    public void refreshLibraryView() {
        long selectedPlaylistId = this.currentPanel instanceof PlaylistPanel panel && !panel.playList.isSearchMode()
                ? panel.playList.getId()
                : -1;
        this.playlistsPanel.refreshPlaylists(selectedPlaylistId);
        if (this.currentPanel instanceof PlaylistPanel panel && !panel.playList.isSearchMode()) {
            CloudMusic.playLists.stream()
                    .filter(playList -> playList.getId() == panel.playList.getId())
                    .findFirst()
                    .ifPresent(playList -> {
                        PlaylistPanel refreshedPanel = new PlaylistPanel(playList);
                        this.innerSetCurrentPanel(refreshedPanel, true);
                        if (currentActionPointer >= 0 && currentActionPointer < actions.size()) {
                            actions.set(currentActionPointer, () -> this.innerSetCurrentPanel(refreshedPanel, false));
                        }
                    });
        }
    }

    private void innerSetCurrentPanel(NCMPanel panel, boolean shouldCallInit) {
        if (this.currentPanel != null && this.currentPanel != panel) {
            this.currentPanel.detach();
        }
        this.prevAnimatingPanel = this.currentPanel;
        this.prevAnimatingPanelAlpha = 1.0f;
        this.currentPanel = panel;
        if (panel != null) {
            if (shouldCallInit)
                this.currentPanel.onInit();
            this.currentPanel.setAlpha(0);
            this.curPanelAlphaAnimation = 0f;
        }
    }

    @Override
    public void removed() {
        if (this.currentPanel != null) {
            this.currentPanel.onRemoved();
        }
        super.removed();
    }

    @Override
    public void onKeyTyped(char typedChar, int keyCode) {

        if (this.basePanel.onKeyTypedReceived(typedChar, keyCode)) {
            return;
        }

        if (this.currentPanel != null && this.currentPanel.onKeyTypedReceived(typedChar, keyCode)) {
            return;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.musicLyricsPanel != null)
                this.musicLyricsPanel.close();
            else
                closing = true;
        }

        if (keyCode == GLFW.GLFW_KEY_SPACE && CloudMusic.currentlyPlaying != null && CloudMusic.player != null && !CloudMusic.player.isFinished()) {
            if (CloudMusic.player.isPausing())
                CloudMusic.player.unpause();
            else
                CloudMusic.player.pause();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (this.assetPanelFailed && this.assetPanelAlpha > 0.5f
                && RenderSystem.isHovered(mouseX, mouseY, assetPanelX, assetPanelY, assetPanelWidth, assetPanelHeight)) {
            AssetManager.get().retry();
            return;
        }

        if (musicLyricsPanel == null) {
            if (this.playlistsPanel != null && this.playlistsPanel.handleSuggestionClick(mouseX, mouseY, mouseButton)) {
                return;
            }
            this.basePanel.onMouseClickReceived(mouseX, mouseY, mouseButton);

            if (this.currentPanel != null)
                this.currentPanel.onMouseClickReceived(mouseX, mouseY, mouseButton);

            this.controlsBar.onMouseClickReceived(mouseX, mouseY, mouseButton);

            if (mouseButton == 4) {
                if (currentActionPointer >= actions.size() - 1) {
                    currentActionPointer = actions.size() - 1;
                } else {
                    currentActionPointer++;
                    actions.get(currentActionPointer).run();
                }
            } else if (mouseButton == 3) {
                if (currentActionPointer > 0) {
                    --currentActionPointer;
                    actions.get(currentActionPointer).run();
                }
            }
        } else {
            this.musicLyricsPanel.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void mousePressed(double mouseX, double mouseY, int mouseButton) {
        if (musicLyricsPanel != null) {
            return;
        }

        this.basePanel.onMousePressReceived(mouseX, mouseY, mouseButton);

        if (this.currentPanel != null)
            this.currentPanel.onMousePressReceived(mouseX, mouseY, mouseButton);

        this.controlsBar.onMousePressReceived(mouseX, mouseY, mouseButton);
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int mouseButton) {
        if (musicLyricsPanel == null && currentPanel instanceof PlaylistPanel playlistPanel) {
            playlistPanel.onMouseReleased(mouseX, mouseY, mouseButton);
        }
    }

    public enum ColorType {
        GENERIC_BACKGROUND,
        ELEMENT_BACKGROUND,
        ELEMENT_HOVER,
        PRIMARY_TEXT,
        SECONDARY_TEXT
    }

    public static int getColor(ColorType type) {
        return switch (type) {
            case GENERIC_BACKGROUND -> 0x0E0F12;
            case ELEMENT_BACKGROUND -> 0x16171B;
            case ELEMENT_HOVER -> 0x24262C;
            case PRIMARY_TEXT -> 0xF2F3F5;
            case SECONDARY_TEXT -> 0x8A8D94;
        };
    }
}
