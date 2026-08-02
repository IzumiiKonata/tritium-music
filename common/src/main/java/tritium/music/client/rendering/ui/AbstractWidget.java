package tritium.music.client.rendering.ui;

import lombok.Getter;
import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.RGBA;
import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.SharedRenderingConstants;
import tritium.music.client.util.ClientSettings;
import tritium.music.client.util.CursorUtils;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressWarnings("unchecked")
public abstract class AbstractWidget<SELF extends AbstractWidget<SELF>> implements SharedRenderingConstants {

    public interface OnClickCallback {
        boolean onClick(double relativeX, double relativeY, int mouseButton);
    }

    public interface OnKeyTypedCallback {
        boolean onKeyTyped(char character, int keyCode);
    }

    public interface OnDWheelCallback {
        boolean onDWheel(double mouseX, double mouseY, int dWheel);
    }

    public interface RenderCallback {
        void onRender();
    }

    @Getter
    AbstractWidget<?> parent = null;

    @Getter
    List<AbstractWidget<?>> children = new CopyOnWriteArrayList<>();

    public static class Bounds {
        private double x, y;
        private double width, height;
    }

    @Getter
    private final Bounds bounds = new Bounds();

    private static volatile int LAYOUT_STAMP = 0;

    private int posStamp = -1;
    private int alphaStamp = -1;
    private double cachedX, cachedY;
    private float cachedAlpha;

    private static void invalidateLayout() {
        LAYOUT_STAMP++;
    }

    private RenderCallback beforeRenderCallback = () -> {
    };

    @Getter
    private boolean clickable = true;

    @Getter
    private boolean hovering = false;

    @Getter
    private boolean hidden = false;
    @Getter
    private boolean shouldOverrideMouseCursor = false;
    @Getter
    private boolean bloom = false;
    @Getter
    private boolean blur = false;

    private Color color = Color.BLACK;

    protected OnClickCallback clickCallback = null;
    private OnKeyTypedCallback keyTypedCallback = null;
    private OnDWheelCallback dWheelCallback = null;

    private Runnable transformations = null, onTick = null;

    private float alpha = 1.0f;

    public abstract void onRender(double mouseX, double mouseY);

    protected boolean shouldRenderChildren(AbstractWidget<?> child, double mouseX, double mouseY) {
        return true;
    }

    public void renderWidget(double mouseX, double mouseY, int dWheel) {

        if (this.parent == null)
            invalidateLayout();

        if (this.isHidden())
            return;

        boolean shouldResetMatrixState = this.transformations != null;

        if (shouldResetMatrixState) {
            RenderContext.graphics().pose().pushMatrix();
            this.transformations.run();
        }

        this.beforeRenderCallback.onRender();

        this.onRender(mouseX, mouseY);

        boolean debug = ClientSettings.SHOW_WIDGET_BOUNDARY.getValue();

        boolean childHovering = false;

        for (AbstractWidget<?> child : this.getChildren()) {

            if (child.isHidden())
                continue;

            if (!this.shouldRenderChildren(child, mouseX, mouseY))
                continue;

            child.renderWidget(mouseX, mouseY, dWheel);

            if (debug) {
                child.renderDebugLayout();
            }

            if (!childHovering && child.isClickable() && child.testHovered(mouseX, mouseY)) {
                childHovering = true;
            }
        }

        if (debug) {
            this.renderDebugLayout();
        }

        if (shouldResetMatrixState)
            RenderContext.graphics().pose().popMatrix();

        this.hovering = !childHovering && this.testHovered(mouseX, mouseY);

        if (this.hovering && this.isShouldOverrideMouseCursor()) {
            CursorUtils.setOverride(this.getHoveringCursorType());
        }

        if (dWheel != 0)
            this.onDWheelReceived(mouseX, mouseY, dWheel);
    }

    public SELF setShouldOverrideMouseCursor(boolean shouldOverrideMouseCursor) {
        this.shouldOverrideMouseCursor = shouldOverrideMouseCursor;
        return (SELF) this;
    }

    public long getHoveringCursorType() {
        return CursorUtils.HAND;
    }

    public SELF setOnTick(Runnable onTick) {
        this.onTick = onTick;
        return (SELF) this;
    }

    public void onTick() {
    }

    public void onTickReceived() {
        if (this.parent == null)
            invalidateLayout();

        if (this.onTick != null) {
            this.onTick.run();
        }

        this.onTick();

        this.getChildren().forEach(AbstractWidget::onTickReceived);
    }

    public void addChild(AbstractWidget<?>... children) {
        this.children.addAll(Arrays.asList(children));
        for (AbstractWidget<?> child : children) {
            child.setParent(this);
        }
    }

    public void addChild(List<AbstractWidget<?>> children) {
        this.children.addAll(children);
        children.forEach(child -> child.setParent(this));
    }

    protected void renderDebugLayout() {
        RenderSystem.drawOutLine(this.getX(), this.getY(), this.getWidth(), this.getHeight(), 0.5, reAlpha(0x00FF0000, this.getAlpha()));

        double lineLength = Math.min(8, Math.min(this.getWidth() * .25, this.getHeight() * .25));
        double lineSize = 1;
        int lineColor = reAlpha(0x000090FF, this.getAlpha());
        Rect.draw(this.getX(), this.getY(), lineLength, lineSize, lineColor);
        Rect.draw(this.getX(), this.getY(), lineSize, lineLength, lineColor);

        Rect.draw(this.getX() + this.getWidth() - lineLength, this.getY(), lineLength, lineSize, lineColor);
        Rect.draw(this.getX() + this.getWidth() - lineSize, this.getY(), lineSize, lineLength, lineColor);

        Rect.draw(this.getX(), this.getY() + this.getHeight() - lineLength, lineSize, lineLength, lineColor);
        Rect.draw(this.getX(), this.getY() + this.getHeight() - lineSize, lineLength, lineSize, lineColor);

        Rect.draw(this.getX() + this.getWidth() - lineLength, this.getY() + this.getHeight() - lineSize, lineLength, lineSize, lineColor);
        Rect.draw(this.getX() + this.getWidth() - lineSize, this.getY() + this.getHeight() - lineLength, lineSize, lineLength, lineColor);
    }

    public SELF setMargin(double margin) {
        return this.setMargin(margin, margin, margin, margin);
    }

    public SELF setMargin(double left, double top, double right, double bottom) {
        this.getBounds().x = left;
        this.getBounds().y = top;
        this.getBounds().width = this.getParentWidth() - left - right;
        this.getBounds().height = this.getParentHeight() - top - bottom;
        invalidateLayout();
        return (SELF) this;
    }

    public SELF expand(double expand) {
        this.getBounds().x -= expand;
        this.getBounds().y -= expand;
        this.getBounds().width += expand * 2;
        this.getBounds().height += expand * 2;
        invalidateLayout();
        return (SELF) this;
    }

    protected boolean testHovered(double mouseX, double mouseY) {
        return this.isHovered(mouseX, mouseY, this.getX(), this.getY(), this.getWidth(), this.getHeight());
    }

    protected boolean testHovered(double mouseX, double mouseY, double expand) {
        return this.isHovered(mouseX, mouseY, this.getX() - expand, this.getY() - expand, this.getWidth() + expand * 2, this.getHeight() + expand * 2);
    }

    public boolean isHovered(double mouseX, double mouseY, double x, double y, double width, double height) {
        if (width < 0) {
            width = -width;
            x -= width;
        }

        if (height < 0) {
            height = -height;
            y -= height;
        }

        return mouseX >= x && mouseY >= y && mouseX <= x + width && mouseY <= y + height;
    }

    protected boolean iterateChildrenMouseClick(List<AbstractWidget<?>> children, double mouseX, double mouseY, int mouseButton) {
        for (AbstractWidget<?> child : children) {
            if (child.isHidden()) {
                continue;
            }

            if (!child.shouldClickChildren(mouseX, mouseY))
                continue;

            if (!child.getChildren().isEmpty()) {
                if (this.iterateChildrenMouseClick(child.getChildren(), mouseX, mouseY, mouseButton)) {
                    return true;
                }
            }

            if (child.isHovering() && child.isClickable() && child.onMouseClicked(mouseX - child.getX(), mouseY - child.getY(), mouseButton)) {
                return true;
            }
        }

        return false;
    }

    protected boolean iterateChildrenDWheel(List<AbstractWidget<?>> children, double mouseX, double mouseY, int dWheel) {
        for (AbstractWidget<?> child : children) {
            if (child.isHidden()) {
                continue;
            }

            if (!child.shouldClickChildren(mouseX, mouseY))
                continue;

            if (!child.getChildren().isEmpty()) {
                if (this.iterateChildrenDWheel(child.getChildren(), mouseX, mouseY, dWheel)) {
                    return true;
                }
            }

            if ((child.isHovering() || child.canBeScrolled()) && child.isClickable() && child.onDWheel(mouseX - child.getX(), mouseY - child.getY(), dWheel)) {
                return true;
            }
        }

        return false;
    }

    public boolean canBeScrolled() {
        return false;
    }

    protected boolean shouldClickChildren(double mouseX, double mouseY) {
        return true;
    }

    public void onMouseClickReceived(double mouseX, double mouseY, int mouseButton) {
        if (!this.shouldClickChildren(mouseX, mouseY))
            return;

        if (!this.iterateChildrenMouseClick(this.getChildren(), mouseX, mouseY, mouseButton)) {
            if (!this.isHidden() && this.isHovering()) {
                this.onMouseClicked(mouseX - this.getX(), mouseY - this.getY(), mouseButton);
            }
        }
    }

    public void onDWheelReceived(double mouseX, double mouseY, int dWheel) {
        if (!this.shouldClickChildren(mouseX, mouseY))
            return;

        if (!this.iterateChildrenDWheel(this.getChildren(), mouseX, mouseY, dWheel)) {
            if (!this.isHidden() && (this.isHovering() || this.canBeScrolled())) {
                this.onDWheel(mouseX - this.getX(), mouseY - this.getY(), dWheel);
            }
        }
    }

    public boolean onMouseClicked(double relativeX, double relativeY, int mouseButton) {
        return this.clickCallback != null && this.isClickable() && this.clickCallback.onClick(relativeX, relativeY, mouseButton);
    }

    protected boolean iterateChildrenKeyType(List<AbstractWidget<?>> children, char typedChar, int keyCode) {
        for (AbstractWidget<?> child : children) {
            if (child.isHidden()) {
                continue;
            }

            if (!child.getChildren().isEmpty()) {
                if (this.iterateChildrenKeyType(child.getChildren(), typedChar, keyCode)) {
                    return true;
                }
            }

            if (child.onKeyTyped(typedChar, keyCode))
                return true;
        }

        return false;
    }

    public boolean onKeyTypedReceived(char typedChar, int keyCode) {
        if (this.isHidden())
            return false;

        boolean responded = this.iterateChildrenKeyType(this.getChildren(), typedChar, keyCode);
        if (!responded) {
            if (!this.isHidden()) {
                return this.onKeyTyped(typedChar, keyCode);
            }

            return false;
        } else {
            return true;
        }
    }

    public boolean onKeyTyped(char character, int keyCode) {
        return this.keyTypedCallback != null && this.keyTypedCallback.onKeyTyped(character, keyCode);
    }

    public boolean onDWheel(double mouseX, double mouseY, int dWheel) {
        return this.dWheelCallback != null && this.dWheelCallback.onDWheel(mouseX, mouseY, dWheel);
    }

    public SELF setOnDWheelCallback(OnDWheelCallback callback) {
        this.dWheelCallback = callback;
        return (SELF) this;
    }

    public SELF setBloom(boolean bloom) {
        this.bloom = bloom;
        return (SELF) this;
    }

    public SELF setBlur(boolean blur) {
        this.blur = blur;
        return (SELF) this;
    }

    public SELF setBeforeRenderCallback(RenderCallback beforeRenderCallback) {
        this.beforeRenderCallback = beforeRenderCallback;
        return (SELF) this;
    }

    public Color getColor() {
        return new Color(this.color.getRed(), this.color.getGreen(), this.color.getBlue(), (int) (this.getAlpha() * 255));
    }

    public SELF setColor(int color) {
        this.color = new Color(color);
        return (SELF) this;
    }

    public SELF setColor(Color color) {
        this.color = color;
        return (SELF) this;
    }

    public int getHexColor() {
        int red = this.color.getRed();
        int green = this.color.getGreen();
        int blue = this.color.getBlue();
        return RGBA.color(red, green, blue, (int) (this.getAlpha() * 255));
    }

    public SELF setTransformations(Runnable transformations) {
        this.transformations = transformations;
        return (SELF) this;
    }

    public double getParentX() {
        if (this.getParent() != null)
            return this.getParent().getX();
        return 0;
    }

    public double getParentY() {
        if (this.getParent() != null)
            return this.getParent().getY();
        return 0;
    }

    public double getParentWidth() {
        if (this.getParent() != null)
            return this.getParent().getWidth();
        return RenderSystem.getWidth();
    }

    public double getParentHeight() {
        if (this.getParent() != null)
            return this.getParent().getHeight();
        return RenderSystem.getHeight();
    }

    public SELF setHidden(boolean hidden) {
        this.hidden = hidden;
        return (SELF) this;
    }

    public float getAlpha() {
        if (this.parent == null)
            return this.alpha;

        if (this.alphaStamp == LAYOUT_STAMP)
            return this.cachedAlpha;

        this.cachedAlpha = this.parent.getAlpha() * this.alpha;
        this.alphaStamp = LAYOUT_STAMP;
        return this.cachedAlpha;
    }

    public float getWidgetAlpha() {
        return this.alpha;
    }

    public SELF setAlpha(float alpha) {
        this.alpha = alpha;
        this.color = new Color(this.color.getRed(), this.color.getGreen(), this.color.getBlue(), (int) (alpha * 255));
        invalidateLayout();
        return (SELF) this;
    }

    public SELF setOnClickCallback(OnClickCallback callback) {
        this.clickCallback = callback;
        return (SELF) this;
    }

    public SELF setOnKeyTypedCallback(OnKeyTypedCallback callback) {
        this.keyTypedCallback = callback;
        return (SELF) this;
    }

    public double getX() {
        if (this.parent == null)
            return this.getBounds().x;

        if (this.posStamp == LAYOUT_STAMP)
            return this.cachedX;

        this.cachedX = this.parent.getX() + this.getBounds().x;
        this.cachedY = this.parent.getY() + this.getBounds().y;
        this.posStamp = LAYOUT_STAMP;
        return this.cachedX;
    }

    public double getY() {
        if (this.parent == null)
            return this.getBounds().y;

        if (this.posStamp == LAYOUT_STAMP)
            return this.cachedY;

        this.cachedX = this.parent.getX() + this.getBounds().x;
        this.cachedY = this.parent.getY() + this.getBounds().y;
        this.posStamp = LAYOUT_STAMP;
        return this.cachedY;
    }

    public double getRelativeX() {
        return this.getBounds().x;
    }

    public double getRelativeY() {
        return this.getBounds().y;
    }

    public double getWidth() {
        return this.getBounds().width;
    }

    public double getHeight() {
        return this.getBounds().height;
    }

    public SELF setWidth(double width) {
        this.getBounds().width = width;
        return (SELF) this;
    }

    public SELF setHeight(double height) {
        this.getBounds().height = height;
        return (SELF) this;
    }

    public SELF setBounds(double x, double y, double width, double height) {
        this.getBounds().x = x;
        this.getBounds().y = y;
        this.getBounds().width = width;
        this.getBounds().height = height;
        invalidateLayout();
        return (SELF) this;
    }

    public SELF center() {
        return this.setPosition(this.getParentWidth() * .5 - this.getWidth() * .5, this.getParentHeight() * .5 - this.getHeight() * .5);
    }

    public SELF centerHorizontally() {
        return this.setPosition(this.getParentWidth() * .5 - this.getWidth() * .5, this.getRelativeY());
    }

    public SELF centerVertically() {
        return this.setPosition(this.getRelativeX(), this.getParentHeight() * .5 - this.getHeight() * .5);
    }

    public SELF setPosition(double x, double y) {
        this.getBounds().x = x;
        this.getBounds().y = y;
        invalidateLayout();
        return (SELF) this;
    }

    public SELF setPositionAbsolute(double x, double y) {
        invalidateLayout();
        this.getBounds().x = this.getBounds().y = 0;
        this.getBounds().x = x - this.getX();
        this.getBounds().y = y - this.getY();
        invalidateLayout();
        return (SELF) this;
    }

    public SELF setBounds(double size) {
        return this.setBounds(size, size);
    }

    public SELF setBounds(double width, double height) {
        this.getBounds().width = width;
        this.getBounds().height = height;
        return (SELF) this;
    }

    public SELF setClickable(boolean clickable) {
        this.clickable = clickable;
        return (SELF) this;
    }

    public SELF setParent(AbstractWidget<?> parent) {
        this.parent = parent;
        invalidateLayout();
        return (SELF) this;
    }
}
