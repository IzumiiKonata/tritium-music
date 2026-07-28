package tritium.music.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import tritium.music.client.screens.ncm.NCMScreen;
import tritium.music.client.screens.ncm.panels.HudSettingsPanel;

public class TritiumModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> NCMScreen.withPanel(new HudSettingsPanel(), parent);
    }
}
