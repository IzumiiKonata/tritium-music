package tritium.music.forge;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.screens.ncm.NCMScreen;
import tritium.music.core.CloudMusic;

/**
 * @author IzumiiKonata
 * Date: 2026/9/30 16:01
 */
@Mod.EventBusSubscriber(modid = TritiumMusicForge.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class TMForgeEventsListener {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            return;
        }

        if (TritiumMusicForge.OPEN_NCM_SCREEN.consumeClick()) {
            NCMScreen.open();
        }
    }

    @SubscribeEvent
    public static void onGameShuttingDown(GameShuttingDownEvent event) {
        CloudMusic.shutdownPlayback();
        CloudMusic.onStop();
        FontManager.dispose();
    }

}
