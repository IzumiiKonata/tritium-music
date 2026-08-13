package tritium.music.client.rendering.ui.widgets;

import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.ui.AbstractWidget;

import java.util.List;

public final class ContextMenuWidget extends AbstractWidget<ContextMenuWidget> {
    private static final double ITEM_HEIGHT = 22;
    private static final double PADDING = 4;
    private static final double MIN_WIDTH = 108;
    private static final int MAX_VISIBLE_ITEMS = 10;
    private final CFontRenderer font = FontManager.pf14bold;
    private volatile List<Item> items = List.of();
    private boolean open;
    private float menuAlpha;
    private int firstVisibleIndex;

    public record Item(String label, Runnable action, boolean selected, boolean enabled) {
        public Item(String label, Runnable action) {
            this(label, action, false, true);
        }
    }

    public ContextMenuWidget() {
        setShouldOverrideMouseCursor(true);
        setHidden(true);
    }

    public void open(double x, double y, List<Item> items) {
        this.items = List.copyOf(items);
        firstVisibleIndex = 0;
        setPosition(x, y);
        updateBounds();
        open = true;
        setHidden(false);
    }

    public void updateItems(List<Item> items) {
        if (!open) return;
        this.items = List.copyOf(items);
        clampScroll();
        updateBounds();
    }

    public void close() {
        open = false;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean handleClick(double mouseX, double mouseY, int mouseButton) {
        if (!open) return false;
        boolean inside = isHovered(mouseX, mouseY, getX(), getY(), getWidth(), getHeight());
        if (!inside && mouseButton == 1) {
            close();
            return false;
        }
        if (mouseButton == 0 && inside) {
            double itemOffset = mouseY - getY() - PADDING;
            int visibleIndex = (int) (itemOffset / ITEM_HEIGHT);
            int index = firstVisibleIndex + visibleIndex;
            if (itemOffset >= 0 && visibleIndex >= 0 && visibleIndex < visibleItemCount()
                    && index >= 0 && index < items.size()) {
                Item item = items.get(index);
                close();
                if (item.enabled() && item.action() != null) item.action().run();
                return true;
            }
        }
        close();
        return true;
    }

    public boolean handleWheel(double mouseX, double mouseY, int dWheel) {
        if (!open || dWheel == 0 || !isHovered(mouseX, mouseY, getX(), getY(), getWidth(), getHeight())) {
            return false;
        }
        firstVisibleIndex -= Integer.signum(dWheel);
        clampScroll();
        return true;
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        menuAlpha = Interpolations.interpolate(menuAlpha, open ? 1f : 0f, open ? .35f : .25f);
        if (!open && menuAlpha <= .01f) {
            menuAlpha = 0f;
            setHidden(true);
            return;
        }
        float renderAlpha = getAlpha() * menuAlpha;
        clampPosition();
        roundedRect(getX(), getY(), getWidth(), getHeight(), 4, reAlpha(0xFF202126, renderAlpha));
        int visibleItems = visibleItemCount();
        int endIndex = Math.min(items.size(), firstVisibleIndex + visibleItems);
        for (int index = firstVisibleIndex; index < endIndex; index++) {
            Item item = items.get(index);
            double itemY = getY() + PADDING + (index - firstVisibleIndex) * ITEM_HEIGHT;
            boolean hovered = item.enabled() && isHovered(mouseX, mouseY, getX() + PADDING, itemY, getWidth() - PADDING * 2, ITEM_HEIGHT);
            if (hovered || item.selected()) {
                Rect.draw(getX() + PADDING, itemY + 1, getWidth() - PADDING * 2, ITEM_HEIGHT - 2,
                        reAlpha(hovered ? 0xFF373940 : 0xFF2D2F35, renderAlpha));
            }
            String text = item.selected() ? "✓  " + item.label() : item.label();
            int color = item.enabled() ? 0xFFF1F2F4 : 0xFF777A82;
            double textY = itemY + (ITEM_HEIGHT - font.getStringHeight(text)) * .5;
            font.drawString(text, getX() + 10, textY, reAlpha(color, renderAlpha));
        }
        if (items.size() > visibleItems) {
            double trackX = getX() + getWidth() - 5;
            double trackY = getY() + PADDING + 2;
            double trackHeight = visibleItems * ITEM_HEIGHT - 4;
            double thumbHeight = Math.max(18, trackHeight * visibleItems / items.size());
            double progress = firstVisibleIndex / (double) (items.size() - visibleItems);
            Rect.draw(trackX, trackY, 2, trackHeight, reAlpha(0xFF34363C, renderAlpha));
            Rect.draw(trackX, trackY + (trackHeight - thumbHeight) * progress, 2, thumbHeight,
                    reAlpha(0xFF8A8D95, renderAlpha));
        }
    }

    private void updateBounds() {
        double width = MIN_WIDTH;
        for (Item item : items) {
            width = Math.max(width, font.getStringWidthD((item.selected() ? "✓  " : "") + item.label()) + 20);
        }
        setBounds(width + (items.size() > visibleItemCount() ? 6 : 0), visibleItemCount() * ITEM_HEIGHT + PADDING * 2);
    }

    private int visibleItemCount() {
        int available = Math.max(1, (int) ((getParentHeight() - PADDING * 2 - 4) / ITEM_HEIGHT));
        return Math.min(items.size(), Math.min(MAX_VISIBLE_ITEMS, available));
    }

    private void clampScroll() {
        firstVisibleIndex = Math.max(0, Math.min(firstVisibleIndex, Math.max(0, items.size() - visibleItemCount())));
    }

    private void clampPosition() {
        double x = Math.max(2, Math.min(getRelativeX(), getParentWidth() - getWidth() - 2));
        double y = Math.max(2, Math.min(getRelativeY(), getParentHeight() - getHeight() - 2));
        if (x != getRelativeX() || y != getRelativeY()) setPosition(x, y);
    }
}
