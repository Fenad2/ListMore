package com.aimtrack.trajectory;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.world.World;

import java.util.List;

/**
 * 轨迹系统 - 核心管理器
 * 负责状态检测、缓存管理、触发重算
 */
public class TrajectorySystem {

    private static PlayerState lastState = null;
    private static List<TrajectoryPoint> cachedTrajectory = null;
    private static ProjectileConfig cachedConfig = null;

    /**
     * 获取当前轨迹（带缓存）
     * 如果玩家状态没变，直接返回缓存结果
     */
    public static List<TrajectoryPoint> getTrajectory(ClientPlayerEntity player, World world, ProjectileConfig config) {
        PlayerState currentState = new PlayerState(player);

        // 检测是否需要重新计算
        if (lastState == null || lastState.needsRecalculation(currentState) || cachedConfig != config) {
            cachedTrajectory = TrajectoryPredictor.predict(player, world, config);
            lastState = currentState;
            cachedConfig = config;
        }

        return cachedTrajectory;
    }

    /**
     * 获取轨迹的落点（最后一个点）
     */
    public static TrajectoryPoint getLandingPoint(ClientPlayerEntity player, World world, ProjectileConfig config) {
        List<TrajectoryPoint> trajectory = getTrajectory(player, world, config);
        if (trajectory.isEmpty()) return null;
        return trajectory.get(trajectory.size() - 1);
    }

    /**
     * 清除缓存（比如切换世界时）
     */
    public static void clearCache() {
        lastState = null;
        cachedTrajectory = null;
        cachedConfig = null;
    }
}
