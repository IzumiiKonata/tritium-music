package tritium.music.client.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.SharedRenderingConstants;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.util.CursorUtils;

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

    /** Screen-wide fade alpha (1 = fully open). Subclasses with an open/close animation override this. */
    protected float screenAlpha() {
        return 1f;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Skip vanilla's panorama/menu background (the screen draws its own dark overlay) and
        // fade the backdrop blur with the screen alpha — vanilla's blur is binary, so it drops
        // out partway through the close animation rather than snapping at the very end.
        if (screenAlpha() > 0.5f && minecraft.options.getMenuBackgroundBlurriness() >= 1.0f) {
            graphics.blurBeforeThisStratum();
        }
    }

    public void drawScreen(double mouseX, double mouseY) {
    }

    public void onKeyTyped(char typedChar, int keyCode) {
    }

    public void mouseClicked(double mouseX, double mouseY, int mouseButton) {
    }

    public void mouseReleased(double mouseX, double mouseY, int mouseButton) {
    }

    public void mouseClickMove(double mouseX, double mouseY, int mouseButton, long timeSinceLastClick) {
    }

    public void mouseScrolled(double mouseX, double mouseY, int dWheel) {
    }

    public void renderLast(double mouseX, double mouseY) {
    }

    private boolean isButtonDown(int button) {
        return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), button) == GLFW.GLFW_PRESS;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        RenderContext.begin(graphics, partialTick);
        Interpolations.calcFrameDelta();
        CursorUtils.resetOverride();

        try {
            RenderSystem.resetColor();

            graphics.pose().pushMatrix();
            try {
                double normalizer = RenderSystem.getScaleNormalizer();
                double offsetX = RenderSystem.getOffsetX();
                double offsetY = RenderSystem.getOffsetY();
                graphics.pose().translate((float) offsetX, (float) offsetY);
                graphics.pose().scale((float) normalizer, (float) normalizer);

                double mx = RenderSystem.getMouseX();
                double my = RenderSystem.getMouseY();

                boolean lmb = isButtonDown(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                boolean rmb = isButtonDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT);

                if (lmb || rmb) {
                    if (clickMoveTicks > 1) {
                        this.mouseClickMove(mx, my, lmb ? 0 : 1, System.currentTimeMillis() - lastClick);
                    }
                    clickMoveTicks++;
                }

                if (!lmb && lmbPressed) lmbPressed = false;
                if (!rmb && rmbPressed) rmbPressed = false;

                this.drawScreen(mx, my);
                this.renderLast(mx, my);
            } finally {
                graphics.pose().popMatrix();
            }
        } finally {
            CursorUtils.applyOverride();
            RenderContext.end();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        this.onKeyTyped('\0', event.key());
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        this.onKeyTyped((char) event.codepoint(), 0);
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = RenderSystem.getMouseX();
        double my = RenderSystem.getMouseY();
        clickMoveTicks = 0;
        lastClick = System.currentTimeMillis();
        if (event.button() == 0) lmbPressed = true;
        if (event.button() == 1) rmbPressed = true;
        this.mouseClicked(mx, my, event.button());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mx = RenderSystem.getMouseX();
        double my = RenderSystem.getMouseY();
        if (event.button() == 0) this.lmbPressed = false;
        if (event.button() == 1) this.rmbPressed = false;
        this.mouseReleased(mx, my, event.button());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
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
