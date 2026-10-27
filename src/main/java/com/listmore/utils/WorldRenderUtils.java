package com.listmore.utils;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;

import fi.dy.masa.malilib.render.MaLiLibPipelines;
import fi.dy.masa.malilib.render.RenderContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class WorldRenderUtils {
    public static final float OUTLINE_WIDTH = 2.0F;

    private WorldRenderUtils() {
    }

    //TODO: 应该要再耦合malilib的，太麻烦了。暂时鸽了
    public static Vec3 getCameraPosition() {
        //#if MC >= 26.2
        //$$ return Minecraft.getInstance().gameRenderer.mainCamera().position();
        //#elseif MC >= 1.21.11
        //$$ return Minecraft.getInstance().gameRenderer.getMainCamera().position();
        //#else
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        //#endif
    }

    // 批量绘制完整方块的半透明填充
    // id渲染资源标识、positions要绘制的方块世界坐标、cameraPosition当前相机世界坐标、inset方块边界内缩量、fillColor填充颜色
    public static void drawBlockFills(String id, Iterable<BlockPos> positions, Vec3 cameraPosition,
                                      float inset, Color4f fillColor) {
        RenderContext context = createFillContext(id);
        try {
            BufferBuilder builder = context.getBuilder();
            for (BlockPos position : positions) {
                float minX = (float) (position.getX() + inset - cameraPosition.x);
                float minY = (float) (position.getY() + inset - cameraPosition.y);
                float minZ = (float) (position.getZ() + inset - cameraPosition.z);
                float maxX = minX + 1.0F - inset * 2.0F;
                float maxY = minY + 1.0F - inset * 2.0F;
                float maxZ = minZ + 1.0F - inset * 2.0F;

                RenderUtils.drawBoxAllSidesBatchedQuads(minX, minY, minZ, maxX, maxY, maxZ, fillColor, builder);
            }
            draw(context, builder.build());
        } finally {
            closeQuietly(context);
        }
    }

    // 批量绘制完整方块的逐方块描边
    // id渲染资源标识、positions要绘制的方块世界坐标、cameraPosition当前相机世界坐标、inset方块边界内缩量、outlineColor描边颜色
    public static void drawBlockOutlines(String id, Iterable<BlockPos> positions, Vec3 cameraPosition,
                                         float inset, Color4f outlineColor) {
        RenderContext context = createOutlineContext(id);
        try {
            BufferBuilder builder = context.getBuilder();
            for (BlockPos position : positions) {
                float minX = (float) (position.getX() + inset - cameraPosition.x);
                float minY = (float) (position.getY() + inset - cameraPosition.y);
                float minZ = (float) (position.getZ() + inset - cameraPosition.z);
                float maxX = minX + 1.0F - inset * 2.0F;
                float maxY = minY + 1.0F - inset * 2.0F;
                float maxZ = minZ + 1.0F - inset * 2.0F;

                drawBoxOutline(minX, minY, minZ, maxX, maxY, maxZ, outlineColor, builder);
            }
            draw(context, builder.build());
        } finally {
            closeQuietly(context);
        }
    }

    // 批量绘制完整方块的半透明填充和描边
    // id渲染资源标识、positions要绘制的方块世界坐标、cameraPosition当前相机世界坐标、inset方块边界内缩量、fillColor填充颜色、outlineColor描边颜色
    public static void drawFilledOutlinedBlockBoxes(String id, Iterable<BlockPos> positions, Vec3 cameraPosition,
                                                    float inset, Color4f fillColor, Color4f outlineColor) {
        drawBlockFills(id + "_fill", positions, cameraPosition, inset, fillColor);
        drawBlockOutlines(id + "_outline", positions, cameraPosition, inset, outlineColor);
    }

    // 绘制一个只有描边的立方体
    // id渲染资源标识、center相对于当前相机的立方体中心坐标、halfSize立方体半边长、color描边颜色
    public static void drawOutlinedBox(String id, Vec3 center, float halfSize, Color4f color) {
        RenderContext context = createOutlineContext(id);
        try {
            BufferBuilder builder = context.getBuilder();
            drawBoxOutline(
                    (float) (center.x - halfSize), (float) (center.y - halfSize), (float) (center.z - halfSize),
                    (float) (center.x + halfSize), (float) (center.y + halfSize), (float) (center.z + halfSize),
                    color, builder
            );
            draw(context, builder.build());
        } finally {
            closeQuietly(context);
        }
    }

    // 创建填充渲染上下文
    public static RenderContext createFillContext(String id) {
        return new RenderContext(
                () -> id,
                MaLiLibPipelines.POSITION_COLOR_MASA_NO_DEPTH_NO_CULL
                //#if MC >= 26.2
                //$$ , 0
                //#endif
        );
    }

    // 旧版本线宽由RenderContext设置，新版本在线段绘制调用中指定
    public static RenderContext createOutlineContext(String id) {
        RenderContext context = new RenderContext(
                () -> id,
                MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL
                //#if MC >= 26.2
                //$$ , 0
                //#endif
        );
        //#if MC < 1.21.11
        context.lineWidth(OUTLINE_WIDTH);
        //#endif
        return context;
    }

    // 统一各版本的线框参数差异
    private static void drawBoxOutline(float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                                       Color4f color, BufferBuilder builder) {
        RenderUtils.drawBoxAllEdgesBatchedLines(minX, minY, minZ, maxX, maxY, maxZ, color
                //#if MC >= 1.21.11
                //$$ , OUTLINE_WIDTH
                //#endif
                , builder);
    }

    public static void draw(RenderContext context, MeshData mesh) {
        if (mesh == null) {
            return;
        }
        try {
            context.draw(mesh, false, true);
        } finally {
            mesh.close();
        }
    }

    public static void closeQuietly(RenderContext context) {
        try {
            context.close();
        } catch (Exception ignored) {
        }
    }
}
