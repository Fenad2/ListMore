package com.listmore.config;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import fi.dy.masa.malilib.config.IConfigStringList;

// 存储已注册字符串列表内各项的启用状态
public final class ListEntryToggleConfig {
	private static final Map<IConfigStringList, EntryStates> REGISTERED_LISTS = new IdentityHashMap<>();

	private ListEntryToggleConfig() {
	}

	public static void register(IConfigStringList config, String jsonKey) {
		REGISTERED_LISTS.put(config, new EntryStates(jsonKey));
	}

	public static boolean isRegistered(IConfigStringList config) {
		return REGISTERED_LISTS.containsKey(config);
	}

	public static boolean isEnabled(IConfigStringList config, String entry) {
		EntryStates states = REGISTERED_LISTS.get(config);
		return states != null && states.isEnabled(entry);
	}

	public static boolean getStateOrDefault(IConfigStringList config, String entry) {
		EntryStates states = REGISTERED_LISTS.get(config);
		return states != null && states.getStateOrDefault(entry);
	}

	public static void toggle(IConfigStringList config, String entry) {
		EntryStates states = REGISTERED_LISTS.get(config);
		if (states != null) {
			states.toggle(entry);
		}
	}

	public static void refresh(IConfigStringList config) {
		EntryStates states = REGISTERED_LISTS.get(config);
		if (states != null) {
			states.refresh(config.getStrings());
		}
	}

	public static void refreshAll() {
		for (Map.Entry<IConfigStringList, EntryStates> entry : REGISTERED_LISTS.entrySet()) {
			entry.getValue().refresh(entry.getKey().getStrings());
		}
	}

	public static void read(JsonObject generic) {
		for (EntryStates states : REGISTERED_LISTS.values()) {
			states.read(generic);
		}
	}

	public static void write(JsonObject generic) {
		for (EntryStates states : REGISTERED_LISTS.values()) {
			states.write(generic);
		}
	}

	private static final class EntryStates {
		private final String jsonKey;
		private final Map<String, Boolean> values = new LinkedHashMap<>();

		private EntryStates(String jsonKey) {
			this.jsonKey = jsonKey;
		}

		private boolean isEnabled(String entry) {
			return Boolean.TRUE.equals(values.get(normalize(entry)));
		}

		private boolean getStateOrDefault(String entry) {
			return values.getOrDefault(normalize(entry), true);
		}

		private void toggle(String entry) {
			String normalized = normalize(entry);
			if (!normalized.isEmpty()) {
				values.put(normalized, !getStateOrDefault(normalized));
			}
		}

		private void refresh(List<String> entries) {
			Map<String, Boolean> refreshed = new LinkedHashMap<>();
			for (String entry : entries) {
				String normalized = normalize(entry);
				if (!normalized.isEmpty()) {
					refreshed.putIfAbsent(normalized, values.getOrDefault(normalized, true));
				}
			}

			values.clear();
			values.putAll(refreshed);
		}

		private void read(JsonObject generic) {
			values.clear();
			JsonElement element = generic.get(this.jsonKey);
			if (element == null || !element.isJsonObject()) {
				return;
			}

			for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
				JsonElement value = entry.getValue();
				String normalized = normalize(entry.getKey());
				if (!normalized.isEmpty() && value.isJsonPrimitive()
					&& value.getAsJsonPrimitive().isBoolean()) {
					values.put(normalized, value.getAsBoolean());
				}
			}
		}

		private void write(JsonObject generic) {
			JsonObject stateObject = new JsonObject();
			for (Map.Entry<String, Boolean> entry : values.entrySet()) {
				stateObject.addProperty(entry.getKey(), entry.getValue());
			}
			generic.add(this.jsonKey, stateObject);
		}

		private static String normalize(String entry) {
			return entry == null ? "" : entry.trim().toLowerCase(Locale.ROOT);
		}
	}
}
