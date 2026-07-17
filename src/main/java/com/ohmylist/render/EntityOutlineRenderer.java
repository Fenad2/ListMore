package com.ohmylist.render;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ohmylist.config.OhMyListConfigs;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

public final class EntityOutlineRenderer {
	private static Set<Identifier> selectedEntityIds = Set.of();

	private EntityOutlineRenderer() {
	}

	public static boolean shouldRender(Entity entity) {
		if (entity == null || entity.isRemoved()) {
			return false;
		}
		if (!OhMyListConfigs.Generic.ENTITY_HIGHLIGHT_OUTLINE.getBooleanValue()) {
			return false;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || entity == client.player) {
			return false;
		}

		Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		return entityId != null && selectedEntityIds.contains(entityId);
	}

	public static int getOutlineColorRgb() {
		return OhMyListConfigs.Generic.ENTITY_HIGHLIGHT_OUTLINE_COLOR.getIntegerValue() & 0x00FFFFFF;
	}

	public static void refreshSelectedEntityTypes() {
		refreshSelectedEntityTypes(OhMyListConfigs.Generic.ENTITY_HIGHLIGHT_OUTLINE_LIST.getStrings());
	}

	public static void refreshSelectedEntityTypes(List<String> entries) {
		Set<Identifier> entityIds = new HashSet<>();

		for (String entry : entries) {
			Identifier entityId = parseEntityId(entry);
			if (entityId != null) {
				entityIds.add(entityId);
			}
		}

		selectedEntityIds = Set.copyOf(entityIds);
	}

	private static Identifier parseEntityId(String entry) {
		if (entry == null) {
			return null;
		}

		String entityIdText = entry.trim();
		if (entityIdText.isEmpty()) {
			return null;
		}
		if (!entityIdText.contains(":")) {
			entityIdText = "minecraft:" + entityIdText;
		}

		Identifier entityId = Identifier.tryParse(entityIdText);
		return entityId != null && BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).isPresent() ? entityId : null;
	}
}
