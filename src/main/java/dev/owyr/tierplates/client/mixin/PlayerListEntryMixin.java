package dev.owyr.tierplates.client.mixin;

import dev.owyr.tierplates.client.render.TierTextFormatter;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListEntry.class)
public abstract class PlayerListEntryMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void tierplates$decorateDisplayName(CallbackInfoReturnable<Text> cir) {
        PlayerListEntry entry = (PlayerListEntry) (Object) this;
        Text name = cir.getReturnValue();
        if (name == null) {
            name = Text.literal(entry.getProfile().name());
        }
        cir.setReturnValue(TierTextFormatter.decorateFlatName(entry.getProfile().id(), entry.getProfile().name(), name));
    }
}
