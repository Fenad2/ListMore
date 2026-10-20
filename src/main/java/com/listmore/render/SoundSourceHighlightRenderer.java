package com.listmore.render;

//#if MC >= 26.3
//$$ import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
//#elseif MC >= 26.1
//$$ import com.mojang.blaze3d.buffers.GpuBufferSlice;
//#endif
import com.listmore.ListMore;
import com.listmore.config.ListMoreConfigs;
import com.listmore.feature.SoundSourceHighlightManager;
import com.listmore.utils.WorldRenderUtils;
import com.mojang.blaze3d.pipeline.RenderTarget;

import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.util.data.Color4f;

import net.minecraft.client.Camera;
//#if MC >= 26.1
//$$ import net.minecraft.client.DeltaTracker;
//#endif
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
	private static final float BLOCK_INSET = 0.002F;
	private static final long ERROR_LOG_INTERVAL_MS = 5000L;
	private static final SoundSourceHighlightRenderer INSTANCE = new SoundSourceHighlightRenderer();
	private static long lastErrorLogTime;

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

	private static void drawHighlights() {
		Set<BlockPos> positions = SoundSourceHighlightManager.getInstance().getBlockPositions();

		if (positions.isEmpty()) {
			return;
		}

		Color4f outlineColor = getBlockOutlineColor();
		Color4f fillColor = new Color4f(
			outlineColor.r,
			outlineColor.g,
			outlineColor.b,
			outlineColor.a * 0.3F
		);
		Vec3 cameraPosition = WorldRenderUtils.getCameraPosition();

		try {
			WorldRenderUtils.drawFilledOutlinedBlockBoxes(
				"listmore:sound_source_highlight",
				positions,
				cameraPosition,
				BLOCK_INSET,
				fillColor,
				outlineColor
			);
		} catch (Throwable throwable) {
			long now = System.currentTimeMillis();

			if (now - lastErrorLogTime > ERROR_LOG_INTERVAL_MS) {
				lastErrorLogTime = now;
				ListMore.LOGGER.error("Failed to draw sound source highlight boxes", throwable);
			}
		}
	}

	private static Color4f getBlockOutlineColor() {
		return Color4f.fromColor(ListMoreConfigs.Generic.SOUND_SOURCE_HIGHLIGHT_BLOCK_COLOR.getIntegerValue());
	}
}
