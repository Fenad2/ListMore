package com.listmore.feature.soundSourceHighlight;

//#if MC >= 26.3
//$$ import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
//#elseif MC >= 26.1
//$$ import com.mojang.blaze3d.buffers.GpuBufferSlice;
//#endif
import com.listmore.ListMore;
import com.listmore.config.ListMoreConfigs;
import com.listmore.utils.WorldRenderUtils;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.BufferBuilder;

import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.RenderContext;
import fi.dy.masa.malilib.util.data.Color4f;

import net.minecraft.client.Camera;
//#if MC >= 26.1
//$$ import net.minecraft.client.DeltaTracker;
//#endif
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
//#if MC >= 26.1
//$$ import net.minecraft.client.renderer.state.level.CameraRenderState;
//#endif
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
//#if MC >= 26.1 && MC < 26.3
//$$ import org.joml.Matrix4fc;
//$$ import org.joml.Vector4f;
//#elseif MC >= 26.3
//$$ import org.joml.Vector4f;
//#else
import org.joml.Matrix4f;
//#endif

import java.util.Set;

public final class SoundSourceHighlightRenderer implements IRenderer {
	private static final long ERROR_LOG_INTERVAL_MS = 5000L;
	private static final String BLOCK_FILL_ID = "listmore:sound_source_highlight_fill";
	private static final String BLOCK_OUTLINE_ID = "listmore:sound_source_highlight_outline";
	private static final SoundSourceHighlightRenderer INSTANCE = new SoundSourceHighlightRenderer();
	private static long lastErrorLogTime;
	private final BlockHighlightGeometry blockGeometry = new BlockHighlightGeometry();
	private Set<BlockPos> highlightedPositions = Set.of();
	private ClientLevel trackedLevel;
	private long lastGeometryTick = Long.MIN_VALUE;
	private SoundSourceHighlightRenderer() {
	}

	public static SoundSourceHighlightRenderer getInstance() {
		return INSTANCE;
	}

	//#if MC >= 26.1
	//$$ @Override
	//$$ public void onExtractWorldLast(DeltaTracker deltaTracker, Camera camera, float partialTicks, ProfilerFiller profiler) {
	//$$ 	SoundSourceHighlightManager.getInstance().tick();
	//$$ }
	//#else
	@Override
	public void onRenderWorldLastAdvanced(RenderTarget framebuffer, Matrix4f posMatrix, Matrix4f projMatrix,
										Frustum frustum, Camera camera, RenderBuffers buffers, ProfilerFiller profiler) {
		drawHighlights();
	}
	//#endif

	//#if MC >= 26.3
	//$$ @Override
	//$$ public void onRenderWorldLast(RenderTarget framebuffer, CameraRenderState cameraState,
	//$$ 								  Frustum culling, RenderBuffers buffers, GpuBufferSlice terrainFog,
	//$$ 								  Vector4f fogColor, ProfilerFiller profiler) {
	//$$ 	drawHighlights();
	//$$ }
	//#elseif MC >= 26.1
	//$$ @Override
	//$$ public void onRenderWorldLast(RenderTarget framebuffer, Matrix4fc modelViewMatrix, CameraRenderState cameraState,
	//$$ 								  Frustum culling, RenderBuffers buffers, GpuBufferSlice terrainFog,
	//$$ 								  Vector4f fogColor, ProfilerFiller profiler) {
	//$$ 	drawHighlights();
	//$$ }
	//#endif

	private void drawHighlights() {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;

		if (level == null) {
			this.clearGeometry();
			return;
		}

		try {
			long gameTime = level.getGameTime();

			if (level != this.trackedLevel || gameTime != this.lastGeometryTick) {
				this.trackedLevel = level;
				this.lastGeometryTick = gameTime;
				Set<BlockPos> positions = SoundSourceHighlightManager.getInstance().getBlockPositions();

				if (!positions.equals(this.highlightedPositions)) {
					this.highlightedPositions = positions;
					this.blockGeometry.rebuild(positions);
				}
			}

			if (this.highlightedPositions.isEmpty()) {
				return;
			}

			Color4f outlineColor = getBlockOutlineColor();
			Color4f fillColor = outlineColor.withAlpha(
				ListMoreConfigs.Generic.SOUND_SOURCE_HIGHLIGHT_BLOCK_OPACITY.getFloatValue()
			);
			Vec3 cameraPosition = WorldRenderUtils.getCameraPosition();

			drawMergedFill(cameraPosition, fillColor);
			drawMergedOutline(cameraPosition, outlineColor);
		} catch (Throwable throwable) {
			long now = System.currentTimeMillis();

			if (now - lastErrorLogTime > ERROR_LOG_INTERVAL_MS) {
				lastErrorLogTime = now;
				ListMore.LOGGER.error("Failed to draw sound source highlight boxes", throwable);
			}
		}
	}

	// 只画并集的暴露面
	private void drawMergedFill(Vec3 cameraPosition, Color4f color) {
		if (this.blockGeometry.faceCount() == 0 || color.a <= 0.0F) {
			return;
		}

		RenderContext context = WorldRenderUtils.createFillContext(BLOCK_FILL_ID);
		try {
			BufferBuilder builder = context.getBuilder();
			int[] faces = this.blockGeometry.faces();
			int faceCount = this.blockGeometry.faceCount();

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
			WorldRenderUtils.draw(context, builder.build());
		} finally {
			WorldRenderUtils.closeQuietly(context);
		}
	}

	// 只画体素并集的硬边
	private void drawMergedOutline(Vec3 cameraPosition, Color4f color) {
		if (this.blockGeometry.edgeCount() == 0 || color.a <= 0.0F) {
			return;
		}

		RenderContext context = WorldRenderUtils.createOutlineContext(BLOCK_OUTLINE_ID);
		try {
			BufferBuilder builder = context.getBuilder();
			int[] edges = this.blockGeometry.edges();
			int edgeCount = this.blockGeometry.edgeCount();

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

	private void clearGeometry() {
		this.highlightedPositions = Set.of();
		this.blockGeometry.clear();
		this.trackedLevel = null;
		this.lastGeometryTick = Long.MIN_VALUE;
	}

	private static Color4f getBlockOutlineColor() {
		return Color4f.fromColor(ListMoreConfigs.Generic.SOUND_SOURCE_HIGHLIGHT_BLOCK_COLOR.getIntegerValue());
	}
}
