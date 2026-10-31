package com.listmore.mixin;

import com.listmore.feature.soundSourceHighlight.SoundSourceHighlightManager;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handleSoundEntityEvent", at = @At("RETURN"))
    private void listmore$trackEntitySound(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        SoundSourceHighlightManager.getInstance().onSoundEntityPacket(packet.getId(), packet.getVolume());
    }

    @Inject(method = "handleBlockEvent", at = @At("RETURN"))
    private void listmore$trackBlockSound(ClientboundBlockEventPacket packet, CallbackInfo ci) {
        SoundSourceHighlightManager.getInstance().onBlockEvent(
                packet.getPos(),
                packet.getBlock(),
                packet.getB0()
        );
    }

    @Inject(method = "handleLevelEvent", at = @At("RETURN"))
    private void listmore$trackLevelSound(ClientboundLevelEventPacket packet, CallbackInfo ci) {
        SoundSourceHighlightManager.getInstance().onLevelEvent(packet.getType(), packet.getPos());
    }
}
