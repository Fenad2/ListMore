package com.listmore.mixin.litematica;

import com.listmore.config.ListMoreConfigs;
import fi.dy.masa.litematica.gui.GuiSchematicBrowserBase;
import fi.dy.masa.litematica.gui.widgets.WidgetSchematicBrowser;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase.DirectoryEntry;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Restriction(require = @Condition("litematica"))
@Mixin(value = GuiSchematicBrowserBase.class, remap = false)
public abstract class GuiSchematicBrowserBaseMixin {
	@Shadow protected abstract ISelectionListener<DirectoryEntry> getSelectionListener();

	@Inject(method = "createListWidget(II)Lfi/dy/masa/litematica/gui/widgets/WidgetSchematicBrowser;",
			at = @At("HEAD"), cancellable = true, remap = false)
	private void listmore$createPreviewBrowser(int listX, int listY,
			CallbackInfoReturnable<WidgetSchematicBrowser> cir) {
		if (!ListMoreConfigs.Generic.SCHEMATIC_DYNAMIC_PREVIEW.getBooleanValue()) {
			return;
		}
		try {
			Class<?> browserClass = Class.forName("com.listmore.schematic.browser.ListMoreSchematicBrowser");
			Object browser = browserClass.getConstructor(int.class, int.class, int.class, int.class,
					GuiSchematicBrowserBase.class, ISelectionListener.class)
					.newInstance(listX, listY, 100, 100, (GuiSchematicBrowserBase) (Object) this,
							this.getSelectionListener());
			cir.setReturnValue((WidgetSchematicBrowser) browser);
		} catch (ReflectiveOperationException ignored) {
		}
	}
}
