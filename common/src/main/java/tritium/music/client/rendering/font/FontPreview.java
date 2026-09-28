package tritium.music.client.rendering.font;

import tritium.music.client.config.FontConfig;
import tritium.music.core.util.AsyncUtil;

import java.awt.Font;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class FontPreview implements AutoCloseable {

    public static final float NORMAL_SIZE = 20f;
    public static final float BOLD_SIZE = 26f;

    private static final String CJK_SAMPLE = "音乐播放器测试中文";
    private static final Set<FontPreview> LIVE = ConcurrentHashMap.newKeySet();

    private final AtomicLong requestId = new AtomicLong();

    private volatile boolean closed;
    private volatile String signature = "";
    private volatile boolean ready;
    private volatile boolean cjkSupported = true;

    private FontLibrary.Loaded mainResource;
    private FontLibrary.Loaded englishResource;
    private FontLibrary.Loaded mainBoldResource;
    private FontLibrary.Loaded englishBoldResource;
    private CFontRenderer normalRenderer;
    private CFontRenderer boldRenderer;

    public FontPreview() {
        LIVE.add(this);
    }

    public boolean isClosed() {
        return closed;
    }

    public static void disposeAll() {
        for (FontPreview preview : List.copyOf(LIVE)) {
            preview.close();
        }
        LIVE.clear();
    }

    public void request(FontConfig config) {
        if (closed || config == null) {
            return;
        }

        FontConfig snapshot = config.copy();
        snapshot.normalize();
        String requested = signature(snapshot);
        if (requested.equals(signature)) {
            return;
        }
        signature = requested;

        long id = requestId.incrementAndGet();
        AsyncUtil.runAsync(() -> {
            FontLibrary.Loaded main = FontLibrary.acquire(snapshot.main.source, snapshot.main.value, snapshot.main.style);
            FontLibrary.Loaded english = FontLibrary.acquire(snapshot.english.source, snapshot.english.value, snapshot.english.style);
            FontLibrary.Loaded mainBold = FontLibrary.acquire(snapshot.mainBold.source, snapshot.mainBold.value, snapshot.mainBold.style);
            FontLibrary.Loaded englishBold = FontLibrary.acquire(snapshot.englishBold.source, snapshot.englishBold.value, snapshot.englishBold.style);

            if (closed || id != requestId.get()) {
                closeAll(main, english, mainBold, englishBold);
                return;
            }

            AsyncUtil.runOnRenderThread(() -> {
                if (closed || id != requestId.get()) {
                    closeAll(main, english, mainBold, englishBold);
                    return;
                }
                install(snapshot, main, english, mainBold, englishBold);
            });
        });
    }

    private void install(FontConfig config,
                         FontLibrary.Loaded main,
                         FontLibrary.Loaded english,
                         FontLibrary.Loaded mainBold,
                         FontLibrary.Loaded englishBold) {
        releaseResources();

        mainResource = main;
        englishResource = english;
        mainBoldResource = mainBold;
        englishBoldResource = englishBold;

        CFontRenderer.Face mainFace = new CFontRenderer.Face(main.font(), main.shaper());
        CFontRenderer.Face englishFace = new CFontRenderer.Face(english.font(), english.shaper());
        CFontRenderer.Face mainBoldFace = new CFontRenderer.Face(mainBold.font(), mainBold.shaper());
        CFontRenderer.Face englishBoldFace = new CFontRenderer.Face(englishBold.font(), englishBold.shaper());

        if (normalRenderer == null) {
            normalRenderer = new CFontRenderer(englishFace, NORMAL_SIZE * 0.5f, mainFace);
        } else {
            normalRenderer.reload(englishFace, NORMAL_SIZE * 0.5f, mainFace);
        }

        if (boldRenderer == null) {
            boldRenderer = new CFontRenderer(englishBoldFace, BOLD_SIZE * 0.5f, mainBoldFace);
        } else {
            boldRenderer.reload(englishBoldFace, BOLD_SIZE * 0.5f, mainBoldFace);
        }

        cjkSupported = supports(englishFace.font(), mainFace.font())
                && supports(englishBoldFace.font(), mainBoldFace.font());
        ready = normalRenderer != null && boldRenderer != null;
    }

    private static boolean supports(Font primary, Font fallback) {
        if (primary != null && primary.canDisplayUpTo(CJK_SAMPLE) < 0) {
            return true;
        }
        return fallback != null && fallback.canDisplayUpTo(CJK_SAMPLE) < 0;
    }

    private static Font derive(FontLibrary.Loaded resource, FontConfig.Slot slot) {
        return resource == null ? null : resource.font();
    }

    public CFontRenderer normal() {
        return ready ? normalRenderer : null;
    }

    public CFontRenderer bold() {
        return ready ? boldRenderer : null;
    }

    public boolean ready() {
        return ready;
    }

    public boolean cjkSupported() {
        return cjkSupported;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        LIVE.remove(this);
        requestId.incrementAndGet();
        AsyncUtil.runOnRenderThread(this::releaseAll);
    }

    private void releaseAll() {
        if (normalRenderer != null) {
            normalRenderer.close();
            normalRenderer = null;
        }
        if (boldRenderer != null) {
            boldRenderer.close();
            boldRenderer = null;
        }
        releaseResources();
        ready = false;
    }

    private void releaseResources() {
        closeAll(mainResource, englishResource, mainBoldResource, englishBoldResource);
        mainResource = null;
        englishResource = null;
        mainBoldResource = null;
        englishBoldResource = null;
    }

    private static void closeAll(FontLibrary.Loaded... resources) {
        for (FontLibrary.Loaded resource : resources) {
            if (resource != null) {
                resource.close();
            }
        }
    }

    private static String signature(FontConfig config) {
        StringBuilder builder = new StringBuilder(160);
        for (FontConfig.SlotType type : FontConfig.SlotType.values()) {
            FontConfig.Slot slot = config.slot(type);
            builder.append(type.name()).append('|')
                    .append(slot.source).append('|')
                    .append(slot.value).append('|')
                    .append(slot.style).append('\n');
        }
        return builder.toString();
    }
}
