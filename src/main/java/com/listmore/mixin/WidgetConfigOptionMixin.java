package com.listmore.mixin;

import com.listmore.config.ListMoreConfigs;
import com.listmore.feature.CopyTargetIdFeature;

import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.ConfigButtonKeybind;
import fi.dy.masa.malilib.gui.interfaces.IKeybindConfigGui;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOptionBase;
import fi.dy.masa.malilib.gui.widgets.WidgetKeybindSettings;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptionsBase;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.util.StringUtils;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WidgetConfigOption.class, remap = false)
public abstract class WidgetConfigOptionMixin extends WidgetConfigOptionBase<GuiConfigsBase.ConfigOptionWrapper> {
	@Shadow
	@Final
	protected IKeybindConfigGui host;

	public WidgetConfigOptionMixin(
		int x,
		int y,
		int width,
		int height,
		WidgetListConfigOptionsBase<?, ?> parent,
		GuiConfigsBase.ConfigOptionWrapper entry,
		int listIndex
	) {
		super(x, y, width, height, parent, entry, listIndex);
	}

	@Shadow
	protected abstract void addKeybindResetButton(int x, int y, IKeybind keybind, ConfigButtonKeybind buttonHotkey);

	@Inject(method = "addHotkeyConfigElements", at = @At("HEAD"), cancellable = true)
	private void listmore$addCopyTargetIdTrigger(
		int x,
		int y,
		int configWidth,
		String configName,
		IHotkey hotkey,
		CallbackInfo ci
	) {
		if (hotkey != ListMoreConfigs.Generic.COPY_TARGET_ID) {
			return;
		}

		ButtonGeneric triggerButton = new ButtonGeneric(
			x,
			y,
			-1,
			20,
			StringUtils.translate("listmore.gui.button.trigger")
		);
		int triggerButtonWidth = triggerButton.getWidth();
		x += triggerButtonWidth + 2;
		configWidth -= triggerButtonWidth + 2 + 22;

		IKeybind keybind = hotkey.getKeybind();
		ConfigButtonKeybind keybindButton = new ConfigButtonKeybind(x, y, configWidth, 20, keybind, this.host);
		x += configWidth + 2;

		this.addWidget(new WidgetKeybindSettings(x, y, 20, 20, keybind, configName, this.parent, this.host.getDialogHandler()));
		x += 22;

		this.addButton(triggerButton, (button, mouseButton) -> CopyTargetIdFeature.copyTargetId());
		this.addButton(keybindButton, this.host.getButtonPressListener());
		this.addKeybindResetButton(x, y, keybind, keybindButton);
		ci.cancel();
	}
}
