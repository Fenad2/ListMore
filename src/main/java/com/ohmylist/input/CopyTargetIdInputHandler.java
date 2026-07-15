package com.ohmylist.input;

import com.google.common.collect.ImmutableList;
import com.ohmylist.config.OhMyListConfigs;
import com.ohmylist.feature.CopyTargetIdFeature;

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
		OhMyListConfigs.Generic.COPY_TARGET_ID.getKeybind().setCallback((action, keybind) -> {
			CopyTargetIdFeature.copyTargetId();
			return true;
		});
	}

	@Override
	public void addKeysToMap(IKeybindManager manager) {
		manager.addKeybindToMap(OhMyListConfigs.Generic.COPY_TARGET_ID.getKeybind());
	}

	@Override
	public void addHotkeys(IKeybindManager manager) {
		manager.addHotkeysForCategory(
			"Oh My List",
			"ohmylist.hotkeys.category.generic",
			ImmutableList.of(OhMyListConfigs.Generic.COPY_TARGET_ID)
		);
	}
}
