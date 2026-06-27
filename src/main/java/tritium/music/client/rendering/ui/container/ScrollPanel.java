package tritium.music.client.rendering.ui.container;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.StencilClipManager;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.ui.AbstractWidget;

import java.util.List;

public class ScrollPanel extends AbstractWidget<ScrollPanel> {

    @Getter
    private double spacing = 0;
    public double actualScrollOffset = 0, targetScrollOffset = 0;

    @Getter
    private Alignment alignment = Alignment.VERTICAL;

    public enum Alignment {
        VERTICAL,
        HORIZONTAL,
        VERTICAL_WITH_HORIZONTAL_FILL
    }

    public ScrollPanel setAlignment(Alignment alignment) {
        if (alignment == null)
            throw new IllegalArgumentException("Alignment cannot be null!");

        this.alignment = alignment;
        return this;
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        this.actualScrollOffset = Interpolations.interpolate(this.actualScrollOffset, this.targetScrollOffset, 1f);
        this.alignChildren();
    }

    @Override
    protected void renderDebugLayout() {
        super.renderDebugLayout();
        Rect.draw(this.getX(), this.getY(), this.getWidth(), this.getHeight(), reAlpha(0x00F50EE8, this.getAlpha() * .05f));
    }

    public ScrollPanel setSpacing(double spacing) {
        this.spacing = spacing;
        return this;
    }

    @Override
    public boolean onDWheel(double mouseX, double mouseY, int dWheel) {
        this.performScroll(dWheel);
        super.onDWheel(mouseX, mouseY, dWheel);
        return true;
    }

    private static boolean isShiftDown() {
        long handle = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private void performScroll(int dWheel) {
        if (dWheel != 0) {
            double strength = 12;

            if (isShiftDown())
                strength *= 2;

            if (dWheel > 0)
                this.targetScrollOffset -= strength;
            else
                this.targetScrollOffset += strength;
        }

        this.targetScrollOffset = Math.max(this.targetScrollOffset, 0);

        switch (this.alignment) {
            case VERTICAL -> {
                double childrenHeightSum = this.getChildrenHeightSum();
                if (childrenHeightSum > this.getHeight())
                    this.targetScrollOffset = Math.min(this.targetScrollOffset, childrenHeightSum - this.getHeight());
                else
                    this.targetScrollOffset = Math.min(this.targetScrollOffset, 0);
            }
            case HORIZONTAL -> {
                double childrenWidthSum = this.getChildrenWidthSum();
                if (childrenWidthSum > this.getWidth())
                    this.targetScrollOffset = Math.min(this.targetScrollOffset, childrenWidthSum - this.getWidth());
                else
                    this.targetScrollOffset = Math.min(this.targetScrollOffset, 0);
            }
            case VERTICAL_WITH_HORIZONTAL_FILL -> {
                double childrenHeightSum = this.getChildrenHeightSumHorizontalFill();
                if (childrenHeightSum > this.getHeight())
                    this.targetScrollOffset = Math.min(this.targetScrollOffset, childrenHeightSum - this.getHeight());
                else
                    this.targetScrollOffset = Math.min(this.targetScrollOffset, 0);
            }
        }
    }

    public void scrollToEnd() {
        switch (this.alignment) {
            case VERTICAL -> {
                double childrenHeightSum = this.getChildrenHeightSum();
                this.targetScrollOffset = childrenHeightSum > this.getHeight() ? childrenHeightSum - this.getHeight() : 0;
            }
            case HORIZONTAL -> {
                double childrenWidthSum = this.getChildrenWidthSum();
                this.targetScrollOffset = childrenWidthSum > this.getWidth() ? childrenWidthSum - this.getWidth() : 0;
            }
            case VERTICAL_WITH_HORIZONTAL_FILL -> {
                double childrenHeightSum = this.getChildrenHeightSumHorizontalFill();
                this.targetScrollOffset = childrenHeightSum > this.getHeight() ? childrenHeightSum - this.getHeight() : 0;
            }
        }
    }

    public void scrollToEndImmediately() {
        switch (this.alignment) {
            case VERTICAL -> {
                double childrenHeightSum = this.getChildrenHeightSum();
                this.actualScrollOffset = childrenHeightSum > this.getHeight() ? childrenHeightSum - this.getHeight() : 0;
            }
            case HORIZONTAL -> {
                double childrenWidthSum = this.getChildrenWidthSum();
                this.actualScrollOffset = childrenWidthSum > this.getWidth() ? childrenWidthSum - this.getWidth() : 0;
            }
            case VERTICAL_WITH_HORIZONTAL_FILL -> {
                double childrenHeightSum = this.getChildrenHeightSumHorizontalFill();
                this.actualScrollOffset = childrenHeightSum > this.getHeight() ? childrenHeightSum - this.getHeight() : 0;
            }
        }
    }

    public boolean isScrolledToEnd() {
        return switch (this.alignment) {
            case VERTICAL -> {
                double childrenHeightSum = this.getChildrenHeightSum();
                yield childrenHeightSum > this.getHeight() ? this.targetScrollOffset >= childrenHeightSum - this.getHeight() : this.targetScrollOffset == 0;
            }
            case HORIZONTAL -> {
                double childrenWidthSum = this.getChildrenWidthSum();
                yield childrenWidthSum > this.getWidth() ? this.targetScrollOffset >= childrenWidthSum - this.getWidth() : this.targetScrollOffset == 0;
            }
            case VERTICAL_WITH_HORIZONTAL_FILL -> {
                double childrenHeightSum = this.getChildrenHeightSumHorizontalFill();
                yield childrenHeightSum > this.getHeight() ? this.targetScrollOffset >= childrenHeightSum - this.getHeight() : this.targetScrollOffset == 0;
            }
        };
    }

    public void alignChildren() {
        double offsetX = 0;
        double offsetY = 0;

        if (this.alignment == Alignment.VERTICAL || this.alignment == Alignment.VERTICAL_WITH_HORIZONTAL_FILL)
            offsetY = -this.actualScrollOffset;
        else
            offsetX = -this.actualScrollOffset;

        switch (this.alignment) {
            case VERTICAL -> {
                double childrenHeightSum = this.getChildrenHeightSum();
                if (childrenHeightSum < this.getHeight())
                    this.targetScrollOffset = 0;
                else if (this.targetScrollOffset > childrenHeightSum - this.getHeight()) {
                    this.targetScrollOffset = childrenHeightSum - this.getHeight();
                }
            }
            case HORIZONTAL -> {
                double childrenWidthSum = this.getChildrenWidthSum();
                if (childrenWidthSum < this.getWidth())
                    this.targetScrollOffset = 0;
                else if (this.targetScrollOffset > childrenWidthSum - this.getWidth()) {
                    this.targetScrollOffset = childrenWidthSum - this.getWidth();
                }
            }
            case VERTICAL_WITH_HORIZONTAL_FILL -> {
                double childrenHeightSum = this.getChildrenHeightSumHorizontalFill();
                if (childrenHeightSum < this.getHeight())
                    this.targetScrollOffset = 0;
                else if (this.targetScrollOffset > childrenHeightSum - this.getHeight()) {
                    this.targetScrollOffset = childrenHeightSum - this.getHeight();
                }
            }
        }

        for (AbstractWidget<?> child : this.getChildren()) {
            double width = child.getWidth();
            double height = child.getHeight();

            if (child.isHidden())
                continue;

            switch (this.alignment) {
                case VERTICAL -> {
                    child.setPosition(child.getRelativeX(), offsetY);
                    offsetY += height + spacing;
                }
                case HORIZONTAL -> {
                    child.setPosition(offsetX, child.getRelativeY());
                    offsetX += width + spacing;
                }
                case VERTICAL_WITH_HORIZONTAL_FILL -> {
                    if (offsetX + width > this.getWidth()) {
                        offsetX = 0;
                        offsetY += height + spacing;
                    }

                    child.setPosition(offsetX, offsetY);
                    offsetX += width + spacing;
                }
            }
        }
    }

    protected double getChildrenHeightSum() {
        double result = 0;

        for (AbstractWidget<?> child : this.getChildren()) {
            if (child.isHidden())
                continue;

            result += child.getHeight() + this.spacing;
        }

        if (result > 0)
            result -= this.spacing;

        return result;
    }

    protected double getChildrenWidthSum() {
        double result = 0;

        for (AbstractWidget<?> child : this.getChildren()) {
            if (child.isHidden())
                continue;

            result += child.getWidth() + this.spacing;
        }

        if (result > 0)
            result -= this.spacing;

        return result;
    }

    protected double getChildrenHeightSumHorizontalFill() {
        double result = 0;
        double offsetX = 0;

        for (AbstractWidget<?> child : this.getChildren()) {
            double width = child.getWidth();
            double height = child.getHeight();

            if (child.isHidden())
                continue;

            if (offsetX == 0 && result == 0) {
                result += height + spacing;
            }

            if (offsetX + width > this.getWidth()) {
                offsetX = 0;
                result += height + spacing;
            }

            offsetX += width + spacing;
        }

        if (result > 0)
            result -= this.spacing;

        return result;
    }

    @Override
    public void renderWidget(double mouseX, double mouseY, int dWheel) {
        StencilClipManager.beginClip(() -> Rect.draw(this.getX(), this.getY(), this.getWidth(), this.getHeight(), -1));

        super.renderWidget(mouseX, mouseY, dWheel);

        StencilClipManager.endClip();
    }

    @Override
    protected boolean shouldRenderChildren(AbstractWidget<?> child, double mouseX, double mouseY) {
        switch (this.getAlignment()) {
            case VERTICAL, VERTICAL_WITH_HORIZONTAL_FILL -> {
                if (child.getRelativeY() + child.getHeight() < 0)
                    return false;

                if (child.getRelativeY() > this.getHeight())
                    return false;
            }
            case HORIZONTAL -> {
                if (child.getRelativeX() + child.getWidth() < 0)
                    return false;

                if (child.getRelativeX() > this.getWidth())
                    return false;
            }
        }

        return true;
    }

    @Override
    protected boolean shouldClickChildren(double mouseX, double mouseY) {
        return this.testHovered(mouseX, mouseY);
    }

    @Override
    public boolean canBeScrolled() {
        return true;
    }
}
