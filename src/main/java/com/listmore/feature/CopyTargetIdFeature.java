package com.listmore.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
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

		String id = getTargetId(client);
		if (id == null) {
			return false;
		}

		client.keyboardHandler.setClipboard(id);
		return true;
	}

	private static String getTargetId(Minecraft client) {
		HitResult hitResult = client.hitResult;
		if (hitResult instanceof EntityHitResult entityHitResult) {
			return String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entityHitResult.getEntity().getType()));
		}
		if (hitResult instanceof BlockHitResult blockHitResult) {
			return String.valueOf(BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(blockHitResult.getBlockPos()).getBlock()));
		}

		return null;
	}
}
