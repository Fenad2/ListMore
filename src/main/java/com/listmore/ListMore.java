package com.listmore;

import com.listmore.config.ListMoreConfigGui;
import com.listmore.config.ListMoreConfigs;
import com.listmore.input.CopyTargetIdInputHandler;
import com.listmore.render.EntityOutlineRenderer;
import com.listmore.render.EntityRenderBlacklist;
import com.listmore.render.FurnaceAshAssistantRenderer;
import com.listmore.render.PlayerTracerHudRenderer;
import com.listmore.render.ProjectileLandingRenderer;

import net.fabricmc.api.ClientModInitializer;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.event.RenderEventHandler;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListMore implements ClientModInitializer {
	public static final String MOD_ID = "listmore";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static boolean configHandlerRegistered;

	@Override
	public void onInitializeClient() {
		initializeConfigs();
		CopyTargetIdInputHandler.getInstance().init();
		InputEventHandler.getKeybindManager().registerKeybindProvider(CopyTargetIdInputHandler.getInstance());
		RenderEventHandler.getInstance().registerInGameGuiRenderer(PlayerTracerHudRenderer.getInstance());
		RenderEventHandler.getInstance().registerWorldLastRenderer(ProjectileLandingRenderer.getInstance());
		RenderEventHandler.getInstance().registerWorldLastRenderer(FurnaceAshAssistantRenderer.getInstance());
		Registry.CONFIG_SCREEN.registerConfigScreenFactory(new ModInfo(MOD_ID, "ListMore", ListMoreConfigGui::new));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static void initializeConfigs() {
		if (configHandlerRegistered == false) {
			ListMoreConfigs.init();
			configHandlerRegistered = true;
		}
		reloadConfigs();
	}

	public static void reloadConfigs() {
		ListMoreConfigs.loadFromFile();
		EntityOutlineRenderer.refreshSelectedEntityTypes();
		EntityRenderBlacklist.refreshBlockedEntityTypes();
	}

	public static boolean shouldRenderPlayerTracer(Entity entity) {
		if (!(entity instanceof Player)) {
			return false;
		}
		if (!ListMoreConfigs.Generic.PLAYER_TRACER.getBooleanValue()) {
			return false;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return false;
		}
		if (entity == client.player || entity.isRemoved()) {
			return false;
		}

		return client.player.distanceToSqr(entity) <= 96.0D * 96.0D;
	}

	public static int getPlayerTracerColorArgb() {
		return ListMoreConfigs.Generic.PLAYER_TRACER_COLOR.getIntegerValue();
	}
}
