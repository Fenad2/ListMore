package com.listmore.mixin;

import com.listmore.feature.SingleBlockMining;
import com.listmore.render.EntityOutlineRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Shadow
	public HitResult hitResult;

	@Inject(method = "startAttack", at = @At("HEAD"))
	private void listmore$resetSingleBlockMining(CallbackInfoReturnable<Boolean> cir) {
		SingleBlockMining.resetAttackCycle();
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void listmore$stopAfterSingleBlockMining(boolean leftClick, CallbackInfo ci) {
		if (!leftClick) {
			SingleBlockMining.resetAttackCycle();
			return;
		}

		if (!SingleBlockMining.isAttackCycleConsumed()) {
			return;
		}

		if (this.hitResult != null && this.hitResult.getType() == HitResult.Type.BLOCK) {
			((Minecraft) (Object) this).gameMode.stopDestroyBlock();
			ci.cancel();
		}
	}

	@Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
	private void listmore$entityHighlightOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue() && EntityOutlineRenderer.shouldRender(entity)) {
			cir.setReturnValue(true);
		}
	}
}
