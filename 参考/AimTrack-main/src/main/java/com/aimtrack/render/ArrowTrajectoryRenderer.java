package com.aimtrack.render;

import com.aimtrack.AimTrackMod;
import com.aimtrack.config.AimTrackConfig;
import com.aimtrack.input.KeyBindings;
import com.aimtrack.lbng.LbngSystem;
import com.aimtrack.trajectory.ProjectileConfig;
import com.aimtrack.trajectory.TrajectoryPoint;
import com.aimtrack.trajectory.TrajectorySystem;
import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * 投射物落点预测 - 渲染入口
 * 支持两种模式：原始模式（梯度下降自瞄）和 LBNG 模式（Cydhranian + 二分法）
 * 使用TrajectorySystem缓存，只在状态变化时重算
 */
public class ArrowTrajectoryRenderer {

    // 缓存上一帧的主手物品，用于检测物品切换
    private static Item lastHeldItem = null;

    public static void register() {
        AimTrackMod.LOGGER.info("[AimTrack] 投射物落点预测渲染器注册完成");

        WorldRenderEvents.AFTER_ENTITIES.register((context) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) return;

            AimTrackConfig config = AutoConfig.getConfigHolder(AimTrackConfig.class).get();

            // ===== LBNG 模式路由 =====
            if (config.lbngMode) {
                LbngSystem.onRender(context);
                return;
            }

            // ===== 原始模式 =====
            ClientPlayerEntity player = client.player;

            // 检测物品切换，切换时清除目标
            Item currentHeldItem = player.getMainHandStack().getItem();
            if (lastHeldItem != null && lastHeldItem != currentHeldItem) {
                AutoAimHelper.clearTarget();
                TrajectorySystem.clearCache();
            }
            lastHeldItem = currentHeldItem;

            // 获取投射物配置
            ProjectileConfig projectileConfig = ProjectileConfig.fromItem(currentHeldItem);
            if (projectileConfig == null) {
                AutoAimHelper.clearTarget();
                return;
            }

            // 瞬间投掷物品：手持就显示落点预测（不自瞄）
            if (projectileConfig.instantThrow) {
                TrajectoryPoint landing = TrajectorySystem.getLandingPoint(player, client.world, projectileConfig);
                if (landing != null) {
                    TrajectoryDrawHelper.drawLandingPoint(context, landing.toVec3d(), landing.hitEntity);
                }
                return;
            }

            // 长按物品（弓箭、三叉戟）：需要正在使用才显示
            if (!player.isUsingItem()) {
                AutoAimHelper.clearTarget();
                return;
            }

            ItemStack useItem = player.getActiveItem();
            if (useItem == null) return;

            ProjectileConfig activeConfig = ProjectileConfig.fromItem(useItem.getItem());
            if (activeConfig == null || activeConfig.instantThrow) return;

            // 获取落点（带缓存）
            TrajectoryPoint landing = TrajectorySystem.getLandingPoint(player, client.world, activeConfig);

            if (landing != null) {
                TrajectoryDrawHelper.drawLandingPoint(context, landing.toVec3d(), landing.hitEntity);

                // 画锁定目标的碰撞箱
                if (activeConfig.needsAim) {
                    Entity lockedTarget = AutoAimHelper.getCurrentTarget();
                    if (lockedTarget != null) {
                        TrajectoryDrawHelper.drawTargetBox(context, lockedTarget);
                    }
                }

                // 自瞄逻辑
                if (activeConfig.needsAim && KeyBindings.isAimEnabled()) {
                    if (useItem.getItem() instanceof net.minecraft.item.BowItem) {
                        int useDuration = player.getItemUseTime();
                        float pull = net.minecraft.item.BowItem.getPullProgress(useDuration);
                        if (pull >= 1.0f) {
                            AutoAimHelper.tryAutoAim(player, landing.toVec3d(), landing.tick, activeConfig.speed, activeConfig.gravity);
                        } else {
                            AutoAimHelper.clearTarget();
                        }
                    } else {
                        AutoAimHelper.tryAutoAim(player, landing.toVec3d(), landing.tick, activeConfig.speed, activeConfig.gravity);
                    }
                } else {
                    AutoAimHelper.clearTarget();
                }
            } else {
                AutoAimHelper.clearTarget();
            }
        });
    }
}
