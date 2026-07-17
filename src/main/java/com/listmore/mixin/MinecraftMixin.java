package com.listmore.mixin;

import com.listmore.render.EntityOutlineRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
	private void listmore$entityHighlightOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue() && EntityOutlineRenderer.shouldRender(entity)) {
			cir.setReturnValue(true);
		}
	}
}
