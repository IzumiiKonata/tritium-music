package tritium.music.client.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.SharedRenderingConstants;
import tritium.music.client.rendering.TextField;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.shader.EffectQueue;
import tritium.music.client.rendering.ui.AbstractWidget;
import tritium.music.client.util.CursorUtils;
import tritium.music.core.assets.AssetPlatform;
import tritium.music.platform.Platform;

import java.util.concurrent.atomic.AtomicBoolean;

public class BaseScreen extends Screen implements SharedRenderingConstants {

    private static final double TOUCH_SCROLL_STEP = 12.0;
    private static final double TOUCH_SCROLL_THRESHOLD = 6.0;
    private static final double CLICK_SLOP = 6.0;
    private static final double FLING_MIN_VELOCITY = 0.02;
    private static final double FLING_MAX_VELOCITY = 0.35;
    private static final double FLING_DECAY_PER_MILLIS = 0.004;
    private static final int MAX_PENDING_WHEEL = 64;
    private static final AtomicBoolean WHEEL_EVENTS_LOGGED = new AtomicBoolean();

    public boolean lmbPressed = false, rmbPressed = false;

    private int clickMoveTicks = 0;
    private long lastClick = 0L;

    private int pendingWheel = 0;
    private double dragStartX, dragStartY, lastDragX, lastDragY, dragScrollNotches, dragVelocity;
    private long lastDragNanos = System.nanoTime();
    private boolean dragScrolling;

    private boolean pressActive;
    private boolean pressMoved;
    private int pressButton = -1;
    private double pressX, pressY;

    private double flingVelocity, flingRemainder;
    private long flingNanos = System.nanoTime();

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

protected boolean clickOnPress() {
        return false;
    }

    private static boolean touchScrollEnabled() {
        return AssetPlatform.isAndroid() || Boolean.getBoolean("tritium.touchScroll");
    }

//    @Override
//    public void extractBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
//        if (screenAlpha() > 0.5f && minecraft.options.getMenuBackgroundBlurriness() >= 1.0f) {
//            graphics.blurBeforeThisStratum();
//        }
//    }

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

private void trackPress(double mouseX, double mouseY) {
        if (!this.pressActive || this.pressMoved) {
            return;
        }
        if (Math.abs(mouseX - this.pressX) > CLICK_SLOP || Math.abs(mouseY - this.pressY) > CLICK_SLOP) {
            this.pressMoved = true;
        }
    }

    private void updateTouchScroll(double mouseX, double mouseY) {
        long now = System.nanoTime();
        double deltaY = mouseY - this.lastDragY;
        double elapsed = Math.max(1.0, (now - this.lastDragNanos) / 1_000_000.0);
        this.lastDragX = mouseX;
        this.lastDragY = mouseY;
        this.lastDragNanos = now;

        if (!touchScrollEnabled()) {
            return;
        }

        if (!this.dragScrolling) {
            double totalX = mouseX - this.dragStartX;
            double totalY = mouseY - this.dragStartY;
            if (Math.abs(totalY) < TOUCH_SCROLL_THRESHOLD || Math.abs(totalY) <= Math.abs(totalX)) {
                return;
            }
            this.dragScrolling = true;
            this.pressMoved = true;
            this.dragScrollNotches = 0;
            this.dragVelocity = 0;
            deltaY = totalY;
            elapsed = Math.max(elapsed, 16.0);
        }

        double notches = deltaY / TOUCH_SCROLL_STEP;
        this.dragScrollNotches += notches;
        this.dragVelocity = this.dragVelocity * 0.55 + notches / elapsed * 0.45;

        int whole = (int) this.dragScrollNotches;
        if (whole != 0) {
            this.dragScrollNotches -= whole;
            this.pendingWheel = clampWheel(this.pendingWheel + whole);
        }
    }

    private void updateFling() {
        if (this.flingVelocity == 0) {
            return;
        }

        long now = System.nanoTime();
        double elapsed = Math.max(1.0, Math.min(64.0, (now - this.flingNanos) / 1_000_000.0));
        this.flingNanos = now;

        double notches = this.flingVelocity * elapsed + this.flingRemainder;
        this.flingVelocity *= Math.exp(-FLING_DECAY_PER_MILLIS * elapsed);
        if (Math.abs(this.flingVelocity) < FLING_MIN_VELOCITY) {
            this.flingVelocity = 0;
        }

        int whole = (int) notches;
        this.flingRemainder = notches - whole;
        if (whole != 0) {
            this.pendingWheel = clampWheel(this.pendingWheel + whole);
        }
    }

    private static int clampWheel(int wheel) {
        return Math.max(-MAX_PENDING_WHEEL, Math.min(MAX_PENDING_WHEEL, wheel));
    }

    private void stopFling() {
        this.flingVelocity = 0;
        this.flingRemainder = 0;
        this.dragVelocity = 0;
    }

    private boolean isButtonDown(int button) {
        return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), button) == GLFW.GLFW_PRESS;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        EffectQueue.beginFrame();
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

                if (lmb) {
                    this.trackPress(mx, my);
                    this.updateTouchScroll(mx, my);
                } else {
                    this.dragScrolling = false;
                    this.lastDragX = mx;
                    this.lastDragY = my;
                    this.lastDragNanos = System.nanoTime();
                }

                if (!this.dragScrolling) {
                    this.updateFling();
                }

                this.drawScreen(mx, my);
                this.renderLast(mx, my);
            } finally {
                graphics.pose().popMatrix();
            }
        } finally {
            CursorUtils.applyOverride();
            tritium.music.client.rendering.font.TextureAtlas.flushAllDirty();
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

//    @Override
//    public boolean preeditUpdated(PreeditEvent event) {
//        return TextField.preeditUpdated(event);
//    }

    @Override
    public void removed() {
        TextField.clearFocus();
        this.pendingWheel = 0;
        this.dragScrolling = false;
        this.pressActive = false;
        this.pressButton = -1;
        this.stopFling();
        AbstractWidget.clearPendingClick();
        super.removed();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = RenderSystem.getMouseX();
        double my = RenderSystem.getMouseY();
        clickMoveTicks = 0;
        lastClick = System.currentTimeMillis();
        boolean flinging = this.flingVelocity != 0;
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            lmbPressed = true;
            this.dragStartX = mx;
            this.dragStartY = my;
            this.dragScrollNotches = 0;
            this.dragScrolling = false;
            this.stopFling();
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) rmbPressed = true;

        this.pressActive = true;
        this.pressMoved = flinging;
        this.pressButton = toLegacyButton(event.button());
        this.pressX = mx;
        this.pressY = my;
        this.lastDragX = mx;
        this.lastDragY = my;
        this.lastDragNanos = System.nanoTime();

        TextField.clearFocusOutside(mx, my);

        if (clickOnPress()) {
            this.pressActive = false;
            this.pressButton = -1;
            this.mouseClicked(mx, my, toLegacyButton(event.button()));
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mx = RenderSystem.getMouseX();
        double my = RenderSystem.getMouseY();
int button = toLegacyButton(event.button());
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) this.lmbPressed = false;
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) this.rmbPressed = false;

        if (this.pressActive && this.pressButton == button) {
            boolean click = !this.pressMoved && !this.dragScrolling;
            this.pressActive = false;
            this.pressButton = -1;
            if (click) {
                this.mouseClicked(this.pressX, this.pressY, button);
                AbstractWidget.commitPendingClick(mx, my);
            } else {
                AbstractWidget.clearPendingClick();
            }
        }

        if (this.dragScrolling) {
            this.dragScrolling = false;
            double velocity = this.dragVelocity;
            this.stopFling();
            if (Math.abs(velocity) >= FLING_MIN_VELOCITY) {
                this.flingVelocity = Math.max(-FLING_MAX_VELOCITY, Math.min(FLING_MAX_VELOCITY, velocity));
                this.flingNanos = System.nanoTime();
            }
        }

        this.mouseReleased(mx, my, button);
        return true;
    }

    private static int toLegacyButton(int glfwButton) {
        return switch (glfwButton) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> 0;
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> 2;
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> 1;
            default -> glfwButton;
        };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double mx = RenderSystem.getMouseX();
        double my = RenderSystem.getMouseY();
        double axis = scrollY != 0.0 ? scrollY : scrollX;
        int dWheel = (int) Math.signum(axis);
        if (AssetPlatform.isAndroid() && WHEEL_EVENTS_LOGGED.compareAndSet(false, true)) {
            Platform.log("[NCM] Scroll events are delivered (x=" + scrollX + ", y=" + scrollY + ")");
        }
        this.pendingWheel += dWheel;
        this.mouseScrolled(mx, my, dWheel);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
