package tritium.music.mixin;

import net.minecraft.client.gui.components.toasts.NowPlayingToast;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tritium.music.client.rendering.MusicToastState;

@Mixin(NowPlayingToast.class)
public class NowPlayingToastMixin {

    @Inject(method = "getCurrentSongName", at = @At("HEAD"), cancellable = true)
    private static void tritiumMusic$currentSong(CallbackInfoReturnable<String> cir) {
        if (MusicToastState.active()) {
            cir.setReturnValue(MusicToastState.text());
        }
    }

    @Inject(method = "getNowPlayingString", at = @At("HEAD"), cancellable = true)
    private static void tritiumMusic$nowPlayingString(String currentSongKey, CallbackInfoReturnable<Component> cir) {
        if (MusicToastState.active()) {
            cir.setReturnValue(Component.literal(MusicToastState.text()));
        }
    }
}
