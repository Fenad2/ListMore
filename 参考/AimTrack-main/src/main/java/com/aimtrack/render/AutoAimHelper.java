package com.aimtrack.render;

import com.aimtrack.config.AimTrackConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;
import java.util.Optional;

/**
 * 自动瞄准辅助
 * 满弓时自动把视角拉到落点附近最近的实体身上
 * 会预判实体的移动，模拟人手平滑移动
 * 核心思路：迭代模拟箭的抛物线弹道，找到能命中目标的pitch角
 */
public class AutoAimHelper {

    private static AimTrackConfig getConfig() {
        return AutoConfig.getConfigHolder(AimTrackConfig.class).get();
    }

    private static final double DRAG = 0.99;

    private static double currentProjectileSpeed = 3.0;
    private static double currentProjectileGravity = 0.05;

    private static final int MAX_SIM_TICKS = 200;

    private static Entity currentTarget = null;
    private static int lostCount = 0;

    private static float lastPitch = 0.0f;
    private static boolean hasLastPitch = false;

    private static Vec3d lastTargetPos = Vec3d.ZERO;
    private static boolean hasLastTargetPos = false;

    public static boolean tryAutoAim(ClientPlayerEntity player, Vec3d landingPos, int flightTicks, double projectileSpeed, double projectileGravity) {
        if (!com.aimtrack.input.KeyBindings.isAimEnabled()) {
            return false;
        }

        if (player == null) return false;

        currentProjectileSpeed = projectileSpeed;
        currentProjectileGravity = projectileGravity;

        // 先看看当前目标还在不在范围内
        if (currentTarget != null) {
            boolean shouldLose = false;
            if (!currentTarget.isAlive()) {
                shouldLose = true;
            } else {
                double distToPlayer = currentTarget.squaredDistanceTo(player.getPos());
                if (distToPlayer > 50.0 * 50.0) {
                    shouldLose = true;
                }
            }

            if (shouldLose) {
                lostCount++;
                if (lostCount >= getConfig().loseThreshold) {
                    currentTarget = null;
                    lostCount = 0;
                    hasLastPitch = false;
                    return false;
                }
            } else {
                lostCount = 0;
            }
        }

        // 没有目标就找一个新的
        if (currentTarget == null) {
            currentTarget = findNearestTarget(player, landingPos);
            if (currentTarget == null) return false;
            hasLastPitch = false;
        }

        // 预测实体在箭到达时的位置
        Vec3d predictedPos = predictEntityPosition(currentTarget, flightTicks);

        // 平滑目标位置，防止史莱姆跳跃时位置剧烈变化
        if (hasLastTargetPos) {
            predictedPos = new Vec3d(
                lastTargetPos.x + (predictedPos.x - lastTargetPos.x) * getConfig().positionSmoothFactor,
                lastTargetPos.y + (predictedPos.y - lastTargetPos.y) * getConfig().positionSmoothFactor,
                lastTargetPos.z + (predictedPos.z - lastTargetPos.z) * getConfig().positionSmoothFactor
            );
        }
        lastTargetPos = predictedPos;
        hasLastTargetPos = true;

        // 用弹道模拟算出真正能命中的yaw和pitch
        float[] aimAngles = calculateAimAngles(player, predictedPos, currentTarget);
        if (aimAngles == null) return false;

        float targetYaw = aimAngles[0];
        float targetPitch = aimAngles[1];

        // 平滑转向，模拟人手
        smoothTurn(player, targetYaw, targetPitch);

        return true;
    }

    public static void clearTarget() {
        currentTarget = null;
        lostCount = 0;
        hasLastPitch = false;
        hasLastTargetPos = false;
    }

    public static Entity getCurrentTarget() {
        return currentTarget;
    }

    private static Entity findNearestTarget(ClientPlayerEntity player, Vec3d landingPos) {
        List<LivingEntity> entities = player.getWorld().getEntitiesByClass(
                LivingEntity.class,
                new Box(
                        landingPos.x - getConfig().searchRadius, landingPos.y - getConfig().searchRadius, landingPos.z - getConfig().searchRadius,
                        landingPos.x + getConfig().searchRadius, landingPos.y + getConfig().searchRadius, landingPos.z + getConfig().searchRadius
                ),
                entity -> entity != player && entity.isAlive() && entity.canHit()
        );

        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (LivingEntity entity : entities) {
            if (!isInFrontOfPlayer(player, entity)) {
                continue;
            }
            if (!hasLineOfSight(player, entity)) {
                continue;
            }

            double dist = entity.squaredDistanceTo(landingPos);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = entity;
            }
        }

        return nearest;
    }

    private static float[] calculateAimAngles(ClientPlayerEntity player, Vec3d targetPos, Entity target) {
        Vec3d playerEye = player.getEyePos();

        // 目标中心（取碰撞箱0.6高度处，补偿箭的下坠）
        Vec3d targetCenter = targetPos.add(0, target.getHeight() * 0.6, 0);

        // 水平方向直接对准目标
        double dx = targetCenter.x - playerEye.x;
        double dz = targetCenter.z - playerEye.z;
        float targetYaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;

        // 水平距离
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDist < 0.1) return null;

        // 直瞄的pitch作为初始值
        double dy = targetCenter.y - playerEye.y;
        float directPitch = (float) (-(MathHelper.atan2(dy, horizontalDist) * (180.0 / Math.PI)));

        // 如果有上一帧的pitch，从那里开始（更稳定）
        float startPitch = hasLastPitch ? lastPitch : directPitch;
        startPitch = MathHelper.clamp(startPitch, -90.0f, 90.0f);

        // 梯度下降法找最佳pitch
        float bestPitch = startPitch;
        double bestScore = Double.MAX_VALUE;

        // 搜索范围
        float searchRange = 20.0f;
        float low = Math.max(startPitch - searchRange, -90.0f);
        float high = Math.min(startPitch + searchRange, 90.0f);

        // 先用较粗的步长扫描整个范围，找到大概位置
        float coarseStep = 2.0f;
        for (float p = low; p <= high; p += coarseStep) {
            double score = simulateScore(playerEye, targetYaw, p, targetCenter, horizontalDist, targetPos, target);
            if (score < bestScore) {
                bestScore = score;
                bestPitch = p;
            }
        }

        // 再用细步长在最佳位置附近精修
        float fineLow = Math.max(bestPitch - coarseStep, -90.0f);
        float fineHigh = Math.min(bestPitch + coarseStep, 90.0f);
        float fineStep = 0.2f;
        for (float p = fineLow; p <= fineHigh; p += fineStep) {
            double score = simulateScore(playerEye, targetYaw, p, targetCenter, horizontalDist, targetPos, target);
            if (score < bestScore) {
                bestScore = score;
                bestPitch = p;
            }
        }

        // 缓存这一帧的结果
        lastPitch = bestPitch;
        hasLastPitch = true;

        return new float[]{targetYaw, bestPitch};
    }

    private static double simulateScore(Vec3d startPos, float yaw, float pitch,
                                        Vec3d targetCenter, double targetHorizontalDist,
                                        Vec3d targetPos, Entity target) {
        float yawRad = yaw * ((float) Math.PI / 180.0f);
        float pitchRad = pitch * ((float) Math.PI / 180.0f);

        double vx = -Math.sin(yawRad) * Math.cos(pitchRad) * currentProjectileSpeed;
        double vy = -Math.sin(pitchRad) * currentProjectileSpeed;
        double vz = Math.cos(yawRad) * Math.cos(pitchRad) * currentProjectileSpeed;

        Vec3d pos = startPos;
        Vec3d vel = new Vec3d(vx, vy, vz);
        Vec3d prevPos = pos;

        // 目标的碰撞箱（基于预测位置）
        Box targetBox = target.getBoundingBox().offset(targetPos.subtract(target.getPos()));

        double prevHorizontalDist = 0;
        double closestYDiff = Double.MAX_VALUE;
        boolean passedTarget = false;

        for (int i = 0; i < MAX_SIM_TICKS; i++) {
            pos = pos.add(vel);
            vel = vel.multiply(DRAG);
            vel = vel.subtract(0, currentProjectileGravity, 0);

            // 检查这一步有没有穿过目标碰撞箱
            Optional<Vec3d> hit = targetBox.raycast(prevPos, pos);
            if (hit.isPresent()) {
                return -1000.0;
            }

            // 计算当前水平距离
            double hDist = Math.sqrt((pos.x - startPos.x) * (pos.x - startPos.x) +
                                     (pos.z - startPos.z) * (pos.z - startPos.z));

            // 如果水平距离刚好穿过目标水平距离，记录Y差
            if (prevHorizontalDist < targetHorizontalDist && hDist >= targetHorizontalDist) {
                double t = (targetHorizontalDist - prevHorizontalDist) / (hDist - prevHorizontalDist);
                double estimatedY = prevPos.y + (pos.y - prevPos.y) * t;
                double yDiff = Math.abs(estimatedY - targetCenter.y);
                closestYDiff = yDiff;
                passedTarget = true;
            }

            if (passedTarget && hDist > targetHorizontalDist + 5.0) {
                break;
            }

            if (pos.y < startPos.y - 100) break;
            prevPos = pos;
            prevHorizontalDist = hDist;
        }

        double score = closestYDiff;
        if (!passedTarget) {
            double closestHorizontalDist = prevHorizontalDist;
            double distDiff = Math.abs(closestHorizontalDist - targetHorizontalDist);
            score += distDiff * 0.5;
        }

        return score;
    }

    private static boolean isInFrontOfPlayer(ClientPlayerEntity player, Entity entity) {
        Vec3d lookDir = player.getRotationVec(1.0f);
        Vec3d toEntity = entity.getPos().subtract(player.getPos()).normalize();
        double dot = lookDir.dotProduct(toEntity);
        return dot > MathHelper.cos((float) getConfig().fovAngle * (float) Math.PI / 180.0f);
    }

    private static boolean hasLineOfSight(ClientPlayerEntity player, Entity entity) {
        Vec3d playerEye = player.getEyePos();
        Vec3d targetPos = entity.getPos().add(0, entity.getHeight() * 0.5, 0);

        HitResult hit = player.getWorld().raycast(new RaycastContext(
                playerEye, targetPos,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }

        var blockState = player.getWorld().getBlockState(((net.minecraft.util.hit.BlockHitResult) hit).getBlockPos());
        var block = blockState.getBlock();

        if (block instanceof net.minecraft.block.FenceBlock ||
            block instanceof net.minecraft.block.FenceGateBlock ||
            block instanceof net.minecraft.block.PaneBlock ||
            block instanceof net.minecraft.block.CactusBlock ||
            block instanceof net.minecraft.block.CobwebBlock) {
            return true;
        }

        double distToTarget = hit.getPos().squaredDistanceTo(targetPos);
        return distToTarget < 1.0;
    }

    private static Vec3d predictEntityPosition(Entity entity, int ticks) {
        Vec3d velocity = entity.getVelocity();
        double vy = velocity.y * 0.1;
        return entity.getPos().add(velocity.x * ticks, vy * ticks, velocity.z * ticks);
    }

    private static void smoothTurn(ClientPlayerEntity player, float targetYaw, float targetPitch) {
        float currentYaw = player.getYaw();
        float currentPitch = player.getPitch();

        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = MathHelper.wrapDegrees(targetPitch - currentPitch);

        yawDiff = MathHelper.clamp(yawDiff, -(float) getConfig().maxTurnSpeed, (float) getConfig().maxTurnSpeed);
        pitchDiff = MathHelper.clamp(pitchDiff, -(float) getConfig().maxPitchSpeed, (float) getConfig().maxPitchSpeed);

        float newYaw = currentYaw + yawDiff * (float) getConfig().smoothFactor;
        float newPitch = currentPitch + pitchDiff * (float) getConfig().smoothFactor;

        newPitch = MathHelper.clamp(newPitch, -90.0f, 90.0f);

        player.setYaw(newYaw);
        player.setPitch(newPitch);
    }
}
