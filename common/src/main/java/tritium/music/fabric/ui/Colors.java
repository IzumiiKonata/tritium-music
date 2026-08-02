package tritium.music.fabric.ui;

import net.minecraft.util.ARGB;

public final class Colors {

    private Colors() {
    }

    public static final int BACKGROUND = 0xFF0E0F12;
    public static final int ELEMENT = 0xFF16171B;
    public static final int ELEMENT_HOVER = 0xFF24262C;
    public static final int PRIMARY_TEXT = 0xFFF2F3F5;
    public static final int SECONDARY_TEXT = 0xFF8A8D94;
    public static final int ACCENT = 0xFFD60017;

    public static int withAlpha(int argb, float alpha) {
        return ARGB.color((int) (alpha * 255f) & 0xFF, argb & 0xFFFFFF);
    }

    public static int withAlpha(int argb, int alpha) {
        return ARGB.color(alpha & 0xFF, argb & 0xFFFFFF);
    }

    public static int lerp(int from, int to, float t) {
        int fa = from >>> 24, fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int ta = to >>> 24, tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        return ARGB.color(
                Math.round(fa + (ta - fa) * t),
                Math.round(fr + (tr - fr) * t),
                Math.round(fg + (tg - fg) * t),
                Math.round(fb + (tb - fb) * t)
        );
    }
}
