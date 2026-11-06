package com.listmore.feature.containerSignalPreview;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;

public final class ContainerSignalPreviewTracker {
    private static BlockPos position;
    private static long interactionTime;
    private static Object world;
    private static AbstractContainerMenu openedMenu;
    private static boolean isBlockContainer;

    private ContainerSignalPreviewTracker() {
    }

    public static void rememberInteraction(BlockPos blockPos) {
        position = blockPos.immutable();
        world = Minecraft.getInstance().level;
        interactionTime = System.nanoTime();
    }

    public static boolean isBlockContainer(AbstractContainerMenu menu) {
        if (menu instanceof net.minecraft.world.inventory.InventoryMenu) {
            return false;
        }
        if (menu == openedMenu) {
            return isBlockContainer;
        }
        openedMenu = menu;
        isBlockContainer = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level != world || position == null
                || System.nanoTime() - interactionTime > 5_000_000_000L) {
            return false;
        }
        Object blockEntity = minecraft.level.getBlockEntity(position);
        if (!(blockEntity instanceof Container) || blockEntity instanceof EnderChestBlockEntity) {
            return false;
        }
        MenuType<?> type = menu.getType();
        isBlockContainer = type == MenuType.GENERIC_9x1 || type == MenuType.GENERIC_9x2
                || type == MenuType.GENERIC_9x3 || type == MenuType.GENERIC_9x4
                || type == MenuType.GENERIC_9x5 || type == MenuType.GENERIC_9x6
                || type == MenuType.HOPPER || type == MenuType.GENERIC_3x3
                || type == MenuType.SHULKER_BOX || type == MenuType.FURNACE
                || type == MenuType.BLAST_FURNACE || type == MenuType.SMOKER
                || type == MenuType.BREWING_STAND || type == MenuType.CRAFTER_3x3;
        position = null;
        return isBlockContainer;
    }

    public static void clearInteraction() {
        position = null;
    }
}
