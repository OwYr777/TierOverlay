package dev.owyr.tierplates.client.mixin;

import dev.owyr.tierplates.client.render.TierTextFormatter;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Text tierplates$decorateChatMessage(Text message) {
        try {
            return TierTextFormatter.decorateChatMessage(message);
        } catch (Throwable ignored) {
            return message;
        }
    }
}
