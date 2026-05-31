package dev.owyr.tierplates.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.owyr.tierplates.client.screen.TierPlatesConfigScreen;

public final class TierPlatesModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return TierPlatesConfigScreen::new;
    }
}
