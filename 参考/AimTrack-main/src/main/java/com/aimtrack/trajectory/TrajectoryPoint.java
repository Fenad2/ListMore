package com.aimtrack.trajectory;

import net.minecraft.util.math.Vec3d;

/**
 * 轨迹上的一个点
 * 用原始double存储，避免临时对象
 */
public class TrajectoryPoint {
    public final double x, y, z;
    public final int tick;
    public final boolean hitEntity;
    public final boolean hitBlock;

    public TrajectoryPoint(double x, double y, double z, int tick, boolean hitEntity, boolean hitBlock) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.tick = tick;
        this.hitEntity = hitEntity;
        this.hitBlock = hitBlock;
    }

    public Vec3d toVec3d() {
        return new Vec3d(x, y, z);
    }
}
