package com.listmore.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;

public enum TntExplosionPreviewMode implements IConfigOptionListEntry {
	FULL("full", "listmore.label.tntExplosionPreviewMode.full"),
	PARTIAL("partial", "listmore.label.tntExplosionPreviewMode.partial");

	private final String configString;
	private final String translationKey;

	TntExplosionPreviewMode(String configString, String translationKey) {
		this.configString = configString;
		this.translationKey = translationKey;
	}

	@Override
	public String getStringValue() {
		return this.configString;
	}

	@Override
	public String getDisplayName() {
		return StringUtils.translate(this.translationKey);
	}

	@Override
	public IConfigOptionListEntry cycle(boolean forward) {
		int index = (this.ordinal() + (forward ? 1 : values().length - 1)) % values().length;
		return values()[index];
	}

	@Override
	public TntExplosionPreviewMode fromString(String value) {
		for (TntExplosionPreviewMode mode : values()) {
			if (mode.configString.equalsIgnoreCase(value)) {
				return mode;
			}
		}
		return FULL;
	}
}
