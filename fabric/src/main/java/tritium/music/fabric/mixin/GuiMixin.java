package tritium.music.fabric.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tritium.music.fabric.FrameProbe;
import tritium.music.fabric.TritiumMusicMod;

@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void tritium$renderHudWidgets(GuiGraphics graphics, float partialTick, CallbackInfo callbackInfo) {
        TritiumMusicMod.renderHudWidgets(graphics, partialTick);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void tritium$restoreHudState(GuiGraphics graphics, float partialTick, CallbackInfo callbackInfo) {
        TritiumMusicMod.auditHudState();
    }

    @Inject(method = "renderVignette", at = @At("HEAD"))
    private void tritium$beforeVignette(GuiGraphics graphics, Entity entity, CallbackInfo callbackInfo) {
        FrameProbe.sample("vignette_head");
    }

    @Inject(method = "renderVignette", at = @At("RETURN"))
    private void tritium$afterVignette(GuiGraphics graphics, Entity entity, CallbackInfo callbackInfo) {
        FrameProbe.sample("vignette_return");
    }
}
