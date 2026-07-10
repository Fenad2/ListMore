package com.ohmylist;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigColor;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;

public class OhMyListConfigs implements IConfigHandler {
	private static final String CONFIG_FILE_NAME = OhMyList.MOD_ID + ".json";
	private static final String GENERIC_KEY = OhMyList.MOD_ID + ".config.generic";
	private static final OhMyListConfigs INSTANCE = new OhMyListConfigs();

	public static class Generic {
		public static final ConfigBoolean PLAYER_XRAY = createPlayerXray();
		public static final ConfigColor PLAYER_XRAY_COLOR = createPlayerXrayColor();
		public static final ConfigBoolean PLAYER_TRACER = createPlayerTracer();
		public static final ConfigColor PLAYER_TRACER_COLOR = createPlayerTracerColor();
		public static final ConfigBoolean PROJECTILE_LANDING_PREDICTION = createProjectileLandingPrediction();

		public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
			PLAYER_XRAY,
			PLAYER_XRAY_COLOR,
			PLAYER_TRACER,
			PLAYER_TRACER_COLOR,
			PROJECTILE_LANDING_PREDICTION
		);
	}

	private static ConfigBoolean createPlayerXray() {
		ConfigBoolean config = new ConfigBoolean("playerXray", false).apply(GENERIC_KEY);
		config.setValueChangeCallback(value -> INSTANCE.save());
		return config;
	}

	private static ConfigColor createPlayerXrayColor() {
		ConfigColor config = new ConfigColor("playerXrayColor", "#FFFFFFFF").apply(GENERIC_KEY);
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
