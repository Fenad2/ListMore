package com.listmore.mixin;

import com.listmore.feature.SingleBlockMining;
import com.listmore.feature.SingleBlockPlacement;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
	@Unique
	private boolean listmore$skipUseItemOnPacket;

	@Inject(method = "useItemOn", at = @At("HEAD"))
	private void listmore$beginSingleBlockPlacement(LocalPlayer player, InteractionHand hand,
			BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
		SingleBlockPlacement.beginBlockInteraction();
	}

	@Inject(method = "useItemOn", at = @At("RETURN"))
	private void listmore$endSingleBlockPlacement(LocalPlayer player, InteractionHand hand,
			BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
		SingleBlockPlacement.endBlockInteraction();
	}

	@Inject(method = "performUseItemOn", at = @At(value = "INVOKE", target =
			"Lnet/minecraft/world/item/ItemStack;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"),
			cancellable = true)
	private void listmore$stopAdditionalBlockPlacement(LocalPlayer player, InteractionHand hand,
			BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
		ItemStack stack = player.getItemInHand(hand);
		if (SingleBlockPlacement.isUseConsumed() && stack.getItem() instanceof BlockItem) {
			this.listmore$skipUseItemOnPacket = true;
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}

	@Redirect(method = "startPrediction", at = @At(value = "INVOKE", target =
			"Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
	private void listmore$skipBlockedUseItemOnPacket(ClientPacketListener connection, Packet<?> packet) {
		if (this.listmore$skipUseItemOnPacket) {
			this.listmore$skipUseItemOnPacket = false;
			return;
		}

		connection.send(packet);
	}

	@Inject(method = "destroyBlock", at = @At("RETURN"))
	private void listmore$markSingleBlockMining(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			SingleBlockMining.markBlockDestroyed();
		}
	}
}
