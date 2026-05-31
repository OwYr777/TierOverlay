package dev.owyr.tierplates.client.mixin;

import dev.owyr.tierplates.client.render.DuplicateServerTagSuppressor;
import net.minecraft.client.render.entity.ArmorStandEntityRenderer;
import net.minecraft.client.render.entity.state.ArmorStandEntityRenderState;
import net.minecraft.entity.decoration.ArmorStandEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorStandEntityRenderer.class)
public abstract class ArmorStandEntityRendererMixin {
    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/decoration/ArmorStandEntity;Lnet/minecraft/client/render/entity/state/ArmorStandEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void tierplates$hideDuplicateServerNameTags(ArmorStandEntity entity, ArmorStandEntityRenderState state,
                                                        float tickDelta, CallbackInfo ci) {
        try {
            if ((state.invisible || state.marker) && !state.showArms && state.displayName != null
                    && DuplicateServerTagSuppressor.shouldHide(state.x, state.y, state.z, state.displayName)) {
                state.displayName = null;
                state.nameLabelPos = null;
            }
        } catch (Throwable ignored) {
            // Never let a server-specific nametag setup break entity rendering.
        }
    }
}
