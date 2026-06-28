package tritium.music.client.rendering;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;

import java.util.OptionalDouble;

public final class LuminRenderTarget implements AutoCloseable {

        private LuminTexture colorTexture;
        private GpuTexture depthTexture;
        private GpuTextureView depthView;
        private final Identifier identifier;
        private int width;
        private int height;

        private LuminRenderTarget(String name, int width, int height) {
            this.width = width;
            this.height = height;
            this.identifier = Identifier.fromNamespaceAndPath("tritium", "render-target-" + name);
            createTextures();
        }

        public static LuminRenderTarget create(String name, int width, int height) {
            return new LuminRenderTarget(name, width, height);
        }

        private void createTextures() {
            var device = com.mojang.blaze3d.systems.RenderSystem.getDevice();

            final var colorTexture = device.createTexture(
                    "lumin-rt-color",
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC,
                    GpuFormat.RGBA8_UNORM,
                    width, height, 1, 1
            );
            final var colorView = device.createTextureView(colorTexture);

            depthTexture = device.createTexture(
                    "lumin-rt-depth",
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC,
                    GpuFormat.D32_FLOAT,
                    width, height, 1, 1
            );
            depthView = device.createTextureView(depthTexture);

            final var sampler = com.mojang.blaze3d.systems.RenderSystem.getDevice().createSampler(
                    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.NEAREST, FilterMode.NEAREST,
                    1, OptionalDouble.empty()
            );

            this.colorTexture = new LuminTexture(colorTexture, colorView, sampler);

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
            encoder.clearColorAndDepthTextures(colorTexture.getTexture(), new Vector4f(0, 0, 0, 0), depthTexture, 1.0);
            encoder.submit();
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

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public LuminTexture getColorTexture() {
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
        }
    }