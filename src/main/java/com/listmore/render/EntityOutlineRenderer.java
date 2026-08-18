package com.listmore.render;

import com.listmore.config.ListEntryToggleConfig;
import com.listmore.config.ListMoreConfigs;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

public final class EntityOutlineRenderer {
	private EntityOutlineRenderer() {
	}

	public static boolean shouldRender(Entity entity) {
		if (entity == null || entity.isRemoved()) {
			return false;
		}
		if (!ListMoreConfigs.Generic.ENTITY_HIGHLIGHT_OUTLINE_ENABLED.getBooleanValue()) {
			return false;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || entity == client.player) {
			return false;
		}

		String entityId = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
		return ListEntryToggleConfig.isEnabled(ListMoreConfigs.Generic.ENTITY_HIGHLIGHT_OUTLINE_LIST, entityId);
	}

	public static int getOutlineColorRgb() {
		return ListMoreConfigs.Generic.ENTITY_HIGHLIGHT_OUTLINE_COLOR.getIntegerValue() & 0x00FFFFFF;
	}
}
