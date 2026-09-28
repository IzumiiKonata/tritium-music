package tritium.music.client.screens.ncm.panels;

import net.minecraft.client.resources.language.I18n;
import tritium.music.client.config.FontConfig;
import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.client.rendering.font.FontCatalog;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.font.FontOption;
import tritium.music.client.rendering.font.FontPreview;
import tritium.music.client.rendering.ui.widgets.FontListWidget;
import tritium.music.client.rendering.ui.widgets.RoundedButtonWidget;
import tritium.music.client.rendering.ui.widgets.TextFieldWidget;
import tritium.music.client.screens.ncm.NCMPanel;
import tritium.music.client.screens.ncm.NCMScreen;
import tritium.music.platform.Platform;

import java.awt.Color;
import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FontSettingsPage extends NCMPanel {

    private static final double SLOT_ROW_HEIGHT = 26;
    private static final double STYLE_ROW_HEIGHT = 22;
    private static final double STYLE_ROW_Y = 32;
    private static final double STYLE_BUTTON_WIDTH = 76;
    private static final double TOGGLE_ROW_Y = 58;
    private static final double TOGGLE_HEIGHT = 22;
    private static final double TOGGLE_WIDTH = 168;
    private static final double GAP = 6;
    private static final double TOP = 90;
    private static final double SEARCH_HEIGHT = 26;
    private static final double SEARCH_TEXT_PADDING = 8;
    private static final double ACTION_HEIGHT = 26;
    private static final double PREVIEW_HEIGHT = 140;
    private static final double STACKED_PREVIEW_HEIGHT = 104;
    private static final double MIN_PREVIEW_HEIGHT = 60;
    private static final double HOVER_SETTLE_FRAMES = 5;

    private static final int COLOR_SLOT_ACTIVE = 0xFFC30218;
    private static final int COLOR_SLOT_ACTIVE_LOCKED = 0xFF5E2531;
    private static final int COLOR_FIELD = 0xFF1A1C20;
    private static final int COLOR_CARD_DIVIDER = 0xFF2C2F35;

    private static final String[] STYLE_KEYS = {
            FontConfig.STYLE_PLAIN, FontConfig.STYLE_BOLD, FontConfig.STYLE_ITALIC, FontConfig.STYLE_BOLD_ITALIC
    };

    private final List<RoundedButtonWidget> slotButtons = new ArrayList<>();
    private final List<RoundedButtonWidget> styleButtons = new ArrayList<>();

    private FontConfig pending;
    private FontConfig.SlotType selectedSlot = FontConfig.SlotType.MAIN;
    private String search = "";

    private FontPreview preview;
    private FontOption lastHovered;
    private int hoverStableFrames;
    private boolean systemFontsApplied;
    private String status = "";
    private int statusColorValue = 0xFF8A8D94;

    private TextFieldWidget searchField;
    private RoundedButtonWidget rescanButton;
    private RoundedButtonWidget applyButton;
    private RoundedButtonWidget resetButton;
    private RoundedButtonWidget folderButton;
    private RoundedButtonWidget shapingButton;
    private FontListWidget list;

    private double searchX, searchY, searchWidth;
    private double previewX, previewY, previewWidth, previewHeight;
    private double styleRowRight;
    private double toggleRowRight;
    private double bodyTop = TOP;
    private boolean previewVisible;
    private boolean previewDetailed;

    private double insetLeft = 24;
    private double insetTop = 94;
    private double insetRight = 24;
    private double insetBottom = 130;

    public FontSettingsPage setContentInsets(double left, double top, double right, double bottom) {
        this.insetLeft = left;
        this.insetTop = top;
        this.insetRight = right;
        this.insetBottom = bottom;
        return this;
    }

    @Override
    public void onInit() {
        if (pending == null) {
            pending = FontConfig.get().copy();
            pending.normalize();
            selectedSlot = FontConfig.SlotType.MAIN;
        }

        getChildren().clear();
        slotButtons.clear();
        styleButtons.clear();

        setBeforeRenderCallback(this::onBeforeRender);

        buildSlotButtons();
        buildStyleButtons();

        shapingButton = lockedToggle("tritium-music.ui.settings.font.shaping");
        addChild(shapingButton);

        searchField = new TextFieldWidget(FontManager.pf14);
        searchField.setColor(-1);
        searchField.setPlaceholder(I18n.get("tritium-music.ui.settings.font.search"));
        searchField.drawUnderline(false);
        searchField.setText(search);
        searchField.setTextChangedCallback(text -> {
            search = text == null ? "" : text;
            refreshList(false);
        });
        addChild(searchField);

        rescanButton = button("tritium-music.ui.settings.font.rescan", FontManager.pf14bold, this::rescan);
        addChild(rescanButton);

        list = new FontListWidget();
        list.setOnSelect(this::selectFont);
        addChild(list);

        applyButton = button("tritium-music.ui.settings.font.apply", FontManager.pf14bold, this::apply);
        resetButton = button("tritium-music.ui.settings.font.reset", FontManager.pf14bold, this::resetToDefault);
        folderButton = button("tritium-music.ui.settings.font.open_folder", FontManager.pf14bold, this::openFontFolder);
        addChild(applyButton, resetButton, folderButton);

        refreshList(true);
    }

    private void onBeforeRender() {
        setBounds(
                insetLeft,
                insetTop,
                Math.max(0, getParentWidth() - insetLeft - insetRight),
                Math.max(0, getParentHeight() - insetTop - insetBottom));
        layoutChildren();
    }

    private void buildSlotButtons() {
        for (FontConfig.SlotType type : FontConfig.SlotType.values()) {
            RoundedButtonWidget button = new RoundedButtonWidget(slotLabel(type), FontManager.pf14bold);
            button.setRadius(5);
            button.setTextColor(getColor(NCMScreen.ColorType.PRIMARY_TEXT));
            button.setBeforeRenderCallback(() -> button.setColor(selectedSlot == type
                    ? COLOR_SLOT_ACTIVE
                    : getColor(NCMScreen.ColorType.ELEMENT_HOVER)));
            button.setOnClickCallback((x, y, mouseButton) -> {
                if (mouseButton != 0 || selectedSlot == type) {
                    return false;
                }
                selectedSlot = type;
                refreshList(true);
                return true;
            });
            slotButtons.add(button);
            addChild(button);
        }
    }

    private void buildStyleButtons() {
        for (String style : STYLE_KEYS) {
            RoundedButtonWidget button = new RoundedButtonWidget(styleLabel(style), FontManager.pf14bold);
            button.setRadius(5);
            button.setTextColor(getColor(NCMScreen.ColorType.PRIMARY_TEXT));
            button.setBeforeRenderCallback(() -> {
                FontConfig.Slot slot = pending.slot(selectedSlot);
                button.setColor(style.equals(slot.style)
                        ? COLOR_SLOT_ACTIVE
                        : getColor(NCMScreen.ColorType.ELEMENT_HOVER));
            });
            button.setOnClickCallback((x, y, mouseButton) -> {
                if (mouseButton != 0) {
                    return false;
                }
                FontConfig.Slot slot = pending.slot(selectedSlot).copy();
                if (style.equals(slot.style)) {
                    return false;
                }
                slot.style = style;
                pending.set(selectedSlot, slot);
                setStatus("");
                return true;
            });
            styleButtons.add(button);
            addChild(button);
        }
    }

    private RoundedButtonWidget lockedToggle(String key) {
        RoundedButtonWidget button = new RoundedButtonWidget("✔ " + I18n.get(key), FontManager.pf14bold);
        button.setRadius(5);
        button.setClickable(false);
        button.setShouldOverrideMouseCursor(false);
        button.setBeforeRenderCallback(() -> button.setColor(
                FontManager.isShapingActive() ? COLOR_SLOT_ACTIVE_LOCKED : 0xFF6B5320));
        return button;
    }

    private RoundedButtonWidget button(String key, CFontRenderer font, Runnable action) {
        RoundedButtonWidget widget = new RoundedButtonWidget(I18n.get(key), font);
        widget.setRadius(5);
        widget.setColor(getColor(NCMScreen.ColorType.ELEMENT_HOVER));
        widget.setTextColor(getColor(NCMScreen.ColorType.PRIMARY_TEXT));
        widget.setOnClickCallback((x, y, mouseButton) -> {
            if (mouseButton != 0) {
                return false;
            }
            action.run();
            return true;
        });
        return widget;
    }

    private void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        if (width <= 0 || height <= 0) {
            previewVisible = false;
            return;
        }

        double slotWidth = Math.max(24, (width - GAP * 3) / 4.0);
        for (int index = 0; index < slotButtons.size(); index++) {
            slotButtons.get(index).setBounds(index * (slotWidth + GAP), 0, slotWidth, SLOT_ROW_HEIGHT);
        }

        double styleWidth = Math.min(STYLE_BUTTON_WIDTH, Math.max(24, (width - GAP * 3) / 4.0));
        double styleX = 0;
        for (RoundedButtonWidget button : styleButtons) {
            button.setBounds(styleX, STYLE_ROW_Y, styleWidth, STYLE_ROW_HEIGHT);
            styleX += styleWidth + GAP;
        }
        styleRowRight = styleX - GAP;

        double toggleWidth = Math.min(TOGGLE_WIDTH, Math.max(56, (width - GAP * 2) / 3.0));
        double mergedToggleX = styleRowRight + GAP * 2;
        boolean mergeToggleRow = mergedToggleX + toggleWidth <= width;

        double toggleX = mergeToggleRow ? mergedToggleX : 0;
        double toggleY = mergeToggleRow ? STYLE_ROW_Y : TOGGLE_ROW_Y;
        if (shapingButton != null) {
            shapingButton.setBounds(toggleX, toggleY, toggleWidth, TOGGLE_HEIGHT);
        }
        toggleRowRight = toggleX + toggleWidth;

        double headerBottom = mergeToggleRow
                ? STYLE_ROW_Y + STYLE_ROW_HEIGHT
                : TOGGLE_ROW_Y + TOGGLE_HEIGHT;
        bodyTop = headerBottom + 10;

        double actionY = Math.max(bodyTop, height - ACTION_HEIGHT);

        double contentBottom = actionY - GAP;
        double listTop = bodyTop + SEARCH_HEIGHT + GAP;

        double leftWidth = width * 0.54;
        double rightX = leftWidth + 12;
        double rightWidth = Math.max(0, width - rightX);

        double bodyHeight = contentBottom - bodyTop;
        boolean stacked = rightWidth < 160 || bodyHeight < 130;

        if (!stacked) {
            searchX = 0;
            searchY = bodyTop;
            searchWidth = leftWidth;

            list.setBounds(0, listTop, leftWidth, Math.max(0, contentBottom - listTop));

            previewX = rightX;
            previewY = bodyTop;
            previewWidth = rightWidth;
            previewHeight = Math.max(0, bodyHeight);
        } else {
            searchX = 0;
            searchY = bodyTop;
            searchWidth = width;

            double available = Math.max(0, contentBottom - listTop);
            double stackedPreview = Math.min(STACKED_PREVIEW_HEIGHT, available - 80);
            if (stackedPreview < MIN_PREVIEW_HEIGHT) {
                stackedPreview = 0;
            }

            previewX = 0;
            previewY = listTop;
            previewWidth = width;
            previewHeight = stackedPreview;

            double stackedListTop = previewY + previewHeight + (previewHeight > 0 ? GAP : 0);
            list.setBounds(0, stackedListTop, width, Math.max(0, contentBottom - stackedListTop));
        }

        previewVisible = previewHeight >= MIN_PREVIEW_HEIGHT && previewWidth >= 80;
        previewDetailed = previewVisible && previewHeight >= PREVIEW_HEIGHT;

        layoutSearchRow();

        double actionBlockWidth = Math.min(width, 330);
        layoutActionRow(width - actionBlockWidth, actionBlockWidth, actionY);
    }

    private void layoutSearchRow() {
        double rescanWidth = Math.min(88, Math.max(0, searchWidth * 0.3));
        boolean showRescan = rescanWidth >= 56;

        double fieldWidth = showRescan ? searchWidth - rescanWidth - GAP : searchWidth;
        if (searchField != null) {
            searchField.setBounds(searchX + SEARCH_TEXT_PADDING, searchY + 3,
                    Math.max(0, fieldWidth - SEARCH_TEXT_PADDING * 2), SEARCH_HEIGHT - 6);
        }
        if (rescanButton != null) {
            rescanButton.setHidden(!showRescan);
            if (showRescan) {
                rescanButton.setBounds(searchX + searchWidth - rescanWidth, searchY, rescanWidth, SEARCH_HEIGHT);
            }
        }
    }

    private void layoutActionRow(double rightX, double rightWidth, double actionY) {
        double buttonWidth = Math.max(0, (rightWidth - GAP * 2) / 3.0);
        if (applyButton == null) {
            return;
        }
        applyButton.setBounds(rightX, actionY, buttonWidth, ACTION_HEIGHT);
        resetButton.setBounds(rightX + buttonWidth + GAP, actionY, buttonWidth, ACTION_HEIGHT);
        folderButton.setBounds(rightX + (buttonWidth + GAP) * 2, actionY, buttonWidth, ACTION_HEIGHT);
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        if (!systemFontsApplied && FontCatalog.systemFontsReady()) {
            systemFontsApplied = true;
            refreshList(false);
        }
        ensurePreview();
        updatePreview();

        renderRowBackgrounds();
        if (previewVisible) {
            renderPreviewCard();
        }
    }

    private void ensurePreview() {
        if (isDetached() || isDetachedParent()) {
            return;
        }
        if (preview == null || preview.isClosed()) {
            preview = new FontPreview();
            lastHovered = null;
            hoverStableFrames = 0;
        }
    }

    private boolean isDetachedParent() {
        return getParent() instanceof NCMPanel parent && parent.isDetached();
    }

    private void updatePreview() {
        if (preview == null || preview.isClosed()) {
            return;
        }

        FontOption hovered = list == null ? null : list.hoveredOption();
        if (hovered != lastHovered) {
            lastHovered = hovered;
            hoverStableFrames = 0;
        } else if (hovered != null) {
            hoverStableFrames++;
        }

        FontOption override = hovered != null && hoverStableFrames >= HOVER_SETTLE_FRAMES ? hovered : null;
        preview.request(effectiveConfig(override));
    }

    private FontConfig effectiveConfig(FontOption override) {
        FontConfig config = pending.copy();
        if (override != null) {
            FontConfig.Slot slot = config.slot(selectedSlot).copy();
            slot.source = override.source();
            slot.value = override.value();
            config.set(selectedSlot, slot);
        }
        return config;
    }

    private void renderRowBackgrounds() {
        float alpha = getAlpha();
        double ox = getX();
        double oy = getY();
        double searchBoxX = ox + searchX;
        double searchBoxY = oy + searchY;

        roundedRect(searchBoxX, searchBoxY, searchWidth, SEARCH_HEIGHT, 6,
                reAlpha(COLOR_FIELD, alpha));

        boolean focused = searchField != null && searchField.isFocused();
        roundedOutline(searchBoxX, searchBoxY, searchWidth, SEARCH_HEIGHT, 6, 1,
                focused
                        ? new Color(0.30f, 0.50f, 0.82f, alpha * 0.9f)
                        : new Color(1f, 1f, 1f, alpha * 0.09f));

        double statusX = ox + styleRowRight + 14;
        if (statusX + 120 < ox + getWidth()) {
            FontManager.pf12.drawString(FontManager.pf12.trim(statusText(), ox + getWidth() - statusX - 8),
                    statusX, oy + STYLE_ROW_Y + 6, reAlpha(statusColor(), alpha));
        }

        Rect.draw(ox, oy + bodyTop - 4, getWidth(), 1, reAlpha(0xFFFFFFFF, alpha * 0.06f));
    }

    private String statusText() {
        if (!status.isEmpty()) {
            return status;
        }
        if (FontManager.isReloading()) {
            return I18n.get("tritium-music.ui.settings.font.loading");
        }
        if (!previewVisible) {
            String notice = previewNotice();
            if (notice != null) {
                return notice;
            }
        }
        return I18n.get("tritium-music.ui.settings.font.style_hint", slotLabel(selectedSlot));
    }

    private int statusColor() {
        if (!status.isEmpty()) {
            return statusColorValue;
        }
        if (FontManager.isReloading()) {
            return 0xFFE0A030;
        }
        return getColor(NCMScreen.ColorType.SECONDARY_TEXT);
    }

    private void renderPreviewCard() {
        float alpha = getAlpha();
        int primary = reAlpha(getColor(NCMScreen.ColorType.PRIMARY_TEXT), alpha);
        int secondary = reAlpha(getColor(NCMScreen.ColorType.SECONDARY_TEXT), alpha);

        double previewX = getX() + this.previewX;
        double previewY = getY() + this.previewY;

        roundedRect(previewX, previewY, previewWidth, previewHeight, 8,
                reAlpha(getColor(NCMScreen.ColorType.ELEMENT_BACKGROUND), alpha));
        roundedOutline(previewX, previewY, previewWidth, previewHeight, 8, 1,
                new Color(1f, 1f, 1f, alpha * 0.06f));

        double headerHeight = 22;
        Rect.draw(previewX + 1, previewY + headerHeight, previewWidth - 2, 1,
                reAlpha(COLOR_CARD_DIVIDER, alpha));
        FontManager.pf12bold.drawString(I18n.get("tritium-music.ui.settings.font.preview"),
                previewX + 10, previewY + headerHeight * .5 - FontManager.pf12.getHeight() * .5, secondary);

        String notice = previewNotice();
        if (notice != null) {
            double noticeWidth = Math.min(FontManager.pf12.getStringWidthD(notice), previewWidth * 0.66);
            FontManager.pf12.drawString(FontManager.pf12.trim(notice, noticeWidth),
                    previewX + previewWidth - 10 - noticeWidth, previewY + headerHeight * .5 - FontManager.pf12.getHeight() * .5, reAlpha(0xFFE0A030, alpha));
        }

        double sampleTop = previewY + headerHeight + 8;
        CFontRenderer normal = preview == null ? null : preview.normal();
        CFontRenderer bold = preview == null ? null : preview.bold();

        if (normal == null || bold == null) {
            FontManager.pf14.drawString(I18n.get("tritium-music.ui.settings.font.preparing"),
                    previewX + 12, sampleTop, secondary);
            return;
        }

        String normalSample = I18n.get("tritium-music.ui.settings.font.sample.normal");
        normal.drawString(normal.trim(normalSample, previewWidth - 24), previewX + 12, sampleTop, primary);

        if (!previewDetailed) {
            return;
        }

        String boldSample = I18n.get("tritium-music.ui.settings.font.sample.bold");
        bold.drawString(bold.trim(boldSample, previewWidth - 24), previewX + 12, sampleTop + 34, primary);

        double dividerY = previewY + previewHeight - 52;
        if (dividerY > sampleTop + 48) {
            Rect.draw(previewX + 12, dividerY, previewWidth - 24, 1, reAlpha(COLOR_CARD_DIVIDER, alpha));
        }

        renderSlotSummary(secondary, previewY + previewHeight - 38);
    }

    private String previewNotice() {
        if (preview != null && !preview.cjkSupported()) {
            return I18n.get("tritium-music.ui.settings.font.warning.cjk");
        }
        if (pending.shaping && !FontManager.isShapingActive()) {
            return I18n.get("tritium-music.ui.settings.font.shaping_unavailable");
        }
        return null;
    }

    private void renderSlotSummary(int secondary, double fromY) {
        FontConfig.SlotType[] types = FontConfig.SlotType.values();
        double previewX = getX() + this.previewX;
        double columnWidth = (previewWidth - 24) * 0.5;

        for (int index = 0; index < types.length; index++) {
            FontConfig.SlotType type = types[index];
            FontConfig.Slot slot = pending.slot(type);
            String text = slotLabel(type) + ": " + FontCatalog.describe(slot.source, slot.value) + styleSuffix(slot);
            double x = previewX + 12 + (index % 2) * columnWidth;
            double y = fromY + Math.floorDiv(index, 2) * 15;
            FontManager.pf12.drawString(FontManager.pf12.trim(text, columnWidth - 8), x, y, secondary);
        }
    }

    private void selectFont(FontOption option) {
        FontConfig.Slot slot = pending.slot(selectedSlot).copy();
        slot.source = option.source();
        slot.value = option.value();
        pending.set(selectedSlot, slot);
        setStatus("");
    }

    private void apply() {
        pending.normalize();
        FontConfig config = FontConfig.get();
        config.assign(pending);
        config.save();
        FontManager.reload();
        setStatus(I18n.get("tritium-music.ui.settings.font.applied"), 0xFF6FCF97);
    }

    public void resetToDefault() {
        pending.reset();
        pending.normalize();
        refreshList(true);
        apply();
    }

    private void rescan() {
        FontCatalog.refresh();
        FontCatalog.preloadSystemFontsAsync();
        refreshList(false);
        setStatus(I18n.get("tritium-music.ui.settings.font.rescanned"), 0xFF6FCF97);
    }

    private void openFontFolder() {
        File dir = FontCatalog.userFontDir();
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir);
                return;
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        Platform.sendChatMessage(I18n.get("tritium-music.ui.settings.font.folder_path", dir.getAbsolutePath()));
    }

    private void refreshList(boolean scrollToSelected) {
        if (list == null) {
            return;
        }
        list.setOptions(FontCatalog.search(search));
        list.setSelectedId(selectedOptionId());
        if (scrollToSelected) {
            list.scrollToSelected();
        }
    }

    private String selectedOptionId() {
        FontConfig.Slot slot = pending.slot(selectedSlot);
        return slot.source + ":" + slot.value;
    }

    private void setStatus(String text) {
        setStatus(text, 0xFF8A8D94);
    }

    private void setStatus(String text, int color) {
        this.status = text == null ? "" : text;
        this.statusColorValue = color;
    }

    @Override
    public void onRemoved() {
        if (preview != null) {
            preview.close();
        }
    }

    private static String slotLabel(FontConfig.SlotType type) {
        return I18n.get(switch (type) {
            case MAIN -> "tritium-music.ui.settings.font.slot.main";
            case ENGLISH -> "tritium-music.ui.settings.font.slot.english";
            case MAIN_BOLD -> "tritium-music.ui.settings.font.slot.main_bold";
            case ENGLISH_BOLD -> "tritium-music.ui.settings.font.slot.english_bold";
        });
    }

    private static String styleLabel(String style) {
        return I18n.get(switch (style) {
            case FontConfig.STYLE_BOLD -> "tritium-music.ui.settings.font.style.bold";
            case FontConfig.STYLE_ITALIC -> "tritium-music.ui.settings.font.style.italic";
            case FontConfig.STYLE_BOLD_ITALIC -> "tritium-music.ui.settings.font.style.bold_italic";
            default -> "tritium-music.ui.settings.font.style.plain";
        });
    }

    private static String styleSuffix(FontConfig.Slot slot) {
        if (slot.style == null || FontConfig.STYLE_PLAIN.equals(slot.style)) {
            return "";
        }
        return " · " + styleLabel(slot.style);
    }
}
