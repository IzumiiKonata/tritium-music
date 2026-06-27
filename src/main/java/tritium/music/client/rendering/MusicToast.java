package tritium.music.client.rendering;

import lombok.experimental.UtilityClass;
import tritium.music.client.rendering.animation.Animation;
import tritium.music.client.rendering.animation.Easing;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.util.Mth;

import java.time.Duration;

/**
 * Now-playing toast slid in from the top-left of the HUD. The original used a
 * 9-patch background texture + an animated music-notes sprite; here the
 * background is a rounded rect and the notes glyph cycles through a rainbow,
 * preserving the slide-in/out animation and colour cycling.
 */
@UtilityClass
public class MusicToast {

    private int musicNoteColorTick;
    private long lastMusicNoteColorChange;
    private int musicNoteColor = -1;
    private String text = null;
    private boolean forward = true;
    private double offset = -240;
    private long waitStart = -1L;
    private final Animation animation = new Animation(Easing.EASE_OUT_QUART, Duration.ofMillis(750));

    public void pushMusicToast(String name) {
        text = name;
        waitStart = -1L;
        forward = true;

        double iconSize = 16;
        double spacing = 4;
        double contentWidth = iconSize + spacing + FontManager.pf16bold.getStringWidthD(text);

        offset = -Math.max(120, contentWidth + 12) * 1.25;
        animation.setValue(offset);
    }

    private void tickMusicNotes() {
        long now;
        if ((now = System.currentTimeMillis()) > lastMusicNoteColorChange + 25L) {
            lastMusicNoteColorChange = now;
            musicNoteColor = getLerpedColor(++musicNoteColorTick);
        }
    }

    public void render() {
        if (text == null) {
            return;
        }

        double iconSize = 16;
        double spacing = 4;
        double contentWidth = iconSize + spacing + FontManager.pf16bold.getStringWidthD(text);

        double toastWidth = Math.max(120, contentWidth + 12), toastHeight = 24;

        offset = animation.run(forward ? 0 : -toastWidth * 1.25);

        double offsetX = offset + 1;
        double offsetY = 1;

        if (!forward && offset + toastWidth < 0)
            return;

        if (forward && offset >= -.5) {
            if (waitStart == -1L) {
                waitStart = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - waitStart > 5000L) {
                forward = false;
            }
        }

        if (!forward && offset <= -toastWidth * 1.2) {
            text = null;
            return;
        }

        tritium.music.client.render.Render.roundedRect(tritium.music.client.render.RenderContext.graphics(),
                (float) offsetX, (float) offsetY, (float) toastWidth, (float) toastHeight, 6, RGBA.color(0x16, 0x17, 0x1B, 245));

        tickMusicNotes();
        double iconX = offsetX + toastWidth * .5 - contentWidth * .5;
        FontManager.music18.drawString("A", iconX, offsetY + toastHeight * .5 - FontManager.music18.getFontHeight() * .5, musicNoteColor);

        FontManager.pf16bold.drawString(text, iconX + iconSize + spacing, offsetY + toastHeight * .5 - FontManager.pf16bold.getFontHeight() * .5, -1);
    }

    private int getLerpedColor(float tick) {
        int colorDuration = 30;
        int tickCount = Mth.floor(tick);
        int value = tickCount / colorDuration;
        int colorCount = MUSIC_NOTE_COLORS.length;
        int c1 = value % colorCount;
        int c2 = (value + 1) % colorCount;
        float subStep = ((float) (tickCount % colorDuration) + Mth.frac(tick)) / (float) colorDuration;
        int color1 = brighten(MUSIC_NOTE_COLORS[c1]);
        int color2 = brighten(MUSIC_NOTE_COLORS[c2]);
        return RGBA.srgbLerp(subStep, color1, color2);
    }

    private int brighten(int rgb) {
        float brightness = 1.25f;
        return RGBA.color(
                Math.min(255, Mth.floor(RGBA.red(rgb) * brightness)),
                Math.min(255, Mth.floor(RGBA.green(rgb) * brightness)),
                Math.min(255, Mth.floor(RGBA.blue(rgb) * brightness)),
                255);
    }

    private final int[] MUSIC_NOTE_COLORS = new int[]{
            0xF9FFFE, 0x9D9D97, 0x3847A6, 0x3C44AA, 0x169C9C,
            0x5E7C16, 0x80C71F, 0xFED83D, 0xF9801D, 0xF38BAA,
            0xB02E26, 0xC74EBD
    };
}
