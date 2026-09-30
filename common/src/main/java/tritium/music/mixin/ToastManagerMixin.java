package tritium.music.mixin;

import net.minecraft.client.MusicToastDisplayState;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tritium.music.client.rendering.MusicToastState;

@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {

    @Inject(method = "showNowPlayingToast", at = @At("HEAD"))
    private void tritiumMusic$trackNowPlayingToast(CallbackInfo callbackInfo) {
        MusicToastState.nowPlayingToastShown();
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/MusicToastDisplayState;renderToast()Z"
            )
    )
    private boolean tritiumMusic$forceRenderToast(MusicToastDisplayState state) {
        return MusicToastState.active() || state.renderToast();
    }
}
