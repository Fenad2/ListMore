package com.listmore.mixin;

import com.listmore.feature.SingleBlockPlacement;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemMixin {
	@Inject(method = "place", at = @At("RETURN"))
	private void listmore$markSingleBlockPlacement(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (cir.getReturnValue().consumesAction()) {
			SingleBlockPlacement.markBlockPlaced();
		}
	}
}
