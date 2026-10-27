package com.listmore.feature.placementAssist;

import com.listmore.config.ListMoreConfigs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class PlacementAssistHandler {
    private PlacementAssistHandler() {
    }

    public static HitResult adjustHitResult(Minecraft client, HitResult hitResult) {
        if (!(hitResult instanceof BlockHitResult blockHitResult) || !canAssist(client)) {
            return hitResult;
        }

        Direction direction = blockHitResult.getDirection();
        if (direction != Direction.UP && direction != Direction.DOWN) {
            return hitResult;
        }

        return new BlockHitResult(
                getFaceCenter(blockHitResult),
                direction,
                blockHitResult.getBlockPos(),
                blockHitResult.isInside()
        );
    }

    public static boolean canAssist(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null ||
                !ListMoreConfigs.Generic.PLACEMENT_ASSIST.getBooleanValue() ||
                client.options.keyShift.isDown()) {
            return false;
        }

        ItemStack mainHand = player.getMainHandItem();
        if (isAssistItem(mainHand)) {
            return true;
        }

        return mainHand.isEmpty() && isAssistItem(player.getOffhandItem());
    }

    public static boolean isAssistItem(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }

        Block block = blockItem.getBlock();
        return block instanceof FenceBlock || block instanceof IronBarsBlock;
    }

    private static Vec3 getFaceCenter(BlockHitResult hitResult) {
        BlockPos pos = hitResult.getBlockPos();
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D + hitResult.getDirection().getStepY() * 0.5D, pos.getZ() + 0.5D);
    }
}
