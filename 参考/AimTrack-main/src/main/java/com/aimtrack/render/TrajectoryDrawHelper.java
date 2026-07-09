package com.aimtrack.render;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * 落点标记渲染器
 * 在世界里画一个小方块标记箭的落点
 * 打到生物 → 红色
 * 打到方块/地面 → 荧光绿
 */
public class TrajectoryDrawHelper {

    // 方块半边长（大概4像素）
    private static final float HALF_SIZE = 0.125f;

    // 打到生物的颜色 - 红色
    private static final int RED_R = 255;
    private static final int RED_G = 0;
    private static final int RED_B = 0;
    private static final int RED_A = 153;

    // 打到方块的颜色 - 荧光绿
    private static final int GREEN_R = 0;
    private static final int GREEN_G = 255;
    private static final int GREEN_B = 51;
    private static final int GREEN_A = 153;

    /**
     * 在落点位置画一个半透明的小方块
     *
     * @param context  渲染上下文
     * @param landing  落点位置
     * @param isEntity 是不是打到了生物
     */
    public static void drawLandingPoint(WorldRenderContext context, Vec3d landing, boolean isEntity) {
        Vec3d camera = context.camera().getPos();

        // 把坐标原点移到相机位置，这样世界坐标就能直接用了
        context.matrixStack().push();
        context.matrixStack().translate(-camera.x, -camera.y, -camera.z);

        try {
            // 用DebugQuads，不需要UV坐标
            VertexConsumer consumer = context.consumers().getBuffer(RenderLayer.getDebugQuads());

            double x = landing.x;
            double y = landing.y;
            double z = landing.z;
            double s = HALF_SIZE;

            var pose = context.matrixStack().peek();

            // 根据落点类型选颜色
            int r = isEntity ? RED_R : GREEN_R;
            int g = isEntity ? RED_G : GREEN_G;
            int b = isEntity ? RED_B : GREEN_B;
            int a = isEntity ? RED_A : GREEN_A;

            // 画六个面组成一个实心方块
            // 底面
            addQuad(consumer, pose,
                    x - s, y - s, z - s,
                    x + s, y - s, z - s,
                    x + s, y - s, z + s,
                    x - s, y - s, z + s,
                    r, g, b, a);

            // 顶面
            addQuad(consumer, pose,
                    x - s, y + s, z - s,
                    x - s, y + s, z + s,
                    x + s, y + s, z + s,
                    x + s, y + s, z - s,
                    r, g, b, a);

            // 前面
            addQuad(consumer, pose,
                    x - s, y - s, z + s,
                    x + s, y - s, z + s,
                    x + s, y + s, z + s,
                    x - s, y + s, z + s,
                    r, g, b, a);

            // 后面
            addQuad(consumer, pose,
                    x - s, y - s, z - s,
                    x - s, y + s, z - s,
                    x + s, y + s, z - s,
                    x + s, y - s, z - s,
                    r, g, b, a);

            // 左面
            addQuad(consumer, pose,
                    x - s, y - s, z - s,
                    x - s, y - s, z + s,
                    x - s, y + s, z + s,
                    x - s, y + s, z - s,
                    r, g, b, a);

            // 右面
            addQuad(consumer, pose,
                    x + s, y - s, z - s,
                    x + s, y + s, z - s,
                    x + s, y + s, z + s,
                    x + s, y - s, z + s,
                    r, g, b, a);

        } finally {
            context.matrixStack().pop();
        }
    }

    /**
     * 画一个四边形（四个顶点）
     */
    private static void addQuad(VertexConsumer consumer, net.minecraft.client.util.math.MatrixStack.Entry pose,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2,
                                double x3, double y3, double z3,
                                double x4, double y4, double z4,
                                int r, int g, int b, int a) {
        consumer.vertex(pose, (float) x1, (float) y1, (float) z1).color(r, g, b, a);
        consumer.vertex(pose, (float) x2, (float) y2, (float) z2).color(r, g, b, a);
        consumer.vertex(pose, (float) x3, (float) y3, (float) z3).color(r, g, b, a);
        consumer.vertex(pose, (float) x4, (float) y4, (float) z4).color(r, g, b, a);
    }

    // 锁定目标的碰撞箱颜色 - 红色线框
    private static final int BOX_R = 255;
    private static final int BOX_G = 50;
    private static final int BOX_B = 50;
    private static final int BOX_A = 180;

    /**
     * 画锁定目标的碰撞箱 - 红色线框（不画实心面，避免遮挡实体）
     */
    public static void drawTargetBox(WorldRenderContext context, Entity target) {
        if (target == null) return;

        Box box = target.getBoundingBox().expand(0.05);

        Vec3d camera = context.camera().getPos();

        context.matrixStack().push();
        context.matrixStack().translate(-camera.x, -camera.y, -camera.z);

        try {
            VertexConsumer consumer = context.consumers().getBuffer(RenderLayer.getLines());
            var pose = context.matrixStack().peek();

            double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
            double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;

            // 12条边
            addLine(consumer, pose, x1, y1, z1, x2, y1, z1);
            addLine(consumer, pose, x2, y1, z1, x2, y1, z2);
            addLine(consumer, pose, x2, y1, z2, x1, y1, z2);
            addLine(consumer, pose, x1, y1, z2, x1, y1, z1);

            addLine(consumer, pose, x1, y2, z1, x2, y2, z1);
            addLine(consumer, pose, x2, y2, z1, x2, y2, z2);
            addLine(consumer, pose, x2, y2, z2, x1, y2, z2);
            addLine(consumer, pose, x1, y2, z2, x1, y2, z1);

            addLine(consumer, pose, x1, y1, z1, x1, y2, z1);
            addLine(consumer, pose, x2, y1, z1, x2, y2, z1);
            addLine(consumer, pose, x2, y1, z2, x2, y2, z2);
            addLine(consumer, pose, x1, y1, z2, x1, y2, z2);

        } finally {
            context.matrixStack().pop();
        }
    }

    private static void addLine(VertexConsumer consumer, net.minecraft.client.util.math.MatrixStack.Entry pose,
                                double x1, double y1, double z1, double x2, double y2, double z2) {
        consumer.vertex(pose, (float) x1, (float) y1, (float) z1).color(BOX_R, BOX_G, BOX_B, BOX_A).normal(0, 1, 0);
        consumer.vertex(pose, (float) x2, (float) y2, (float) z2).color(BOX_R, BOX_G, BOX_B, BOX_A).normal(0, 1, 0);
    }

}
