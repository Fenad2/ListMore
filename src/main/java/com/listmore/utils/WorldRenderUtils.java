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
	private static final float OUTLINE_WIDTH = 2.0F;

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

	// 批量绘制完整方块的逐方块描边（旧样式）
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

	// 批量绘制完整方块的半透明填充和描边（逐方块，旧样式）
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

	// 绘制合并后的方块高亮填充，只画体素并集的外表面
	// id渲染资源标识、geometry缓存的合并轮廓、cameraPosition当前相机世界坐标、color填充颜色
	public static void drawMergedBlockFill(String id, BlockHighlightGeometry geometry, Vec3 cameraPosition,
			Color4f color) {
		if (geometry.faceCount() == 0 || color.a <= 0.0F) {
			return;
		}

		RenderContext context = createFillContext(id);
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
						(float) (x + BlockHighlightGeometry.FACE_CORNER_X[cornerIndex] - cameraPosition.x),
						(float) (y + BlockHighlightGeometry.FACE_CORNER_Y[cornerIndex] - cameraPosition.y),
						(float) (z + BlockHighlightGeometry.FACE_CORNER_Z[cornerIndex] - cameraPosition.z),
						color);
				}
			}
			draw(context, builder.build());
		} finally {
			closeQuietly(context);
		}
	}

	// 绘制合并后的方块高亮轮廓，只画体素并集的硬边
	// id渲染资源标识、geometry缓存的合并轮廓、cameraPosition当前相机世界坐标、color描边颜色
	public static void drawMergedBlockOutline(String id, BlockHighlightGeometry geometry, Vec3 cameraPosition,
			Color4f color) {
		if (geometry.edgeCount() == 0 || color.a <= 0.0F) {
			return;
		}

		RenderContext context = createOutlineContext(id);
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
			draw(context, builder.build());
		} finally {
			closeQuietly(context);
		}
	}

	// 填充
	private static RenderContext createFillContext(String id) {
		return new RenderContext(
			() -> id,
			MaLiLibPipelines.POSITION_COLOR_MASA_NO_DEPTH_NO_CULL
			//#if MC >= 26.2
			//$$ , 0
			//#endif
		);
	}

	// 旧版本线宽由RenderContext设置，新版本在线段绘制调用中指定
	private static RenderContext createOutlineContext(String id) {
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

	// 绘制一条线段，统一各版本的顶点与线宽参数差异
	private static void drawLine(BufferBuilder builder, float minX, float minY, float minZ,
			float maxX, float maxY, float maxZ, Color4f color) {
		addLineVertex(builder, minX, minY, minZ, color);
		addLineVertex(builder, maxX, maxY, maxZ, color);
	}

	private static void addLineVertex(BufferBuilder builder, float x, float y, float z, Color4f color) {
		//#if MC >= 1.21.11
		//$$ builder.addVertex(x, y, z).setColor(color.r, color.g, color.b, color.a).setLineWidth(OUTLINE_WIDTH);
		//#else
		builder.addVertex(x, y, z).setColor(color.r, color.g, color.b, color.a);
		//#endif
	}

	private static void addQuadVertex(BufferBuilder builder, float x, float y, float z, Color4f color) {
		builder.addVertex(x, y, z).setColor(color.r, color.g, color.b, color.a);
	}

	private static void draw(RenderContext context, MeshData mesh) {
		if (mesh == null) {
			return;
		}
		try {
			context.draw(mesh, false, true);
		} finally {
			mesh.close();
		}
	}

	private static void closeQuietly(RenderContext context) {
		try {
			context.close();
		} catch (Exception ignored) {
		}
	}
}
