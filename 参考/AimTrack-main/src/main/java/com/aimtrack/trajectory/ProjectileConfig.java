package com.aimtrack.trajectory;

import net.minecraft.item.*;

/**
 * 投射物配置
 */
public class ProjectileConfig {
    public final double speed;
    public final double gravity;
    public final double drag;
    public final boolean needsAim;
    public final boolean instantThrow;

    public ProjectileConfig(double speed, double gravity, double drag, boolean needsAim, boolean instantThrow) {
        this.speed = speed;
        this.gravity = gravity;
        this.drag = drag;
        this.needsAim = needsAim;
        this.instantThrow = instantThrow;
    }

    // 弓箭
    public static final ProjectileConfig BOW = new ProjectileConfig(3.0, 0.05, 0.99, true, false);
    // 三叉戟
    public static final ProjectileConfig TRIDENT = new ProjectileConfig(2.5, 0.05, 0.99, true, false);
    // 雪球
    public static final ProjectileConfig SNOWBALL = new ProjectileConfig(1.5, 0.03, 0.99, false, true);
    // 鸡蛋
    public static final ProjectileConfig EGG = new ProjectileConfig(1.5, 0.03, 0.99, false, true);
    // 末影珍珠
    public static final ProjectileConfig PEARL = new ProjectileConfig(1.5, 0.03, 0.99, false, true);

    public static ProjectileConfig fromItem(Item item) {
        if (item instanceof BowItem) return BOW;
        if (item instanceof TridentItem) return TRIDENT;
        if (item instanceof SnowballItem) return SNOWBALL;
        if (item instanceof EggItem) return EGG;
        if (item instanceof EnderPearlItem) return PEARL;
        return null;
    }
}
