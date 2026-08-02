package tritium.music.mixin;

import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tritium.music.client.rendering.shader.PostEffectRenderer;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {

    @Inject(method = "draw", at = @At("TAIL"))
    private void tritiumMusic$renderPostEffects(CallbackInfo ci) {
        PostEffectRenderer.render();
    }
}
