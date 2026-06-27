package tritium.music.client.screens.ncm;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.StencilClipManager;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.ui.container.Panel;
import tritium.music.client.rendering.ui.widgets.RectWidget;
import tritium.music.client.screens.BaseScreen;
import tritium.music.client.screens.clickgui.music.LoginRenderer;
import tritium.music.client.screens.ncm.panels.ControlsBar;
import tritium.music.client.screens.ncm.panels.HomePanel;
import tritium.music.client.screens.ncm.panels.NavigateBar;
import tritium.music.core.CloudMusic;
import tritium.music.core.MusicState;
import tritium.music.core.ncm.OptionsUtil;
import tritium.music.core.util.AsyncUtil;

import java.util.ArrayList;
import java.util.List;

public class NCMScreen extends BaseScreen {

    @Getter
    private static final NCMScreen instance = new NCMScreen();

    float alpha = 0f;
    boolean closing = false;

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

    public NCMScreen() {
    }

    public static void open() {
        Minecraft.getInstance().setScreenAndShow(instance);
    }

    @Override
    protected void init() {
        alpha = 0f;
        closing = false;

        this.checkDirty();

        if (this.musicLyricsPanel != null)
            this.musicLyricsPanel.onInit();
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
        if (closing && alpha <= 0.02f)
            Minecraft.getInstance().setScreenAndShow(null);

        alpha = Interpolations.interpolate(alpha, closing ? 0f : 1f, 0.4f);

        this.checkDirty();

        int dWheel = consumeWheel();

        double screenW = RenderSystem.getWidth(), screenH = RenderSystem.getHeight();
        Rect.draw(0, 0, screenW, screenH, hexColor(0f, 0f, 0f, alpha * 0.55f));
        double vignetteH = screenH * 0.35;
        RenderSystem.drawGradientRectTopToBottom(0, 0, screenW, vignetteH, hexColor(0f, 0f, 0f, alpha * 0.28f), hexColor(0f, 0f, 0f, 0f));
        RenderSystem.drawGradientRectTopToBottom(0, screenH - vignetteH, screenW, screenH, hexColor(0f, 0f, 0f, 0f), hexColor(0f, 0f, 0f, alpha * 0.32f));

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

                StencilClipManager.beginClip(() -> Rect.draw(this.currentPanelBg.getX(), this.currentPanelBg.getY(), this.currentPanelBg.getWidth(), this.currentPanelBg.getHeight(), -1));

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
        }

        if (this.musicLyricsPanel != null) {
            StencilClipManager.beginClip(() -> Rect.draw(basePanel.getX(), basePanel.getY(), basePanel.getWidth(), basePanel.getHeight(), -1));
            Rect.draw(basePanel.getX(), basePanel.getY(), basePanel.getWidth(), basePanel.getHeight(), getColor(ColorType.GENERIC_BACKGROUND) | ((int) (this.musicLyricsPanel.alpha * 255)) << 24);
            this.musicLyricsPanel.onRender(mouseX, mouseY, basePanel.getX(), basePanel.getY(), basePanel.getWidth(), basePanel.getHeight(), dWheel);
            StencilClipManager.endClip();

            if (this.musicLyricsPanel.shouldClose())
                this.musicLyricsPanel = null;
        }

        boolean loggedIn = !OptionsUtil.getCookie().isEmpty();

        if (!loggedIn && this.loginRenderer == null) {
            this.loginRenderer = new LoginRenderer();
        }

        if (this.loginRenderer != null) {
            this.loginRenderer.render(mouseX, mouseY, basePanel.getX(), basePanel.getY(), basePanel.getWidth(), basePanel.getHeight(), basePanel.getAlpha());

            if (this.loginRenderer.canClose() && !OptionsUtil.getCookie().isEmpty()) {
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
        MusicState state = MusicState.get();
        this.downloadPanelAlpha = Interpolations.interpolate(this.downloadPanelAlpha, state.isDownloading() ? 1f : 0f, 0.3f);

        if (this.downloadPanelAlpha <= 0.02f)
            return;

        double downloadProgress = state.getDownloadProgress();
        String downloadSpeed = state.getDownloadSpeed();

        double downloadPanelWidth = 240;
        double downloadPanelHeight = 60;
        double progressBarWidth = downloadPanelWidth - 16;
        double progressBarHeight = 8;

        double offsetY = 8 + -(8 + downloadPanelHeight) * (1 - downloadPanelAlpha);
        Rect.draw(RenderSystem.getWidth() * .5 - downloadPanelWidth * .5, offsetY, downloadPanelWidth, downloadPanelHeight, RenderSystem.reAlpha(0x202020, (float) (downloadPanelAlpha * alpha)));
        FontManager.pf34bold.drawCenteredString("Downloading...", RenderSystem.getWidth() * .5, offsetY + 8, hexColor(1f, 1f, 1f, downloadPanelAlpha * alpha));
        FontManager.pf25bold.drawCenteredString(String.valueOf(downloadSpeed), RenderSystem.getWidth() * .5, offsetY + 8 + FontManager.pf34bold.getHeight(), hexColor(1f, 1f, 1f, downloadPanelAlpha * alpha));
        roundedRect(RenderSystem.getWidth() * .5 - progressBarWidth * .5, offsetY + downloadPanelHeight - 8 - progressBarHeight, progressBarWidth, progressBarHeight, 3, hexColor(1f, 1f, 1f, .5f * downloadPanelAlpha * alpha));

        StencilClipManager.beginClip(() -> Rect.draw(RenderSystem.getWidth() * .5 - progressBarWidth * .5, offsetY + downloadPanelHeight - 8 - progressBarHeight, progressBarWidth * downloadProgress, progressBarHeight, -1));
        roundedRect(RenderSystem.getWidth() * .5 - progressBarWidth * .5, offsetY + downloadPanelHeight - 8 - progressBarHeight, progressBarWidth, progressBarHeight, 3, hexColor(1f, 1f, 1f, downloadPanelAlpha * alpha));
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

    private void innerSetCurrentPanel(NCMPanel panel, boolean shouldCallInit) {
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
    public void mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (musicLyricsPanel == null) {
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
