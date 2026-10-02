package tritium.music.client.rendering.font;

import tritium.music.client.config.FontConfig;
import tritium.music.core.util.AsyncUtil;
import tritium.music.platform.Platform;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class FontManager {

    public static CFontRenderer pf12bold, pf14bold, pf16bold, pf18bold, pf20bold, pf25bold, pf28bold, pf34bold, pf40bold, pf50bold, pf65bold;
    public static CFontRenderer pf12, pf14, pf16, pf18, pf20, pf25, pf32;
    public static CFontRenderer icon30;
    public static CFontRenderer music18, music40;

    private static final String FONT_PATH = FontLibrary.FONT_PATH;

    private static final String SYSTEM_FALLBACK_FAMILY = Font.SANS_SERIF;

    private static final AtomicBoolean RELOADING = new AtomicBoolean();
    private static final AtomicBoolean RELOAD_PENDING = new AtomicBoolean();
    private static volatile boolean loaded = false;
    private static volatile boolean shapingActive = true;

    private static FontLibrary.Loaded mainResource;
    private static FontLibrary.Loaded englishResource;
    private static FontLibrary.Loaded mainBoldResource;
    private static FontLibrary.Loaded englishBoldResource;

    private static CFontRenderer[] lyricFonts() {
        return new CFontRenderer[]{
                pf18bold, pf28bold, pf34bold, pf65bold
        };
    }

    public static void prewarmGlyphs(String text) {
        if (text == null || text.isEmpty()) return;
        for (CFontRenderer fr : lyricFonts()) {
            if (fr != null) fr.prewarm(text);
        }
    }

    public static boolean isReloading() {
        return RELOADING.get();
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static boolean isShapingActive() {
        return shapingActive;
    }

    public static void retryShaping() {
        if (!loaded) {
            return;
        }
        if (FontLibrary.invalidateWithoutShaper() > 0) {
            reload();
        }
    }

    public static void loadFonts() {
        if (loaded) {
            return;
        }
        loaded = true;

        FontConfig config = FontConfig.get().copy();
        config.normalize();

        try {
            apply(config,
                    acquire(config.main, FontConfig.DEFAULT_MAIN, FontConfig.DEFAULT_ENGLISH),
                    acquire(config.english, FontConfig.DEFAULT_ENGLISH, FontConfig.DEFAULT_ENGLISH),
                    acquire(config.mainBold, FontConfig.DEFAULT_MAIN_BOLD, FontConfig.DEFAULT_ENGLISH_BOLD),
                    acquire(config.englishBold, FontConfig.DEFAULT_ENGLISH_BOLD, FontConfig.DEFAULT_ENGLISH_BOLD),
                    true);
        } catch (Throwable throwable) {
            Platform.log("[font] the configured fonts could not be loaded (" + describe(throwable)
                    + "), falling back to the bundled defaults");
            loadDefaults();
        }
    }

    private static void loadDefaults() {
        FontConfig defaults = new FontConfig();
        defaults.normalize();
        try {
            apply(defaults,
                    acquire(defaults.main, FontConfig.DEFAULT_MAIN, FontConfig.DEFAULT_ENGLISH),
                    acquire(defaults.english, FontConfig.DEFAULT_ENGLISH, FontConfig.DEFAULT_ENGLISH),
                    acquire(defaults.mainBold, FontConfig.DEFAULT_MAIN_BOLD, FontConfig.DEFAULT_ENGLISH_BOLD),
                    acquire(defaults.englishBold, FontConfig.DEFAULT_ENGLISH_BOLD, FontConfig.DEFAULT_ENGLISH_BOLD),
                    true);
        } catch (Throwable throwable) {
            Platform.log("[font] the bundled fonts could not be loaded: " + describe(throwable));
        }
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    public static void reload() {
        if (!loaded) {
            loadFonts();
            return;
        }
        if (!RELOADING.compareAndSet(false, true)) {
            RELOAD_PENDING.set(true);
            return;
        }

        FontConfig config = FontConfig.get().copy();
        config.normalize();

        AsyncUtil.runAsync(() -> {
            FontLibrary.Loaded main;
            FontLibrary.Loaded english;
            FontLibrary.Loaded mainBold;
            FontLibrary.Loaded englishBold;
            try {
                main = acquire(config.main, FontConfig.DEFAULT_MAIN, FontConfig.DEFAULT_ENGLISH);
                english = acquire(config.english, FontConfig.DEFAULT_ENGLISH, FontConfig.DEFAULT_ENGLISH);
                mainBold = acquire(config.mainBold, FontConfig.DEFAULT_MAIN_BOLD, FontConfig.DEFAULT_ENGLISH_BOLD);
                englishBold = acquire(config.englishBold, FontConfig.DEFAULT_ENGLISH_BOLD, FontConfig.DEFAULT_ENGLISH_BOLD);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                finishReload();
                return;
            }

            AsyncUtil.runOnRenderThread(() -> {
                try {
                    apply(config, main, english, mainBold, englishBold, false);
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                    close(main);
                    close(english);
                    close(mainBold);
                    close(englishBold);
                } finally {
                    finishReload();
                }
            });
        });
    }

    private static void finishReload() {
        RELOADING.set(false);
        if (RELOAD_PENDING.compareAndSet(true, false)) {
            reload();
        }
    }

    public static void dispose() {
        FontPreview.disposeAll();

        for (CFontRenderer renderer : allRenderers()) {
            if (renderer != null) {
                renderer.close();
            }
        }

        releaseCurrentResources();
        FontLibrary.disposeAll();

        pf12 = pf14 = pf16 = pf18 = pf20 = pf25 = pf32 = null;
        pf12bold = pf14bold = pf16bold = pf18bold = pf20bold = pf25bold = null;
        pf28bold = pf34bold = pf40bold = pf50bold = pf65bold = null;
        icon30 = null;
        music18 = null;
        music40 = null;

        loaded = false;
    }

    private static void apply(FontConfig config,
                              FontLibrary.Loaded main,
                              FontLibrary.Loaded english,
                              FontLibrary.Loaded mainBold,
                              FontLibrary.Loaded englishBold,
                              boolean create) {
        CFontRenderer.advancedShaping = config.shaping;
        FontShaper.discretionaryLigatures = config.discretionaryLigatures;

        CFontRenderer.Face mainFace = face(main);
        CFontRenderer.Face englishFace = face(english);
        CFontRenderer.Face mainBoldFace = face(mainBold);
        CFontRenderer.Face englishBoldFace = face(englishBold);

        pf12 = assign(pf12, 12, englishFace, mainFace, create);
        pf14 = assign(pf14, 14, englishFace, mainFace, create);
        pf16 = assign(pf16, 16, englishFace, mainFace, create);
        pf18 = assign(pf18, 18, englishFace, mainFace, create);
        pf20 = assign(pf20, 20, englishFace, mainFace, create);
        pf25 = assign(pf25, 25, englishFace, mainFace, create);
        pf32 = assign(pf32, 32, englishFace, mainFace, create);

        pf12bold = assign(pf12bold, 12, englishBoldFace, mainBoldFace, create);
        pf14bold = assign(pf14bold, 14, englishBoldFace, mainBoldFace, create);
        pf16bold = assign(pf16bold, 16, englishBoldFace, mainBoldFace, create);
        pf18bold = assign(pf18bold, 18, englishBoldFace, mainBoldFace, create);
        pf20bold = assign(pf20bold, 20, englishBoldFace, mainBoldFace, create);
        pf25bold = assign(pf25bold, 25, englishBoldFace, mainBoldFace, create);
        pf28bold = assign(pf28bold, 28, englishBoldFace, mainBoldFace, create);
        pf34bold = assign(pf34bold, 34, englishBoldFace, mainBoldFace, create);
        pf40bold = assign(pf40bold, 40, englishBoldFace, mainBoldFace, create);
        pf50bold = assign(pf50bold, 50, englishBoldFace, mainBoldFace, create);
        pf65bold = assign(pf65bold, 65, englishBoldFace, mainBoldFace, create);

        if (icon30 == null) {
            icon30 = createIconRenderer(30, "icomoon");
            music18 = createIconRenderer(18, "music");
            music40 = createIconRenderer(40, "music");
        }

        releaseCurrentResources();
        mainResource = main;
        englishResource = english;
        mainBoldResource = mainBold;
        englishBoldResource = englishBold;

        shapingActive = config.shaping && pf14bold != null && pf14bold.isShapingEnabled();
        logShapingState(config, main, english, mainBold, englishBold);
    }

    private static void logShapingState(FontConfig config,
                                        FontLibrary.Loaded main,
                                        FontLibrary.Loaded english,
                                        FontLibrary.Loaded mainBold,
                                        FontLibrary.Loaded englishBold) {
        try {
            Platform.log(String.format(java.util.Locale.ROOT,
                    "[font] advanced shaping=%s | harfbuzz=%s | english=%s:%s shaper=%s | main=%s:%s shaper=%s | englishBold shaper=%s | mainBold shaper=%s",
                    shapingActive,
                    HarfBuzzSupport.isAvailable(),
                    config.english.source, config.english.value, backend(english),
                    config.main.source, config.main.value, backend(main),
                    backend(englishBold),
                    backend(mainBold)));
        } catch (Throwable ignored) {
        }
    }

    private static String backend(FontLibrary.Loaded resource) {
        FontShaper shaper = resource == null ? null : resource.shaper();
        return shaper == null ? "none" : shaper.backendName();
    }

    private static CFontRenderer.Face face(FontLibrary.Loaded resource) {
        if (resource == null) {
            return new CFontRenderer.Face(null, null);
        }
        return new CFontRenderer.Face(resource.font(), resource.shaper());
    }

    private static CFontRenderer assign(CFontRenderer existing, float size,
                                        CFontRenderer.Face primary, CFontRenderer.Face fallback,
                                        boolean create) {
        float sizePx = size * 0.5f;
        if (create || existing == null) {
            return new CFontRenderer(primary, sizePx, fallback);
        }
        existing.reload(primary, sizePx, fallback);
        return existing;
    }

    private static CFontRenderer createIconRenderer(float size, String name) {
        String path = FONT_PATH + name + ".ttf";
        Font font = readFont(path);
        if (font == null) {
            return null;
        }
        return new CFontRenderer(font, size * 0.5f, (FontShaper) null, font);
    }

    private static final java.util.Map<String, Font> FONT_CACHE = new java.util.HashMap<>();

    private static Font readFont(String path) {
        synchronized (FONT_CACHE) {
            return FONT_CACHE.computeIfAbsent(path, p -> {
                try (java.io.InputStream stream = FontManager.class.getResourceAsStream(p)) {
                    if (stream == null) {
                        return null;
                    }
                    return Font.createFont(Font.TRUETYPE_FONT, stream);
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            });
        }
    }

    private static FontLibrary.Loaded acquire(FontConfig.Slot slot, String fallbackValue, String latinFallback) {
        FontLibrary.Loaded resource = FontLibrary.acquire(slot.source, slot.value, slot.style);
        if (resource.available()) {
            return resource;
        }
        resource.close();

        if (!slot.value.equals(fallbackValue)) {
            resource = FontLibrary.acquire(FontConfig.SOURCE_BUILTIN, fallbackValue, slot.style);
            if (resource.available()) {
                return resource;
            }
            resource.close();
        }

        if (latinFallback != null && !latinFallback.equals(slot.value) && !latinFallback.equals(fallbackValue)) {
            resource = FontLibrary.acquire(FontConfig.SOURCE_BUILTIN, latinFallback, slot.style);
            if (resource.available()) {
                return resource;
            }
            resource.close();
        }

        return FontLibrary.acquire(FontConfig.SOURCE_SYSTEM, SYSTEM_FALLBACK_FAMILY, slot.style);
    }

    private static void releaseCurrentResources() {
        close(mainResource);
        close(englishResource);
        close(mainBoldResource);
        close(englishBoldResource);
        mainResource = null;
        englishResource = null;
        mainBoldResource = null;
        englishBoldResource = null;
    }

    private static void close(FontLibrary.Loaded resource) {
        if (resource != null) {
            resource.close();
        }
    }

    public static List<CFontRenderer> allRenderers() {
        List<CFontRenderer> renderers = new ArrayList<>(23);
        renderers.add(pf12);
        renderers.add(pf14);
        renderers.add(pf16);
        renderers.add(pf18);
        renderers.add(pf20);
        renderers.add(pf25);
        renderers.add(pf32);
        renderers.add(pf12bold);
        renderers.add(pf14bold);
        renderers.add(pf16bold);
        renderers.add(pf18bold);
        renderers.add(pf20bold);
        renderers.add(pf25bold);
        renderers.add(pf28bold);
        renderers.add(pf34bold);
        renderers.add(pf40bold);
        renderers.add(pf50bold);
        renderers.add(pf65bold);
        renderers.add(icon30);
        renderers.add(music18);
        renderers.add(music40);
        return renderers;
    }
}
