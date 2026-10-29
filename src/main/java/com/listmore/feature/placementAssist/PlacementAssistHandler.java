package com.listmore.feature.placementAssist;

import com.listmore.config.ListMoreConfigs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class PlacementAssistHandler {
    private static final int MAX_EXTENSION_LENGTH = 64;
    private static final double VERTICAL_ASSIST_EDGE = 4.0D / 16.0D;

    private PlacementAssistHandler() {
    }

    public static BlockHitResult adjustHitResult(Player player, Level level, ItemStack stack, BlockHitResult hitResult) {
        PlacementOffset offset = getOffset(player, level, stack, hitResult);
        if (offset == null) {
            return hitResult;
        }

        BlockPos supportingPosition = offset.position().relative(offset.direction().getOpposite());
        Vec3 hitLocation = new Vec3(
                supportingPosition.getX() + 0.5D + offset.direction().getStepX() * 0.5D,
                supportingPosition.getY() + 0.5D + offset.direction().getStepY() * 0.5D,
                supportingPosition.getZ() + 0.5D + offset.direction().getStepZ() * 0.5D
        );
        return new BlockHitResult(hitLocation, offset.direction(), supportingPosition, false);
    }

    public static boolean canAssist(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return false;
        }

        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty()) {
            return canAssist(player, mainHand, client.level);
        }

        return canAssist(player, player.getOffhandItem(), client.level);
    }

    public static boolean canAssist(Player player, ItemStack stack, Level level) {
        return player != null && level != null && !player.isShiftKeyDown()
                && ListMoreConfigs.Generic.PLACEMENT_ASSIST.getBooleanValue() && isAssistItem(stack);
    }

    public static PlacementOffset getOffset(Minecraft client) {
        if (client.player == null || client.level == null || !canAssist(client)
                || !(client.hitResult instanceof BlockHitResult hitResult)) {
            return null;
        }

        ItemStack stack = client.player.getMainHandItem();
        if (stack.isEmpty()) {
            stack = client.player.getOffhandItem();
        }
        return getOffset(client.player, client.level, stack, hitResult);
    }

    public static PlacementOffset getOffset(Player player, Level level,
                                            ItemStack stack, BlockHitResult hitResult) {
        if (!canAssist(player, stack, level)) {
            return null;
        }

        Block block = getAssistBlock(stack);
        if (block == null) {
            return null;
        }

        BlockPos clickedPosition = hitResult.getBlockPos();
        Block clickedBlock = level.getBlockState(clickedPosition).getBlock();
        if (!isSameFamily(block, clickedBlock)) {
            return null;
        }

        Direction direction = getVerticalAssistDirection(clickedPosition, hitResult);
        if (direction == null) {
            return null;
        }
        BlockPos position = clickedPosition;
        int extensionLength = 0;
        while (extensionLength < MAX_EXTENSION_LENGTH
                && isSameFamily(block, level.getBlockState(position.relative(direction)).getBlock())) {
            BlockPos nextPosition = position.relative(direction);
            position = nextPosition;
            extensionLength++;
        }
        position = position.relative(direction);

        if (!level.getBlockState(position).canBeReplaced()) {
            return null;
        }

        return new PlacementOffset(position, direction);
    }

    private static Direction getVerticalAssistDirection(BlockPos clickedPosition, BlockHitResult hitResult) {
        double localY = hitResult.getLocation().y - clickedPosition.getY();
        if (localY <= VERTICAL_ASSIST_EDGE) {
            return Direction.DOWN;
        }
        if (localY >= 1.0D - VERTICAL_ASSIST_EDGE) {
            return Direction.UP;
        }
        return null;
    }

    public static boolean isAssistItem(ItemStack stack) {
        return getAssistBlock(stack) != null;
    }

    private static Block getAssistBlock(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }

        Block block = blockItem.getBlock();
        return block instanceof FenceBlock || block instanceof IronBarsBlock ? block : null;
    }

    private static boolean isSameFamily(Block first, Block second) {
        return first instanceof FenceBlock && second instanceof FenceBlock
                || first instanceof IronBarsBlock && second instanceof IronBarsBlock;
    }

    public record PlacementOffset(BlockPos position, Direction direction) {
    }
}
