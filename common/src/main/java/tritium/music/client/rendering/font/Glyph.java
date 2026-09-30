package tritium.music.client.rendering.font;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;

public class Glyph {

    public final int width;
    public final int height;
    public final int bitmapWidth;
    public final int bitmapHeight;
    public final int originX;
    public final int originY;
    public final int overhang;
    public final char value;

    public float u0, v0, u1, v1;
    public volatile boolean uploaded = false;
    public ResourceLocation atlasIdentifier;
    public NativeImage atlasImage;

    public Glyph(int width, int height, int bitmapWidth, int bitmapHeight,
                 int originX, int originY, int overhang, char value) {
        this.width = width;
        this.height = height;
        this.bitmapWidth = bitmapWidth;
        this.bitmapHeight = bitmapHeight;
        this.originX = originX;
        this.originY = originY;
        this.overhang = overhang;
        this.value = value;
    }

    public int advance() {
        return width + overhang;
    }

    public void setAtlasRegion(TextureAtlas.AtlasRegion region) {
        this.u0 = region.u0();
        this.v0 = region.v0();
        this.u1 = region.u1();
        this.v1 = region.v1();
        this.atlasIdentifier = region.identifier();
        this.atlasImage = region.image();
        this.uploaded = true;
    }
}
