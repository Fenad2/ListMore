package com.listmore.mixin;

import com.listmore.feature.placementAssist.PlacementAssistHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.HitResult;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Minecraft.class, GameRenderer.class})
public class GameRendererMixin {
    @Inject(method = "pick(F)V", at = @At("RETURN"), require = 0)
    private void listmore$applyPlacementAssist(float partialTick, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        HitResult hitResult = client.hitResult;
        if (hitResult != null) {
            client.hitResult = PlacementAssistHandler.adjustHitResult(client, hitResult);
        }
    }
}
