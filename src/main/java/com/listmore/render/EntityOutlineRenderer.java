package com.listmore.render;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.listmore.config.ListMoreConfigs;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

public final class EntityOutlineRenderer {
	private static Set<String> selectedEntityIds = Set.of();

	private EntityOutlineRenderer() {
	}

	public static boolean shouldRender(Entity entity) {
		if (entity == null || entity.isRemoved()) {
			return false;
		}
		if (!ListMoreConfigs.Generic.ENTITY_HIGHLIGHT_OUTLINE.getBooleanValue()) {
			return false;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || entity == client.player) {
			return false;
		}

		return selectedEntityIds.contains(String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())));
	}

	public static int getOutlineColorRgb() {
		return ListMoreConfigs.Generic.ENTITY_HIGHLIGHT_OUTLINE_COLOR.getIntegerValue() & 0x00FFFFFF;
	}

	public static void refreshSelectedEntityTypes() {
		refreshSelectedEntityTypes(ListMoreConfigs.Generic.ENTITY_HIGHLIGHT_OUTLINE_LIST.getStrings());
	}

	public static void refreshSelectedEntityTypes(List<String> entries) {
		Set<String> entityIds = new HashSet<>();

		for (String entry : entries) {
			String entityId = parseEntityId(entry);
			if (entityId != null) {
				entityIds.add(entityId);
			}
		}

		selectedEntityIds = Set.copyOf(entityIds);
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
