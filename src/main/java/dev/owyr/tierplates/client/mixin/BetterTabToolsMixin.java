package dev.owyr.tierplates.client.mixin;

import dev.owyr.tierplates.client.render.TierTextFormatter;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "tab.bettertab.Tools", remap = false)
public abstract class BetterTabToolsMixin {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true, require = 0)
    private static void tierplates$decorateBetterTabName(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        try {
            if (entry == null || entry.getProfile() == null || cir.getReturnValue() == null) {
                return;
            }
            cir.setReturnValue(TierTextFormatter.decorateFlatName(
                    entry.getProfile().id(),
                    entry.getProfile().name(),
                    cir.getReturnValue()
            ));
        } catch (Throwable ignored) {
            // BetterTab is optional; keep its original name if anything changes.
        }
    }
}
