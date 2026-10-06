//#if MC < 1.21.11
package com.listmore.mixin.accessor;

import net.minecraft.client.gui.navigation.ScreenRectangle;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.gui.GuiGraphics$ScissorStack")
public interface GuiGraphicsScissorStackAccessor {
	@Invoker("peek")
	ScreenRectangle listmore$peek();
}
//#endif
