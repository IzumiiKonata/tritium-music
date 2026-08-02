package tritium.music.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MusicToastDisplayState;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tritium.music.client.rendering.MusicToastState;

@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void tritiumMusic$ensureNowPlayingToast(Minecraft minecraft, Options options, CallbackInfo ci) {
        ((ToastManager) (Object) this).setMusicToastDisplayState(MusicToastDisplayState.PAUSE);
    }

    @Redirect(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/MusicToastDisplayState;renderToast()Z"
            )
    )
    private boolean tritiumMusic$forceRenderToast(MusicToastDisplayState state) {
        return MusicToastState.active() || state.renderToast();
    }
}
