package tritium.music.client.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import tritium.music.client.render.GuiStateReset;
import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.SharedRenderingConstants;
import tritium.music.client.rendering.TextField;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.TextureAtlas;
import tritium.music.client.rendering.shader.EffectQueue;
import tritium.music.client.util.CursorUtils;
import tritium.music.client.util.MouseUtil;

public class BaseScreen extends Screen implements SharedRenderingConstants {

    public boolean lmbPressed = false, rmbPressed = false;

    private int clickMoveTicks = 0;
    private long lastClick = 0L;

    private int pendingWheel = 0;

    protected int consumeWheel() {
        int w = pendingWheel;
        pendingWheel = 0;
        return w;
    }

    protected BaseScreen() {
        super(Component.empty());
    }

    protected float screenAlpha() {
        return 1f;
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        if (screenAlpha() > 0.5f) {
            super.renderBackground(graphics);
        }
    }

    public void drawScreen(double mouseX, double mouseY) {
    }

    public void onKeyTyped(char typedChar, int keyCode) {
    }

    public void onMouseClicked(double mouseX, double mouseY, int mouseButton) {
    }

    public void onMouseReleased(double mouseX, double mouseY, int mouseButton) {
    }

    public void mouseClickMove(double mouseX, double mouseY, int mouseButton, long timeSinceLastClick) {
    }

    public void mouseScrolled(double mouseX, double mouseY, int dWheel) {
    }

    public void renderLast(double mouseX, double mouseY) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        EffectQueue.beginFrame();
        RenderContext.begin(graphics, partialTick);
        Interpolations.calcFrameDelta();
        CursorUtils.resetOverride();

        try {
            RenderSystem.resetColor();

            graphics.pose().pushPose();
            try {
                double normalizer = RenderSystem.getScaleNormalizer();
                graphics.pose().translate((float) RenderSystem.getOffsetX(), (float) RenderSystem.getOffsetY(), 0f);
                graphics.pose().scale((float) normalizer, (float) normalizer, 1f);

                double mx = RenderSystem.getMouseX();
                double my = RenderSystem.getMouseY();

                boolean lmb = MouseUtil.isButtonDown(MouseUtil.BUTTON_LEFT);
                boolean rmb = MouseUtil.isButtonDown(MouseUtil.BUTTON_RIGHT);

                if (lmb || rmb) {
                    if (clickMoveTicks > 1) {
                        this.mouseClickMove(mx, my, lmb ? MouseUtil.LEGACY_LEFT : MouseUtil.LEGACY_RIGHT, System.currentTimeMillis() - lastClick);
                    }
                    clickMoveTicks++;
                }

                if (!lmb && lmbPressed) lmbPressed = false;
                if (!rmb && rmbPressed) rmbPressed = false;

                this.drawScreen(mx, my);
                this.renderLast(mx, my);
            } finally {
                graphics.pose().popPose();
            }
        } finally {
            CursorUtils.applyOverride();
            TextureAtlas.flushAllDirty();
            tritium.music.client.rendering.StencilClipManager.endFrame();
            GuiStateReset.restore();
            RenderContext.end();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        this.onKeyTyped('\0', keyCode);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        this.onKeyTyped(codePoint, 0);
        return true;
    }

    @Override
    public void removed() {
        MouseUtil.clearButtons();
        TextField.clearFocus();
        super.removed();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double mx = RenderSystem.getMouseX();
        double my = RenderSystem.getMouseY();
        clickMoveTicks = 0;
        lastClick = System.currentTimeMillis();
        if (button == MouseUtil.LEGACY_LEFT) lmbPressed = true;
        if (button == MouseUtil.LEGACY_RIGHT) rmbPressed = true;
        MouseUtil.setButtonDown(MouseUtil.fromLegacy(button), true);
        TextField.clearFocusOutside(mx, my);
        this.onMouseClicked(mx, my, button);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        double mx = RenderSystem.getMouseX();
        double my = RenderSystem.getMouseY();
        if (button == MouseUtil.LEGACY_LEFT) this.lmbPressed = false;
        if (button == MouseUtil.LEGACY_RIGHT) this.rmbPressed = false;
        MouseUtil.setButtonDown(MouseUtil.fromLegacy(button), false);
        this.onMouseReleased(mx, my, button);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        double mx = RenderSystem.getMouseX();
        double my = RenderSystem.getMouseY();
        int dWheel = (int) Math.signum(scrollY);
        this.pendingWheel += dWheel;
        this.mouseScrolled(mx, my, dWheel);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
