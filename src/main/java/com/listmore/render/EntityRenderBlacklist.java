package com.listmore.render;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.listmore.config.ListMoreConfigs;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

public final class EntityRenderBlacklist {
	private static Set<String> blockedEntityIds = Set.of();

	private EntityRenderBlacklist() {
	}

	// 判断是否应被跳过渲染
	public static boolean shouldSkipRendering(Entity entity) {
		if (entity == null || !ListMoreConfigs.Generic.ENTITY_RENDERING_BLACKLIST.getBooleanValue()) {
			return false;
		}

		String entityId = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
		if (!blockedEntityIds.contains(entityId)) {
			return false;
		}

		int range = ListMoreConfigs.Generic.ENTITY_RENDERING_BLACKLIST_RANGE.getIntegerValue();
		if (range <= 0) {
			return true;
		}

		Minecraft client = Minecraft.getInstance();
		return client.player == null || client.player.distanceToSqr(entity) > (double) range * range;
	}

	public static void refreshBlockedEntityTypes() {
		refreshBlockedEntityTypes(ListMoreConfigs.Generic.ENTITY_RENDERING_BLACKLIST_LIST.getStrings());
	}

	// 根据填写的实体 ID 列表刷新实体类型集合
	public static void refreshBlockedEntityTypes(List<String> entries) {
		Set<String> entityIds = new HashSet<>();

		for (String entry : entries) {
			String entityId = parseEntityId(entry);
			if (entityId != null) {
				entityIds.add(entityId);
			}
		}

		blockedEntityIds = Set.copyOf(entityIds);
	}

	private static String parseEntityId(String entry) {
		if (entry == null) {
			return null;
		}

		String entityIdText = entry.trim();
		if (entityIdText.isEmpty()) {
			return null;
		}

		for (var entityType : BuiltInRegistries.ENTITY_TYPE) {
			String entityId = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entityType));
			if (entityIdText.equals(entityId)) {
				return entityId;
			}
		}

		return null;
	}
}
