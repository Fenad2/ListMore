package com.listmore.input;

import com.google.common.collect.ImmutableList;
import com.listmore.config.ListMoreConfigs;
import com.listmore.feature.CopyTargetIdFeature;

import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;

public final class CopyTargetIdInputHandler implements IKeybindProvider {
	private static final CopyTargetIdInputHandler INSTANCE = new CopyTargetIdInputHandler();

	private CopyTargetIdInputHandler() {
	}

	public static CopyTargetIdInputHandler getInstance() {
		return INSTANCE;
	}

	public void init() {
		ListMoreConfigs.Generic.COPY_TARGET_ID.getKeybind().setCallback((action, keybind) -> {
			CopyTargetIdFeature.copyTargetId();
			return true;
		});
	}

	@Override
	public void addKeysToMap(IKeybindManager manager) {
		manager.addKeybindToMap(ListMoreConfigs.Generic.COPY_TARGET_ID.getKeybind());
	}

	@Override
	public void addHotkeys(IKeybindManager manager) {
		manager.addHotkeysForCategory(
			"ListMore",
			"listmore.hotkeys.category.generic",
			ImmutableList.of(ListMoreConfigs.Generic.COPY_TARGET_ID)
		);
	}
}
