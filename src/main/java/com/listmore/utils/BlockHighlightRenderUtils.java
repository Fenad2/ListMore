package com.listmore.utils;

import com.mojang.blaze3d.vertex.BufferBuilder;

import fi.dy.masa.malilib.render.RenderContext;
import fi.dy.masa.malilib.util.data.Color4f;

import net.minecraft.world.phys.Vec3;

/**
 * 方块高亮合并轮廓的共享绘制工具
 * 每个功能自己持有一个 {@link BlockHighlightGeometryUtils}, 方块集合变化时调用
 * {@link BlockHighlightGeometryUtils#rebuild}, 每帧调用这里的绘制方法
 * 只允许在客户端渲染线程使用
 */
public final class BlockHighlightRenderUtils {
	private BlockHighlightRenderUtils() {
	}

	// 只画体素并集的暴露面
	public static void drawMergedFill(String id, BlockHighlightGeometryUtils geometry, Vec3 cameraPosition, Color4f color) {
		if (geometry.faceCount() == 0 || color.a <= 0.0F) {
			return;
		}
		RenderContext context = WorldRenderUtils.createFillContext(id);
		try {
			BufferBuilder builder = context.getBuilder();
			int[] faces = geometry.faces();
			int faceCount = geometry.faceCount();

			for (int faceIndex = 0; faceIndex < faceCount; faceIndex++) {
				int base = faceIndex * 4;
				int x = faces[base];
				int y = faces[base + 1];
				int z = faces[base + 2];
				int face = faces[base + 3];
				int cornerBase = face * 4;

				for (int corner = 0; corner < 4; corner++) {
					int cornerIndex = cornerBase + corner;
					addQuadVertex(builder,
						(float) (x + BlockHighlightGeometryUtils.FACE_CORNER_X[cornerIndex] - cameraPosition.x),
						(float) (y + BlockHighlightGeometryUtils.FACE_CORNER_Y[cornerIndex] - cameraPosition.y),
						(float) (z + BlockHighlightGeometryUtils.FACE_CORNER_Z[cornerIndex] - cameraPosition.z),
						color);
				}
			}
			WorldRenderUtils.draw(context, builder.build());
		} finally {
			WorldRenderUtils.closeQuietly(context);
		}
	}

	// 只画体素并集的硬边
	public static void drawMergedOutline(String id, BlockHighlightGeometryUtils geometry, Vec3 cameraPosition, Color4f color) {
		if (geometry.edgeCount() == 0 || color.a <= 0.0F) {
			return;
		}

		RenderContext context = WorldRenderUtils.createOutlineContext(id);
		try {
			BufferBuilder builder = context.getBuilder();
			int[] edges = geometry.edges();
			int edgeCount = geometry.edgeCount();

			for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
				int base = edgeIndex * 4;
				int x = edges[base];
				int y = edges[base + 1];
				int z = edges[base + 2];
				int axis = edges[base + 3];
				int endX = axis == 0 ? x + 1 : x;
				int endY = axis == 1 ? y + 1 : y;
				int endZ = axis == 2 ? z + 1 : z;

				drawLine(builder,
					(float) (x - cameraPosition.x), (float) (y - cameraPosition.y), (float) (z - cameraPosition.z),
					(float) (endX - cameraPosition.x), (float) (endY - cameraPosition.y), (float) (endZ - cameraPosition.z),
					color);
			}
			WorldRenderUtils.draw(context, builder.build());
		} finally {
			WorldRenderUtils.closeQuietly(context);
		}
	}

	private static void drawLine(BufferBuilder builder, float minX, float minY, float minZ,
			float maxX, float maxY, float maxZ, Color4f color) {
		addLineVertex(builder, minX, minY, minZ, color);
		addLineVertex(builder, maxX, maxY, maxZ, color);
	}

	private static void addLineVertex(BufferBuilder builder, float x, float y, float z, Color4f color) {
		//#if MC >= 1.21.11
		//$$ builder.addVertex(x, y, z).setColor(color.r, color.g, color.b, color.a).setLineWidth(WorldRenderUtils.OUTLINE_WIDTH);
		//#else
		builder.addVertex(x, y, z).setColor(color.r, color.g, color.b, color.a);
		//#endif
	}

	private static void addQuadVertex(BufferBuilder builder, float x, float y, float z, Color4f color) {
		builder.addVertex(x, y, z).setColor(color.r, color.g, color.b, color.a);
	}
}
