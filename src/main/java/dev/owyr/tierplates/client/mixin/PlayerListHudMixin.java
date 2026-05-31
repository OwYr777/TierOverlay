package dev.owyr.tierplates.client.mixin;

import dev.owyr.tierplates.client.render.TierTextFormatter;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void tierplates$decorateTabName(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        try {
            String profileName = entry.getProfile().name();
            if (profileName == null || profileName.isBlank()) {
                return;
            }
            cir.setReturnValue(TierTextFormatter.decorateFlatName(entry.getProfile().id(), profileName, cir.getReturnValue()));
        } catch (Throwable ignored) {
            // Keep vanilla/custom tab text if a server sends an unexpected player entry.
        }
    }
}
