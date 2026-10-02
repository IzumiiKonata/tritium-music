package tritium.music.mixin;

import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tritium.music.client.audio.OpenALContext;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {

    @Inject(method = "reload", at = @At("RETURN"))
    private void tritiumMusic$outputDeviceChanged(CallbackInfo ci) {
        OpenALContext.invalidate();
    }
}
