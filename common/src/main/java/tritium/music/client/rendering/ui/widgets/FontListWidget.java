package tritium.music.client.rendering.ui.widgets;

import net.minecraft.client.resources.language.I18n;
import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.StencilClipManager;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.font.FontOption;
import tritium.music.client.rendering.ui.AbstractWidget;
import tritium.music.client.util.MouseUtil;

import java.util.List;
import java.util.function.Consumer;

public class FontListWidget extends AbstractWidget<FontListWidget> {

    private static final double ROW_HEIGHT = 22;
    private static final double SCROLL_STEP = 46;
    private static final double SCROLLBAR_WIDTH = 6;
    private static final double SCROLLBAR_PADDING = 3;

    private List<FontOption> options = List.of();
    private String selectedId = "";

    private Consumer<FontOption> selectCallback;

    private double scrollOffset;
    private double targetScrollOffset;

    private FontOption hoveredOption;
    private boolean draggingScrollbar;
    private boolean scrollPending;
    private double scrollbarGrabOffset;

    public FontListWidget setOptions(List<FontOption> options) {
        this.options = options == null ? List.of() : options;
        clampScroll();
        return this;
    }

    public FontListWidget setSelectedId(String selectedId) {
        this.selectedId = selectedId == null ? "" : selectedId;
        return this;
    }

    public FontListWidget setOnSelect(Consumer<FontOption> selectCallback) {
        this.selectCallback = selectCallback;
        return this;
    }

    public FontOption hoveredOption() {
        return hoveredOption;
    }

    public void scrollToSelected() {
        this.scrollPending = true;
    }

    private void applyPendingScroll() {
        if (!scrollPending) {
            return;
        }
        double height = getHeight();
        if (height <= 0) {
            return;
        }
        scrollPending = false;

        for (int index = 0; index < options.size(); index++) {
            if (options.get(index).id().equals(selectedId)) {
                targetScrollOffset = Math.max(0, index * ROW_HEIGHT - height * 0.5 + ROW_HEIGHT * 0.5);
                clampScroll();
                scrollOffset = targetScrollOffset;
                return;
            }
        }
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        applyPendingScroll();
        updateScrollbarDrag(mouseY);

        this.scrollOffset = Interpolations.interpolate(this.scrollOffset, this.targetScrollOffset, 0.35f);
        clampScroll();

        double x = getX();
        double y = getY();
        double width = getWidth();
        double height = getHeight();
        double contentWidth = width - SCROLLBAR_WIDTH;

        roundedRect(x, y, width, height, 6, reAlpha(0xFF0B0C0E, getAlpha()));

        if (options.isEmpty()) {
            CFontRenderer font = FontManager.pf14;
            String empty = I18n.get("tritium-music.ui.settings.font.empty");
            font.drawCenteredString(empty, x + width * 0.5, y + (height - font.getStringHeight(empty)) * 0.5,
                    reAlpha(0xFF6F727A, getAlpha()));
            setHovered(null);
            return;
        }

        boolean insideList = isHovered(mouseX, mouseY, x, y, width, height);
        int first = (int) Math.max(0, Math.floor(scrollOffset / ROW_HEIGHT));
        int visible = (int) Math.ceil(height / ROW_HEIGHT) + 1;
        FontOption hovered = null;

        CFontRenderer nameFont = FontManager.pf14bold;
        CFontRenderer detailFont = FontManager.pf12;

        for (int index = first; index < Math.min(options.size(), first + visible); index++) {
            FontOption option = options.get(index);
            double rowY = y + index * ROW_HEIGHT - scrollOffset;
            if (rowY + ROW_HEIGHT < y || rowY > y + height) {
                continue;
            }

            boolean selected = option.id().equals(selectedId);
            boolean rowHovered = insideList && isHovered(mouseX, mouseY, x, rowY, contentWidth, ROW_HEIGHT);
            if (rowHovered) {
                hovered = option;
            }

            if (selected || rowHovered) {
                roundedRect(x + 2, rowY + 1, contentWidth - 4, ROW_HEIGHT - 2, 5,
                        reAlpha(selected ? 0xFF2C4A70 : 0xFF23252B, getAlpha()));
            }

            String label = nameFont.trim(option.displayName(), Math.max(24, contentWidth - 110));
            double textY = rowY + (ROW_HEIGHT - nameFont.getStringHeight(label)) * 0.5;
            nameFont.drawString(label, x + 10, textY,
                    reAlpha(selected ? 0xFFFFFFFF : 0xFFD3D5DA, getAlpha()));

            String badge = I18n.get(option.detailKey());
            double badgeWidth = detailFont.getStringWidthD(badge);
            detailFont.drawString(badge, x + contentWidth - badgeWidth - 10,
                    rowY + (ROW_HEIGHT - detailFont.getStringHeight(badge)) * 0.5,
                    reAlpha(0xFF7E828C, getAlpha()));
        }

        setHovered(hovered);
        renderScrollbar(x, y, width, height);
    }

    private void setHovered(FontOption option) {
        this.hoveredOption = option;
    }

    private void renderScrollbar(double x, double y, double width, double height) {
        double contentHeight = options.size() * ROW_HEIGHT;
        if (contentHeight <= height || height <= SCROLLBAR_PADDING * 2 + 4) {
            return;
        }

        double trackHeight = height - SCROLLBAR_PADDING * 2;
        double thumbHeight = thumbHeight(height, contentHeight, trackHeight);
        double maxScroll = maxScroll();
        double progress = maxScroll <= 0 ? 0 : scrollOffset / maxScroll;
        double thumbY = y + SCROLLBAR_PADDING + (trackHeight - thumbHeight) * progress;

        Rect.draw(x + width - 5, thumbY, 3, thumbHeight,
                reAlpha(draggingScrollbar ? 0xFFB8BBC2 : 0xFF6A6E78, getAlpha()));
    }

    private void updateScrollbarDrag(double mouseY) {
        if (!draggingScrollbar || !MouseUtil.isLeftDown()) {
            draggingScrollbar = false;
            return;
        }

        double contentHeight = options.size() * ROW_HEIGHT;
        double height = getHeight();
        if (contentHeight <= height) {
            return;
        }

        double trackHeight = height - SCROLLBAR_PADDING * 2;
        double thumbHeight = thumbHeight(height, contentHeight, trackHeight);
        double relativeY = mouseY - getY();
        double progress = (relativeY - scrollbarGrabOffset - SCROLLBAR_PADDING) / Math.max(1, trackHeight - thumbHeight);
        progress = Math.max(0, Math.min(1, progress));
        targetScrollOffset = progress * maxScroll();
        scrollOffset = targetScrollOffset;
    }

    private static double thumbHeight(double height, double contentHeight, double trackHeight) {
        return Math.max(20, trackHeight * height / contentHeight);
    }

    private double maxScroll() {
        return Math.max(0, options.size() * ROW_HEIGHT - getHeight());
    }

    private void clampScroll() {
        double max = maxScroll();
        targetScrollOffset = Math.max(0, Math.min(targetScrollOffset, max));
        scrollOffset = Math.max(0, Math.min(scrollOffset, max));
    }

    @Override
    public boolean onDWheel(double mouseX, double mouseY, int dWheel) {
        targetScrollOffset -= dWheel * SCROLL_STEP;
        clampScroll();
        return true;
    }

    @Override
    public boolean canBeScrolled() {
        return isHovering() && maxScroll() > 0;
    }

    @Override
    public boolean dragOnPress() {
        return true;
    }

    @Override
    public boolean onMousePressed(double relativeX, double relativeY, int mouseButton) {
        if (mouseButton != 0) {
            return false;
        }

        double contentWidth = getWidth() - SCROLLBAR_WIDTH;
        if (relativeX >= contentWidth && maxScroll() > 0) {
            double trackHeight = getHeight() - SCROLLBAR_PADDING * 2;
            double thumbHeight = thumbHeight(getHeight(), options.size() * ROW_HEIGHT, trackHeight);
            double progress = scrollOffset / maxScroll();
            double thumbY = SCROLLBAR_PADDING + (trackHeight - thumbHeight) * progress;
            draggingScrollbar = true;
            scrollbarGrabOffset = relativeY - thumbY;
            return true;
        }

        if (relativeX < 0 || relativeX > contentWidth || relativeY < 0 || relativeY > getHeight()) {
            return false;
        }

        int index = (int) ((relativeY + scrollOffset) / ROW_HEIGHT);
        if (index < 0 || index >= options.size()) {
            return false;
        }

        FontOption option = options.get(index);
        selectedId = option.id();
        if (selectCallback != null) {
            selectCallback.accept(option);
        }
        return true;
    }

    @Override
    public void renderWidget(double mouseX, double mouseY, int dWheel) {
        StencilClipManager.beginClip(getX(), getY(), getWidth(), getHeight());
        super.renderWidget(mouseX, mouseY, dWheel);
        StencilClipManager.endClip();
    }
}
