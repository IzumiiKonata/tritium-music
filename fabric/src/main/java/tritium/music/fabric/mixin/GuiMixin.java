package tritium.music.fabric.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tritium.music.fabric.TritiumMusicMod;

@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void tritium$renderHudWidgets(GuiGraphics graphics, float partialTick, CallbackInfo callbackInfo) {
        TritiumMusicMod.renderHudWidgets(graphics, partialTick);
    }
}
