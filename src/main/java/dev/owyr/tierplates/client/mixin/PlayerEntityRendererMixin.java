package dev.owyr.tierplates.client.mixin;

import dev.owyr.tierplates.client.render.TierNameplateRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/entity/state/EntityRenderState;F)V", at = @At("TAIL"))
    private void tierplates$forceOwnName(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
        try {
            if (entity instanceof PlayerEntity player && state instanceof PlayerEntityRenderState playerState) {
                TierNameplateRenderer.decorateState(player, playerState);
            }
        } catch (Throwable ignored) {
            // Rendering must never fail because of the overlay.
        }
    }

    @Inject(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD")
    )
    private void tierplates$enforceNameLines(PlayerEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue,
                                             CameraRenderState camera, CallbackInfo ci) {
        try {
            TierNameplateRenderer.enforceBeforeRender(state);
        } catch (Throwable ignored) {
            // Keep the vanilla render path if a client/server combination behaves unexpectedly.
        }
    }
}
