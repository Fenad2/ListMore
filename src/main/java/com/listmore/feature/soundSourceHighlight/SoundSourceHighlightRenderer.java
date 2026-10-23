package com.listmore.feature.soundSourceHighlight;

//#if MC >= 26.3
//$$ import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
//#elseif MC >= 26.1
//$$ import com.mojang.blaze3d.buffers.GpuBufferSlice;
//#endif
import com.listmore.ListMore;
import com.listmore.config.ListMoreConfigs;
import com.listmore.utils.BlockHighlightGeometryUtils;
import com.listmore.utils.BlockHighlightRenderUtils;
import com.listmore.utils.WorldRenderUtils;
import com.mojang.blaze3d.pipeline.RenderTarget;

import fi.dy.masa.malilib.interfaces.IRenderer;
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
	private final BlockHighlightGeometryUtils blockGeometry = new BlockHighlightGeometryUtils();
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

			BlockHighlightRenderUtils.drawMergedFill(BLOCK_FILL_ID, this.blockGeometry, cameraPosition, fillColor);
			BlockHighlightRenderUtils.drawMergedOutline(BLOCK_OUTLINE_ID, this.blockGeometry, cameraPosition, outlineColor);
		} catch (Throwable throwable) {
			long now = System.currentTimeMillis();

			if (now - lastErrorLogTime > ERROR_LOG_INTERVAL_MS) {
				lastErrorLogTime = now;
				ListMore.LOGGER.error("Failed to draw sound source highlight boxes", throwable);
			}
		}
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
