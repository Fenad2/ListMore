package com.listmore.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public final class CopyTargetIdFeature {
	private CopyTargetIdFeature() {
	}

	public static boolean copyTargetId() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.hitResult == null) {
			return false;
		}

		Identifier id = getTargetId(client);
		if (id == null) {
			return false;
		}

		client.keyboardHandler.setClipboard(id.toString());
		return true;
	}

	private static Identifier getTargetId(Minecraft client) {
		HitResult hitResult = client.hitResult;
		if (hitResult instanceof EntityHitResult entityHitResult) {
			return BuiltInRegistries.ENTITY_TYPE.getKey(entityHitResult.getEntity().getType());
		}
		if (hitResult instanceof BlockHitResult blockHitResult) {
			return BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(blockHitResult.getBlockPos()).getBlock());
		}

		return null;
	}
}
