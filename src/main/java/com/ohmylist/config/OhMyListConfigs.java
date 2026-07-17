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
import fi.dy.masa.malilib.config.options.ConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigColor;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.interfaces.IValueChangeCallback;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;

public class OhMyListConfigs implements IConfigHandler {
	private static final String CONFIG_FILE_NAME = OhMyList.MOD_ID + ".json";
	private static final String GENERIC_KEY = OhMyList.MOD_ID + ".config.generic";
	private static final OhMyListConfigs INSTANCE = new OhMyListConfigs();

	public static class Generic {
		public static final ConfigHotkey COPY_TARGET_ID = createCopyTargetId();
		public static final ConfigBoolean ENTITY_HIGHLIGHT_OUTLINE = createEntityHighlightOutline();
		public static final ConfigStringList ENTITY_HIGHLIGHT_OUTLINE_LIST = createEntityHighlightOutlineList();
		public static final ConfigColor ENTITY_HIGHLIGHT_OUTLINE_COLOR = createEntityHighlightOutlineColor();
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
			ENTITY_HIGHLIGHT_OUTLINE,
			ENTITY_HIGHLIGHT_OUTLINE_LIST,
			ENTITY_HIGHLIGHT_OUTLINE_COLOR,
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
		return createConfig(new ConfigHotkey("copyTargetId", ""));
	}

	private static ConfigBoolean createEntityHighlightOutline() {
		return createConfig(new ConfigBoolean("entityHighlightOutline", false));
	}

	private static ConfigStringList createEntityHighlightOutlineList() {
		return createConfig(new ConfigStringList("entityHighlightOutlineList", ImmutableList.of()), value -> {
			EntityOutlineRenderer.refreshSelectedEntityTypes(value.getStrings());
			INSTANCE.save();
		});
	}

	private static ConfigColor createEntityHighlightOutlineColor() {
		return createConfig(new ConfigColor("entityHighlightOutlineColor", "#FFFFFFFF"));
	}

	private static ConfigBoolean createEntityRenderingBlacklist() {
		return createConfig(new ConfigBoolean("entityRenderingBlacklist", false));
	}

	private static ConfigStringList createEntityRenderingBlacklistList() {
		return createConfig(new ConfigStringList("entityRenderingBlacklistList", ImmutableList.of()), value -> {
			EntityRenderBlacklist.refreshBlockedEntityTypes(value.getStrings());
			INSTANCE.save();
		});
	}

	private static ConfigInteger createEntityRenderingBlacklistRange() {
		return createConfig(new ConfigInteger("entityRenderingBlacklistRange", 0, 0, 256));
	}

	private static ConfigBoolean createFurnaceAshAssistant() {
		return createConfig(new ConfigBoolean("furnaceAshAssistant", false));
	}

	private static ConfigInteger createFurnaceAshAssistantRange() {
		return createConfig(new ConfigInteger("furnaceAshAssistantRange", 1, 0, 8));
	}

	private static ConfigBoolean createPlayerTracer() {
		return createConfig(new ConfigBoolean("playerTracer", false));
	}

	private static ConfigColor createPlayerTracerColor() {
		return createConfig(new ConfigColor("playerTracerColor", "#FF55FFFF"));
	}

	private static ConfigBoolean createProjectileLandingPrediction() {
		return createConfig(new ConfigBoolean("projectileLandingPrediction", false));
	}

	private static <T extends ConfigBase<T>> T createConfig(T config) {
		return createConfig(config, value -> INSTANCE.save());
	}

	private static <T extends ConfigBase<T>> T createConfig(T config, IValueChangeCallback<T> callback) {
		config.apply(GENERIC_KEY);
		config.setValueChangeCallback(callback);
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
