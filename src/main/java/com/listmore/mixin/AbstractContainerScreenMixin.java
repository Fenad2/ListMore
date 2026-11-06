package com.listmore.mixin;

import com.listmore.config.ListMoreConfigs;
import com.listmore.feature.containerSignalPreview.ContainerSignalPreviewTracker;
import net.minecraft.client.Minecraft;
//#if MC >= 26.1
//$$ import net.minecraft.client.gui.GuiGraphicsExtractor;
//#else
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Shadow
    protected int leftPos;
    @Shadow
    protected int topPos;
    @Unique
    private boolean listmore$blockContainer;

    @Inject(method = "init", at = @At("TAIL"))
    private void listmore$identifyContainer(CallbackInfo ci) {
        AbstractContainerMenu menu = ((AbstractContainerScreen<?>) (Object) this).getMenu();
        this.listmore$blockContainer = ContainerSignalPreviewTracker.isBlockContainer(menu);
    }

    //#if MC >= 26.1
    //$$ @Inject(method = "extractRenderState", at = @At("TAIL"))
    //$$ private void listmore$renderSignal(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
    //#else
    @Inject(method = "render", at = @At("TAIL"))
    private void listmore$renderSignal(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
    //#endif
        if (!this.listmore$blockContainer || !ListMoreConfigs.Generic.CONTAINER_SIGNAL_PREVIEW.getBooleanValue()) {
            return;
        }
        AbstractContainerMenu menu = ((AbstractContainerScreen<?>) (Object) this).getMenu();
        MenuType<?> type = menu.getType();
        if (menu.slots.isEmpty()) {
            return;
        }
        int signal;
        if (type == MenuType.CRAFTER_3x3 && menu instanceof net.minecraft.world.inventory.CrafterMenu crafter) {
            signal = 0;
            for (int index = 0; index < 9; index++) {
                if (menu.getSlot(index).hasItem() || crafter.isSlotDisabled(index)) {
                    signal++;
                }
            }
        } else {
            signal = AbstractContainerMenu.getRedstoneSignalFromContainer(menu.getSlot(0).container);
        }

        if(signal == 0){
            return;
        }
        int x = this.leftPos;
        int y = Math.max(0, this.topPos - 10);
        //#if MC >= 26.1
        //$$ graphics.text(Minecraft.getInstance().font, Integer.toString(signal), x, y, 0xFFFFFFFF, true);
        //#else
        graphics.drawString(Minecraft.getInstance().font, Integer.toString(signal), x, y, 0xFFFFFFFF, true);
        //#endif
    }
}
