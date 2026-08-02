package tritium.music.client.rendering.shader;

public class Shaders {

    public static final GaussianBlurShader BLUR_SHADER = new GaussianBlurShader();
    public static final GaussianBlurShader GAUSSIAN_BLUR_SHADER = new GaussianBlurShader();
    public static final BloomShader BLOOM_SHADER = new BloomShader();
    public static final BloomShader UI_BLOOM_SHADER = new BloomShader();
    public static final UIBackdropBlur UI_BLUR = new UIBackdropBlur();
    public static final StencilShader STENCIL = new StencilShader();
    public static final VFFadeoutShader VF_FADEOUT = new VFFadeoutShader();
}
