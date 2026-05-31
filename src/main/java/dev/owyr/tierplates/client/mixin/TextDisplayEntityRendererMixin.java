package dev.owyr.tierplates.client.mixin;

import dev.owyr.tierplates.client.render.DuplicateServerTagSuppressor;
import net.minecraft.client.render.entity.DisplayEntityRenderer;
import net.minecraft.client.render.entity.state.TextDisplayEntityRenderState;
import net.minecraft.entity.decoration.DisplayEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisplayEntityRenderer.TextDisplayEntityRenderer.class)
public abstract class TextDisplayEntityRendererMixin {
    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity;Lnet/minecraft/client/render/entity/state/TextDisplayEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void tierplates$hideDuplicateServerNameTags(DisplayEntity.TextDisplayEntity entity, TextDisplayEntityRenderState state,
                                                        float tickDelta, CallbackInfo ci) {
        try {
            if (state.data != null && DuplicateServerTagSuppressor.shouldHide(state.x, state.y, state.z, state.data.text())) {
                state.data = null;
                state.textLines = null;
                state.displayName = null;
                state.nameLabelPos = null;
            }
        } catch (Throwable ignored) {
            // Text displays are used differently per server; keep vanilla rendering on unexpected shapes.
        }
    }
}
