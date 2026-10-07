//#if MC < 1.21.11
package com.listmore.mixin.accessor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccessor {
	@Accessor("scissorStack")
	Object listmore$getScissorStack();

	@Accessor("guiRenderState")
	GuiRenderState listmore$getGuiRenderState();
}
//#endif
