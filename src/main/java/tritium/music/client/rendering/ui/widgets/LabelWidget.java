package tritium.music.client.rendering.ui.widgets;

import lombok.Getter;
import tritium.music.client.rendering.ScrollText;
import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.ui.AbstractWidget;

import java.util.function.Supplier;

public class LabelWidget extends AbstractWidget<LabelWidget> {

    Supplier<String> label = () -> "点击输入文字";
    @Getter
    CFontRenderer font = FontManager.pf18;

    @Getter
    double maxWidth = -1;

    @Getter
    private WidthLimitType widthLimitType = WidthLimitType.SCROLL;

    public enum WidthLimitType {
        SCROLL,
        TRIM_TO_WIDTH
    }

    private ScrollText scrollText;

    private ScrollText scrollText() {
        if (scrollText == null) {
            scrollText = new ScrollText();
        }
        return scrollText;
    }

    public LabelWidget(String label, CFontRenderer font) {
        this.setLabel(label);
        this.setFont(font);
    }

    public LabelWidget(Supplier<String> label, CFontRenderer font) {
        this.setLabel(label);
        this.setFont(font);
    }

    public LabelWidget(String label) {
        this.setLabel(label);
    }

    public LabelWidget(Supplier<String> label) {
        this.setLabel(label);
    }

    public LabelWidget() {
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        boolean widthNotLimited = this.getMaxWidth() == -1;

        String lbl = this.getLabel();

        if (widthNotLimited)
            font.drawString(lbl, this.getX(), this.getY(), this.getHexColor());
        else {
            if (this.widthLimitType == WidthLimitType.SCROLL) {
                this.scrollText().render(font, lbl, this.getX(), this.getY(), this.getMaxWidth(), this.getHexColor());
            } else {
                font.drawString(font.trim(lbl, this.getMaxWidth()), this.getX(), this.getY(), this.getHexColor());
            }
        }

        double width;
        double stringWidth = font.getStringWidthD(lbl);
        width = widthNotLimited ? stringWidth : Math.min(this.getMaxWidth(), stringWidth);
        this.setBounds(width, font.getStringHeight(lbl));
    }

    public LabelWidget setMaxWidth(double maxWidth) {
        this.maxWidth = maxWidth;
        return this;
    }

    public LabelWidget setWidthLimitType(WidthLimitType widthLimitType) {
        this.widthLimitType = widthLimitType;
        return this;
    }

    public LabelWidget setFont(CFontRenderer font) {
        this.font = font;
        return this;
    }

    public String getLabel() {
        String lbl = label.get();
        return lbl == null ? "null" : lbl;
    }

    public LabelWidget setLabel(String label) {
        this.setLabel(() -> label);
        return this;
    }

    public LabelWidget setLabel(Supplier<String> label) {
        this.label = label;
        return this;
    }
}
