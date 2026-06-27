package tritium.music.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import tritium.music.client.screens.WidgetSettingsScreen;

public class TritiumModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return WidgetSettingsScreen::new;
    }
}
