package com.ohmylist.mixin;

import com.ohmylist.render.EntityOutlineRenderer;

import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
	@Inject(method = "getTeamColor", at = @At("RETURN"), cancellable = true)
	private void ohmylist$entityRenderingColor(CallbackInfoReturnable<Integer> cir) {
		Entity entity = (Entity) (Object) this;
		if (EntityOutlineRenderer.shouldRender(entity)) {
			cir.setReturnValue(EntityOutlineRenderer.getOutlineColorRgb());
		}
	}
}
