package tritium.music.client.rendering.font;

import com.mojang.blaze3d.platform.NativeImage;
import lombok.RequiredArgsConstructor;
import net.minecraft.resources.Identifier;

@RequiredArgsConstructor
public class Glyph {
    public final int width, height;
    public final char value;

    public float u0, v0, u1, v1;
    public volatile boolean uploaded = false;
    public Identifier atlasIdentifier;
    public NativeImage atlasImage;

    public void setAtlasRegion(TextureAtlas.AtlasRegion region) {
        this.u0 = region.u0;
        this.v0 = region.v0;
        this.u1 = region.u1;
        this.v1 = region.v1;
        this.atlasIdentifier = region.identifier;
        this.atlasImage = region.image;
        this.uploaded = true;
    }
}
