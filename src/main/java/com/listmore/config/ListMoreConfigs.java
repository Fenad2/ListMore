package com.listmore.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.listmore.ListMore;
import com.listmore.render.EntityOutlineRenderer;
import com.listmore.render.EntityRenderBlacklist;

import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigColor;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.interfaces.IValueChangeCallback;
import fi.dy.masa.malilib.util.FileUtils;
//#if MC>=260100
//$$ import fi.dy.masa.malilib.util.data.json.JsonUtils;
//#else
import fi.dy.masa.malilib.util.JsonUtils;
//#endif

public class ListMoreConfigs implements IConfigHandler {
	private static final String LEGACY_CONFIG_FILE_NAME = "ohmylist.json";
	private static final String CONFIG_FILE_NAME = ListMore.MOD_ID + ".json";
	private static final String GENERIC_KEY = ListMore.MOD_ID + ".config.generic";
	private static final ListMoreConfigs INSTANCE = new ListMoreConfigs();

	public static class Generic {
		public static final ConfigHotkey COPY_TARGET_ID = createCopyTargetId();
		public static final ConfigBoolean ENTITY_HIGHLIGHT_OUTLINE = createEntityHighlightOutline();
		public static final ConfigStringList ENTITY_HIGHLIGHT_OUTLINE_LIST = createEntityHighlightOutlineList();
		public static final ConfigColor ENTITY_HIGHLIGHT_OUTLINE_COLOR = createEntityHighlightOutlineColor();
		public static final ConfigBoolean ENTITY_RENDERING_BLACKLIST = createEntityRenderingBlacklist();
		public static final ConfigStringList ENTITY_RENDERING_BLACKLIST_LIST = createEntityRenderingBlacklistList();
		public static final ConfigInteger ENTITY_RENDERING_BLACKLIST_RANGE = createEntityRenderingBlacklistRange();
		public static final ConfigBoolean INVALID_FURNACE_INPUT_HIGHLIGHTER = createInvalidFurnaceInputHighlighter();
		public static final ConfigInteger INVALID_FURNACE_INPUT_HIGHLIGHTER_RANGE = createInvalidFurnaceInputHighlighterRange();
		public static final ConfigBoolean TNT_EXPLOSION_PREVIEW = createTntExplosionPreview();
		public static final ConfigOptionList TNT_EXPLOSION_PREVIEW_MODE = createTntExplosionPreviewMode();
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
			INVALID_FURNACE_INPUT_HIGHLIGHTER,
			INVALID_FURNACE_INPUT_HIGHLIGHTER_RANGE,
			TNT_EXPLOSION_PREVIEW,
			TNT_EXPLOSION_PREVIEW_MODE,
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

	private static ConfigBoolean createInvalidFurnaceInputHighlighter() {
		return createConfig(new ConfigBoolean("invalidFurnaceInputHighlighter", false));
	}

	private static ConfigInteger createInvalidFurnaceInputHighlighterRange() {
		return createConfig(new ConfigInteger("invalidFurnaceInputHighlighterRange", 1, 0, 8));
	}

	private static ConfigBoolean createTntExplosionPreview() {
		return createConfig(new ConfigBoolean("tntExplosionPreview", false));
	}

	private static ConfigOptionList createTntExplosionPreviewMode() {
		return createConfig(new ConfigOptionList("tntExplosionPreviewMode", TntExplosionPreviewMode.FULL));
	}

	private static ConfigBoolean createPlayerTracer() {
		return createConfig(new ConfigBoolean("playerTracer", false));
	}

	private static ConfigColor createPlayerTracerColor() {
		return createConfig(new ConfigColor("playerTracerColor", "#FFFFFFFF"));
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
		ConfigManager.getInstance().registerConfigHandler(ListMore.MOD_ID, INSTANCE);
	}

	public static void loadFromFile() {
		//#if MC>=260100
		//$$ Path configDirectory = FileUtils.getConfigDirectory();
		//#else
		Path configDirectory = FileUtils.getConfigDirectoryAsPath();
		//#endif
		migrateLegacyConfig(configDirectory);
		Path configFile = configDirectory.resolve(CONFIG_FILE_NAME);

		if (!Files.exists(configFile) || !Files.isReadable(configFile)) {
			return;
		}

		//#if MC>=260100
		//$$ JsonElement element = JsonUtils.parseJsonFile(configFile);
		//#else
		JsonElement element = JsonUtils.parseJsonFileAsPath(configFile);
		//#endif
		if (element != null && element.isJsonObject()) {
			JsonObject root = element.getAsJsonObject();
			ConfigUtils.readConfigBase(root, "Generic", Generic.OPTIONS);
		}
	}

	private static void migrateLegacyConfig(Path configDirectory) {
		Path configFile = configDirectory.resolve(CONFIG_FILE_NAME);
		Path legacyConfigFile = configDirectory.resolve(LEGACY_CONFIG_FILE_NAME);

		if (Files.exists(configFile) || !Files.isRegularFile(legacyConfigFile)) {
			return;
		}

		try {
			Files.copy(legacyConfigFile, configFile);
		} catch (IOException exception) {
			ListMore.LOGGER.warn("Failed to migrate legacy config file {}", legacyConfigFile, exception);
		}
	}

	public static void saveToFile() {
		//#if MC>=260100
		//$$ Path dir = FileUtils.getConfigDirectory();
		//#else
		Path dir = FileUtils.getConfigDirectoryAsPath();
		//#endif
		if (!Files.exists(dir)) {
			FileUtils.createDirectoriesIfMissing(dir);
		}

		if (!Files.isDirectory(dir)) {
			return;
		}

		JsonObject root = new JsonObject();
		ConfigUtils.writeConfigBase(root, "Generic", Generic.OPTIONS);
		//#if MC>=260100
		//$$ JsonUtils.writeJsonToFile(root, dir.resolve(CONFIG_FILE_NAME));
		//#else
		JsonUtils.writeJsonToFileAsPath(root, dir.resolve(CONFIG_FILE_NAME));
		//#endif
	}

	@Override
	public void load() {
		loadFromFile();
		EntityOutlineRenderer.refreshSelectedEntityTypes();
		EntityRenderBlacklist.refreshBlockedEntityTypes();
	}

	@Override
	public void save() {
		saveToFile();
	}
}
