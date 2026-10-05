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
import tritium.music.client.rendering.ui.AbstractWidget;
import tritium.music.client.util.CursorUtils;
import tritium.music.client.util.MouseUtil;
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

    public void mousePressed(double mouseX, double mouseY, int mouseButton) {
    }

    public void onMouseReleased(double mouseX, double mouseY, int mouseButton) {
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
        this.pendingWheel = 0;
        this.dragScrolling = false;
        this.pressActive = false;
        this.pressButton = -1;
        this.stopFling();
        AbstractWidget.clearPendingClick();
        super.removed();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double mx = RenderSystem.getMouseX();
        double my = RenderSystem.getMouseY();
        clickMoveTicks = 0;
        lastClick = System.currentTimeMillis();
boolean flinging = this.flingVelocity != 0;
        if (button == MouseUtil.LEGACY_LEFT) {
            lmbPressed = true;
            this.dragStartX = mx;
            this.dragStartY = my;
            this.dragScrollNotches = 0;
            this.dragScrolling = false;
            this.stopFling();
        }
        if (button == MouseUtil.LEGACY_RIGHT) rmbPressed = true;

        this.pressActive = true;
        this.pressMoved = flinging;
        this.pressButton = button;
        this.pressX = mx;
        this.pressY = my;
        this.lastDragX = mx;
        this.lastDragY = my;
        this.lastDragNanos = System.nanoTime();

        MouseUtil.setButtonDown(MouseUtil.fromLegacy(button), true);
        TextField.clearFocusOutside(mx, my);

        this.mousePressed(mx, my, button);

        if (clickOnPress()) {
            this.pressActive = false;
            this.pressButton = -1;
            this.onMouseClicked(mx, my, button);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        double mx = RenderSystem.getMouseX();
        double my = RenderSystem.getMouseY();
if (button == MouseUtil.LEGACY_LEFT) this.lmbPressed = false;
        if (button == MouseUtil.LEGACY_RIGHT) this.rmbPressed = false;
        MouseUtil.setButtonDown(MouseUtil.fromLegacy(button), false);

        if (this.pressActive && this.pressButton == button) {
            boolean click = !this.pressMoved && !this.dragScrolling;
            this.pressActive = false;
            this.pressButton = -1;
            if (click) {
                this.onMouseClicked(this.pressX, this.pressY, button);
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
