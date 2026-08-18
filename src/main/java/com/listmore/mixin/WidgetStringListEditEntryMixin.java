package com.listmore.mixin;

import java.util.List;

import com.listmore.config.ListEntryToggleConfig;
import com.listmore.config.ListMoreConfigs;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOptionBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListStringListEdit;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import fi.dy.masa.malilib.util.StringUtils;

@Mixin(targets = "fi.dy.masa.malilib.gui.widgets.WidgetStringListEditEntry", remap = false)
public abstract class WidgetStringListEditEntryMixin extends WidgetConfigOptionBase<String> {
	private static final int TOGGLE_BUTTON_WIDTH = 40;

	@Shadow
	@Final
	protected WidgetListStringListEdit parent;

	@Shadow
	@Final
	protected int listIndex;

	public WidgetStringListEditEntryMixin(
		int x,
		int y,
		int width,
		int height,
		int listIndex,
		boolean isOdd,
		String initialValue,
		String defaultValue,
		WidgetListStringListEdit parent
	) {
		super(x, y, width, height, parent, initialValue, listIndex);
	}

	@Shadow
	public abstract void applyNewValueToConfig();

	@ModifyConstant(method = "<init>", constant = @Constant(intValue = 160))
	private int listmore$makeRoomForToggleButton(int original) {
		return ListEntryToggleConfig.isRegistered(this.parent.getConfig())
			? original + TOGGLE_BUTTON_WIDTH + 2
			: original;
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	private void listmore$addEntryToggleButton(
		int x,
		int y,
		int width,
		int height,
		int listIndex,
		boolean isOdd,
		String initialValue,
		String defaultValue,
		WidgetListStringListEdit parent,
		CallbackInfo ci
	) {
		if (this.listIndex < 0 || !ListEntryToggleConfig.isRegistered(this.parent.getConfig())) {
			return;
		}

		ButtonGeneric button = new ButtonGeneric(
			x + width - TOGGLE_BUTTON_WIDTH,
			y + 1,
			TOGGLE_BUTTON_WIDTH,
			20,
			this.getToggleText()
		);
		this.addButton(button, (clicked, mouseButton) -> {
			this.applyNewValueToConfig();
			List<String> entries = this.parent.getConfig().getStrings();
			if (this.listIndex >= 0 && this.listIndex < entries.size()) {
				String entry = entries.get(this.listIndex);
				ListEntryToggleConfig.toggle(this.parent.getConfig(), entry);
				clicked.setDisplayString(this.getToggleText(entry));
				ListMoreConfigs.saveToFile();
			}
		});
	}

	private String getToggleText() {
		List<String> entries = this.parent.getConfig().getStrings();
		return this.listIndex >= 0 && this.listIndex < entries.size()
			? this.getToggleText(entries.get(this.listIndex))
			: GuiBase.TXT_GREEN + StringUtils.translate("listmore.gui.button.yes") + GuiBase.TXT_RST;
	}

	private String getToggleText(String entry) {
		boolean enabled = ListEntryToggleConfig.getStateOrDefault(this.parent.getConfig(), entry);
		String color = enabled ? GuiBase.TXT_GREEN : GuiBase.TXT_RED;
		String key = enabled ? "listmore.gui.button.yes" : "listmore.gui.button.no";
		return color + StringUtils.translate(key) + GuiBase.TXT_RST;
	}
}
