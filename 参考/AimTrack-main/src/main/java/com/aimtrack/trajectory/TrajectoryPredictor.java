package com.aimtrack.trajectory;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.BowItem;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 轨迹预测器
 * 用原始double模拟投射物飞行，避免临时对象
 * 速度低于阈值时提前终止
 */
public class TrajectoryPredictor {

    private static final int MAX_TICKS = 200;
    private static final double MIN_VELOCITY_SQ = 0.0001; // 速度平方低于此值终止
    private static final double HIT_SIZE = 0.1;

    /**
     * 预测轨迹
     * @return 轨迹点列表，最后一个点是命中点或终点
     */
    public static List<TrajectoryPoint> predict(ClientPlayerEntity player, World world, ProjectileConfig config) {
        List<TrajectoryPoint> points = new ArrayList<>();

        // 获取初始位置和速度
        double px, py, pz;
        double vx, vy, vz;

        Vec3d eyePos = player.getEyePos();
        px = eyePos.x;
        py = eyePos.y - 0.10000000149011612;
        pz = eyePos.z;

        double speed = config.speed;

        // 弓箭根据拉弓力度调整速度
        if (!config.instantThrow && player.getActiveItem().getItem() instanceof BowItem) {
            int useDuration = player.getItemUseTime();
            float pull = BowItem.getPullProgress(useDuration);
            if (pull < 0.1f) return points;
            speed *= pull;
        }

        Vec3d lookDir = player.getRotationVec(1.0f);
        vx = lookDir.x * speed;
        vy = lookDir.y * speed;
        vz = lookDir.z * speed;

        // 预缓存范围内的实体
        // 估算最大射程：粗略按速度*50tick估算
        double maxRange = speed * 50;
        Box searchBox = new Box(px - maxRange, py - maxRange, pz - maxRange,
                                px + maxRange, py + maxRange, pz + maxRange);
        List<Entity> cachedEntities = world.getEntitiesByClass(Entity.class, searchBox,
            e -> e != player && e.isAlive() && e.canHit());

        double prevX = px, prevY = py, prevZ = pz;

        for (int tick = 0; tick < MAX_TICKS; tick++) {
            // 更新位置
            px += vx;
            py += vy;
            pz += vz;

            // 应用阻力和重力
            vx *= config.drag;
            vy *= config.drag;
            vy -= config.gravity;
            vz *= config.drag;

            // 检测实体碰撞
            EntityHitResult entityHit = checkEntityCollision(prevX, prevY, prevZ, px, py, pz, cachedEntities);

            // 检测方块碰撞
            HitResult blockHit = world.raycast(new RaycastContext(
                new Vec3d(prevX, prevY, prevZ), new Vec3d(px, py, pz),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
            ));

            double entityDist = entityHit != null ? distSq(prevX, prevY, prevZ, entityHit.x, entityHit.y, entityHit.z) : Double.MAX_VALUE;
            double blockDist = blockHit.getType() != HitResult.Type.MISS ? distSq(prevX, prevY, prevZ, blockHit.getPos().x, blockHit.getPos().y, blockHit.getPos().z) : Double.MAX_VALUE;

            if (entityDist < blockDist && entityHit != null) {
                points.add(new TrajectoryPoint(entityHit.x, entityHit.y, entityHit.z, tick, true, false));
                return points;
            }
            if (blockDist < entityDist && blockHit.getType() != HitResult.Type.MISS) {
                points.add(new TrajectoryPoint(blockHit.getPos().x, blockHit.getPos().y, blockHit.getPos().z, tick, false, true));
                return points;
            }

            // 掉出世界
            if (py < -64) {
                points.add(new TrajectoryPoint(px, py, pz, tick, false, true));
                return points;
            }

            // 速度过低提前终止
            double velSq = vx * vx + vy * vy + vz * vz;
            if (velSq < MIN_VELOCITY_SQ) {
                points.add(new TrajectoryPoint(px, py, pz, tick, false, false));
                return points;
            }

            prevX = px;
            prevY = py;
            prevZ = pz;
        }

        points.add(new TrajectoryPoint(px, py, pz, MAX_TICKS, false, false));
        return points;
    }

    private static double distSq(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        return dx * dx + dy * dy + dz * dz;
    }

    private static EntityHitResult checkEntityCollision(double x1, double y1, double z1,
                                                         double x2, double y2, double z2,
                                                         List<Entity> entities) {
        EntityHitResult closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity entity : entities) {
            Box box = entity.getBoundingBox().expand(HIT_SIZE);
            // 手动raycast，避免创建Vec3d
            // 简化：用box的raycast方法
            var hit = box.raycast(new Vec3d(x1, y1, z1), new Vec3d(x2, y2, z2));
            if (hit.isPresent()) {
                Vec3d h = hit.get();
                double d = distSq(x1, y1, z1, h.x, h.y, h.z);
                if (d < closestDist) {
                    closestDist = d;
                    closest = new EntityHitResult(h.x, h.y, h.z, entity);
                }
            }
        }

        return closest;
    }

    private static class EntityHitResult {
        final double x, y, z;
        final Entity entity;

        EntityHitResult(double x, double y, double z, Entity entity) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.entity = entity;
        }
    }
}
