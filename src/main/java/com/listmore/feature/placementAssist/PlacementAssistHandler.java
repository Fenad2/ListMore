package com.listmore.feature.placementAssist;

import com.listmore.config.ListMoreConfigs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
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
        BlockState clickedState = level.getBlockState(clickedPosition);
        Block clickedBlock = clickedState.getBlock();
        if (!isSameFamily(block, clickedBlock)) {
            return null;
        }

        boolean chainAssist = isChain(block);
        Direction direction = chainAssist
                ? getChainAssistDirection(clickedPosition, clickedState, hitResult)
                : getVerticalAssistDirection(clickedPosition, hitResult);
        if (direction == null) {
            return null;
        }
        BlockPos position = clickedPosition;
        int extensionLength = 0;
        while (extensionLength < MAX_EXTENSION_LENGTH
                && matchesExtension(block, clickedState, level.getBlockState(position.relative(direction)))) {
            BlockPos nextPosition = position.relative(direction);
            position = nextPosition;
            extensionLength++;
        }
        position = position.relative(direction);

        if (!level.getBlockState(position).canBeReplaced()) {
            return null;
        }

        BlockPos supportingPosition = position.relative(direction.getOpposite());
        Vec3 placementHitLocation = new Vec3(
                supportingPosition.getX() + 0.5D + direction.getStepX() * 0.5D,
                supportingPosition.getY() + 0.5D + direction.getStepY() * 0.5D,
                supportingPosition.getZ() + 0.5D + direction.getStepZ() * 0.5D
        );
        BlockHitResult placementHit = new BlockHitResult(placementHitLocation, direction, supportingPosition, false);
        InteractionHand hand = player.getMainHandItem() == stack ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        BlockPlaceContext placementContext = new BlockPlaceContext(player, hand, stack, placementHit);
        BlockState placementState = block.getStateForPlacement(placementContext);
        if (placementState == null) {
            return null;
        }
        if (chainAssist) {
            placementState = withChainAxis(placementState, clickedState);
        }

        return new PlacementOffset(position, direction, placementState);
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

    private static Direction getChainAssistDirection(BlockPos clickedPosition, BlockState clickedState,
                                                     BlockHitResult hitResult) {
        Direction.Axis axis = getChainAxis(clickedState);
        if (axis == null) {
            return null;
        }

        double localAxisPosition = switch (axis) {
            case X -> hitResult.getLocation().x - clickedPosition.getX();
            case Y -> hitResult.getLocation().y - clickedPosition.getY();
            case Z -> hitResult.getLocation().z - clickedPosition.getZ();
        };
        if (localAxisPosition <= VERTICAL_ASSIST_EDGE) {
            return Direction.get(Direction.AxisDirection.NEGATIVE, axis);
        }
        if (localAxisPosition >= 1.0D - VERTICAL_ASSIST_EDGE) {
            return Direction.get(Direction.AxisDirection.POSITIVE, axis);
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
        return block instanceof FenceBlock || block instanceof IronBarsBlock || isChain(block) ? block : null;
    }

    private static boolean isSameFamily(Block first, Block second) {
        return first instanceof FenceBlock && second instanceof FenceBlock
                || first instanceof IronBarsBlock && second instanceof IronBarsBlock
                || isChain(first) && isChain(second);
    }

    private static boolean matchesExtension(Block heldBlock, BlockState clickedState, BlockState candidateState) {
        Block candidateBlock = candidateState.getBlock();
        if (!isSameFamily(heldBlock, candidateBlock)) {
            return false;
        }
        if (!isChain(heldBlock)) {
            return true;
        }
        Direction.Axis clickedAxis = getChainAxis(clickedState);
        return clickedAxis != null && clickedAxis == getChainAxis(candidateState);
    }

    private static boolean isChain(Block block) {
        String descriptionId = block.getDescriptionId();
        return descriptionId.endsWith(".chain") || descriptionId.endsWith("_chain");
    }

    private static Direction.Axis getChainAxis(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals("axis")
                    && property.getValueClass() == Direction.Axis.class) {
                return (Direction.Axis) state.getValue(property);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static BlockState withChainAxis(BlockState placementState, BlockState clickedState) {
        Direction.Axis axis = getChainAxis(clickedState);
        if (axis == null) {
            return placementState;
        }
        for (Property<?> property : placementState.getProperties()) {
            if (property.getName().equals("axis")
                    && property.getValueClass() == Direction.Axis.class) {
                return placementState.setValue((Property<Direction.Axis>) property, axis);
            }
        }
        return placementState;
    }

    public record PlacementOffset(BlockPos position, Direction direction, BlockState state) {
    }
}
