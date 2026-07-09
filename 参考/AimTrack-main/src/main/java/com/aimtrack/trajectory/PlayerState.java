package com.aimtrack.trajectory;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

/**
 * 玩家状态快照
 * 用于检测是否需要重新计算轨迹
 */
public class PlayerState {
    public final double x, y, z;
    public final float yaw, pitch;
    public final ItemStack mainHand;
    public final int itemUseTime;
    public final boolean isUsingItem;

    public PlayerState(ClientPlayerEntity player) {
        Vec3d pos = player.getPos();
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        this.yaw = player.getYaw();
        this.pitch = player.getPitch();
        this.mainHand = player.getMainHandStack();
        this.itemUseTime = player.getItemUseTime();
        this.isUsingItem = player.isUsingItem();
    }

    /**
     * 检测状态是否变化到需要重新计算轨迹
     */
    public boolean needsRecalculation(PlayerState other) {
        if (other == null) return true;

        // 位置变化超过0.01格
        if (Math.abs(x - other.x) > 0.01 || Math.abs(y - other.y) > 0.01 || Math.abs(z - other.z) > 0.01) {
            return true;
        }

        // 视角变化超过0.1度
        if (Math.abs(yaw - other.yaw) > 0.1f || Math.abs(pitch - other.pitch) > 0.1f) {
            return true;
        }

        // 物品变化
        if (mainHand.getItem() != other.mainHand.getItem()) {
            return true;
        }

        // 拉弓力度变化（弓箭需要）
        if (isUsingItem && itemUseTime != other.itemUseTime) {
            return true;
        }

        // 使用状态变化
        if (isUsingItem != other.isUsingItem) {
            return true;
        }

        return false;
    }
}
