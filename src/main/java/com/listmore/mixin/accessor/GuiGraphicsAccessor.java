//#if MC < 1.21.11
package com.listmore.mixin.accessor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccessor {
    @Accessor("scissorStack")
    // TODO: 此处在 173bfb8 从 Object 改成了 GuiGraphics.ScissorStack ，我有点记不清为什么要这么改了。这里仍需要测试
    Object listmore$getScissorStack();

    @Accessor("guiRenderState")
    GuiRenderState listmore$getGuiRenderState();
}
//#endif
