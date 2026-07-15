package com.ohmylist.mixin;

import java.util.List;

import com.ohmylist.config.OhMyListConfigGui;

import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptionsBase;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WidgetListConfigOptions.class, remap = false)
public abstract class WidgetListConfigOptionsMixin extends WidgetListConfigOptionsBase<GuiConfigsBase.ConfigOptionWrapper, WidgetConfigOption> {
	@Shadow
	@Final
	protected GuiConfigsBase parent;

	public WidgetListConfigOptionsMixin(int x, int y, int width, int height, int configWidth) {
		super(x, y, width, height, configWidth);
	}

	@Inject(method = "getMaxNameLengthWrapped", at = @At("RETURN"), cancellable = true)
	private void ohmylist$fixLabelWidth(List<GuiConfigsBase.ConfigOptionWrapper> wrappers, CallbackInfoReturnable<Integer> cir) {
		if (!(this.parent instanceof OhMyListConfigGui)) {
			return;
		}

		int desiredLabelWidth = 150;
		int availableLabelWidth = Math.max(0, this.browserEntryWidth - this.configWidth - 60);
		int minimumLabelWidth = Math.min(desiredLabelWidth, availableLabelWidth);
		cir.setReturnValue(Math.max(cir.getReturnValueI(), minimumLabelWidth));
	}
}
