package com.ohmylist.mixin;

import com.ohmylist.OhMyListConfigs;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
	private void ohmylist$playerXray(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue()) {
			return;
		}
		if (!(entity instanceof Player)) {
			return;
		}

		Minecraft client = (Minecraft) (Object) this;
		if (client.player == null || client.level == null) {
			return;
		}
		if (entity == client.player) {
			return;
		}
		if (!OhMyListConfigs.Generic.PLAYER_XRAY.getBooleanValue()) {
			return;
		}

		cir.setReturnValue(true);
	}
}
