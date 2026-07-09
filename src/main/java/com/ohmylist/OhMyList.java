package com.ohmylist;

import net.fabricmc.api.ClientModInitializer;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OhMyList implements ClientModInitializer {
	public static final String MOD_ID = "ohmylist";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static boolean configHandlerRegistered;

	@Override
	public void onInitializeClient() {
		initializeConfigs();
		LOGGER.info("OhMyList initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static void initializeConfigs() {
		if (configHandlerRegistered == false) {
			OhMyListConfigs.init();
			configHandlerRegistered = true;
		}

		reloadConfigs();
	}

	public static void reloadConfigs() {
		OhMyListConfigs.loadFromFile();
	}

	public static boolean shouldApplyPlayerXray(Entity entity) {
		if (!(entity instanceof Player)) {
			return false;
		}
		if (!OhMyListConfigs.Generic.PLAYER_XRAY.getBooleanValue()) {
			return false;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return false;
		}

		return entity != client.player;
	}

	public static int getPlayerXrayColorRgb() {
		return OhMyListConfigs.Generic.PLAYER_XRAY_COLOR.getIntegerValue() & 0x00FFFFFF;
	}
}
