package tritium.music.client.rendering;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.textures.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;

public final class TRenderTarget implements AutoCloseable {

    private TTexture colorTexture;
    private GpuTexture depthTexture;
    private GpuTextureView depthView;
    private GpuBuffer vertexBuffer;
    private final Identifier identifier;
    private int width;
    private int height;

    private TRenderTarget(String name, int width, int height) {
        this.width = width;
        this.height = height;
        this.identifier = Identifier.fromNamespaceAndPath("tritium", "render-target-" + name);
        createTextures();
    }

    public static TRenderTarget create(String name, int width, int height) {
        return new TRenderTarget(name, width, height);
    }

    private void createTextures() {
        var device = com.mojang.blaze3d.systems.RenderSystem.getDevice();

        final var colorTexture = device.createTexture(
                () -> "tritium-rt-color",
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC,
                TextureFormat.RGBA8,
                width, height, 1, 1
        );
        final var colorView = device.createTextureView(colorTexture);

        depthTexture = device.createTexture(
                () -> "tritium-rt-depth",
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC,
                TextureFormat.DEPTH32,
                width, height, 1, 1
        );
        depthView = device.createTextureView(depthTexture);

        final var sampler = com.mojang.blaze3d.systems.RenderSystem.getDevice().createSampler(
                AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.NEAREST, FilterMode.NEAREST,
                1, OptionalDouble.empty()
        );

        this.colorTexture = new TTexture(colorTexture, colorView, sampler);

        Minecraft.getInstance().getTextureManager().register(identifier, getColorTexture());
    }

    public void resize(int newWidth, int newHeight) {
        if (newWidth == width && newHeight == height) return;
        destroyTextures();
        width = newWidth;
        height = newHeight;
        createTextures();
    }

    public Identifier getIdentifier() {
        return identifier;
    }

    public void clear() {
        var device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
        var encoder = device.createCommandEncoder();
        encoder.clearColorAndDepthTextures(colorTexture.getTexture(), 0, depthTexture, 1.0);
    }

    public GpuTextureView colorView() {
        return colorTexture.getTextureView();
    }

    public GpuTextureView depthView() {
        return depthView;
    }

    public GpuTexture colorTexture() {
        return colorTexture.getTexture();
    }

    public GpuSampler sampler() {
        return colorTexture.getSampler();
    }

    GpuBuffer uploadVertices(ByteBuffer data) {
        var device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
        if (vertexBuffer == null || vertexBuffer.size() < data.remaining()) {
            if (vertexBuffer != null) vertexBuffer.close();
            vertexBuffer = device.createBuffer(
                    () -> identifier + " vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    data.remaining()
            );
        }
        device.createCommandEncoder().writeToBuffer(vertexBuffer.slice(0, data.remaining()), data);
        return vertexBuffer;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public TTexture getColorTexture() {
        return colorTexture;
    }

    private void destroyTextures() {
        Minecraft.getInstance().getTextureManager().release(identifier);
        if (depthView != null) depthView.close();
        if (depthTexture != null) depthTexture.close();
    }

    @Override
    public void close() {
        destroyTextures();
        if (vertexBuffer != null) vertexBuffer.close();
    }
}
