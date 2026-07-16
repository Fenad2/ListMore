package com.ohmylist.config;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ohmylist.OhMyList;
import com.ohmylist.render.EntityOutlineRenderer;
import com.ohmylist.render.EntityRenderBlacklist;

import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigColor;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;

public class OhMyListConfigs implements IConfigHandler {
	private static final String CONFIG_FILE_NAME = OhMyList.MOD_ID + ".json";
	private static final String GENERIC_KEY = OhMyList.MOD_ID + ".config.generic";
	private static final OhMyListConfigs INSTANCE = new OhMyListConfigs();

	public static class Generic {
		public static final ConfigHotkey COPY_TARGET_ID = createCopyTargetId();
		public static final ConfigBoolean ENTITY_RENDERING = createEntityRendering();
		public static final ConfigStringList ENTITY_RENDERING_LIST = createEntityRenderingList();
		public static final ConfigColor ENTITY_RENDERING_COLOR = createEntityRenderingColor();
		public static final ConfigBoolean ENTITY_RENDERING_BLACKLIST = createEntityRenderingBlacklist();
		public static final ConfigStringList ENTITY_RENDERING_BLACKLIST_LIST = createEntityRenderingBlacklistList();
		public static final ConfigInteger ENTITY_RENDERING_BLACKLIST_RANGE = createEntityRenderingBlacklistRange();
		public static final ConfigBoolean FURNACE_ASH_ASSISTANT = createFurnaceAshAssistant();
		public static final ConfigInteger FURNACE_ASH_ASSISTANT_RANGE = createFurnaceAshAssistantRange();
		public static final ConfigBoolean PLAYER_TRACER = createPlayerTracer();
		public static final ConfigColor PLAYER_TRACER_COLOR = createPlayerTracerColor();
		public static final ConfigBoolean PROJECTILE_LANDING_PREDICTION = createProjectileLandingPrediction();

		public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
			COPY_TARGET_ID,
			ENTITY_RENDERING,
			ENTITY_RENDERING_LIST,
			ENTITY_RENDERING_COLOR,
			ENTITY_RENDERING_BLACKLIST,
			ENTITY_RENDERING_BLACKLIST_LIST,
			ENTITY_RENDERING_BLACKLIST_RANGE,
			FURNACE_ASH_ASSISTANT,
			FURNACE_ASH_ASSISTANT_RANGE,
			PLAYER_TRACER,
			PLAYER_TRACER_COLOR,
			PROJECTILE_LANDING_PREDICTION
		);
	}

	private static ConfigHotkey createCopyTargetId() {
		ConfigHotkey config = new ConfigHotkey("copyTargetId", "").apply(GENERIC_KEY);
		config.setValueChangeCallback(value -> INSTANCE.save());
		return config;
	}

	private static ConfigBoolean createEntityRendering() {
		ConfigBoolean config = new ConfigBoolean("entityRendering", false).apply(GENERIC_KEY);
		config.setValueChangeCallback(value -> INSTANCE.save());
		return config;
	}

	private static ConfigStringList createEntityRenderingList() {
		ConfigStringList config = new ConfigStringList(
			"entityRenderingList",
			ImmutableList.of("player")
		).apply(GENERIC_KEY);
		config.setValueChangeCallback(value -> {
			EntityOutlineRenderer.refreshSelectedEntityTypes(value.getStrings());
			INSTANCE.save();
		});
		return config;
	}

	private static ConfigColor createEntityRenderingColor() {
		ConfigColor config = new ConfigColor("entityRenderingColor", "#FFFFFFFF").apply(GENERIC_KEY);
		config.setValueChangeCallback(value -> INSTANCE.save());
		return config;
	}

	private static ConfigBoolean createEntityRenderingBlacklist() {
		ConfigBoolean config = new ConfigBoolean("entityRenderingBlacklist", false).apply(GENERIC_KEY);
		config.setValueChangeCallback(value -> INSTANCE.save());
		return config;
	}

	private static ConfigStringList createEntityRenderingBlacklistList() {
		ConfigStringList config = new ConfigStringList("entityRenderingBlacklistList", ImmutableList.of()).apply(GENERIC_KEY);
		config.setValueChangeCallback(value -> {
			EntityRenderBlacklist.refreshBlockedEntityTypes(value.getStrings());
			INSTANCE.save();
		});
		return config;
	}

	private static ConfigInteger createEntityRenderingBlacklistRange() {
		ConfigInteger config = new ConfigInteger("entityRenderingBlacklistRange", 0, 0, 256).apply(GENERIC_KEY);
		config.setValueChangeCallback(value -> INSTANCE.save());
		return config;
	}

	private static ConfigBoolean createFurnaceAshAssistant() {
		ConfigBoolean config = new ConfigBoolean("furnaceAshAssistant", false).apply(GENERIC_KEY);
		config.setValueChangeCallback(value -> INSTANCE.save());
		return config;
	}

	private static ConfigInteger createFurnaceAshAssistantRange() {
		ConfigInteger config = new ConfigInteger("furnaceAshAssistantRange", 1, 0, 8).apply(GENERIC_KEY);
		config.setValueChangeCallback(value -> INSTANCE.save());
		return config;
	}

	private static ConfigBoolean createPlayerTracer() {
		ConfigBoolean config = new ConfigBoolean("playerTracer", false).apply(GENERIC_KEY);
		config.setValueChangeCallback(value -> INSTANCE.save());
		return config;
	}

	private static ConfigColor createPlayerTracerColor() {
		ConfigColor config = new ConfigColor("playerTracerColor", "#FF55FFFF").apply(GENERIC_KEY);
		config.setValueChangeCallback(value -> INSTANCE.save());
		return config;
	}

	private static ConfigBoolean createProjectileLandingPrediction() {
		ConfigBoolean config = new ConfigBoolean("projectileLandingPrediction", false).apply(GENERIC_KEY);
		config.setValueChangeCallback(value -> INSTANCE.save());
		return config;
	}

	public static void init() {
		ConfigManager.getInstance().registerConfigHandler(OhMyList.MOD_ID, INSTANCE);
	}

	public static void loadFromFile() {
		Path configFile = FileUtils.getConfigDirectory().resolve(CONFIG_FILE_NAME);

		if (!Files.exists(configFile) || !Files.isReadable(configFile)) {
			return;
		}

		JsonElement element = JsonUtils.parseJsonFile(configFile);
		if (element != null && element.isJsonObject()) {
			JsonObject root = element.getAsJsonObject();
			ConfigUtils.readConfigBase(root, "Generic", Generic.OPTIONS);
		}
	}

	public static void saveToFile() {
		Path dir = FileUtils.getConfigDirectory();
		if (!Files.exists(dir)) {
			FileUtils.createDirectoriesIfMissing(dir);
		}

		if (!Files.isDirectory(dir)) {
			return;
		}

		JsonObject root = new JsonObject();
		ConfigUtils.writeConfigBase(root, "Generic", Generic.OPTIONS);
		JsonUtils.writeJsonToFile(root, dir.resolve(CONFIG_FILE_NAME));
	}

	@Override
	public void load() {
		loadFromFile();
	}

	@Override
	public void save() {
		saveToFile();
	}
}
