package com.ohmylist.mixin;

import com.ohmylist.render.EntityOutlineRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
	private void ohmylist$entityHighlightOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue() && EntityOutlineRenderer.shouldRender(entity)) {
			cir.setReturnValue(true);
		}
	}
}
