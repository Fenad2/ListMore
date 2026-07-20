package com.listmore.render;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

//#if MC >= 26.1
//$$ import com.mojang.blaze3d.buffers.GpuBufferSlice;
//#endif
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.listmore.config.ListMoreConfigs;
import com.listmore.config.TntExplosionPreviewMode;

import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.MaLiLibPipelines;
import fi.dy.masa.malilib.render.RenderContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;

import net.minecraft.client.Camera;
//#if MC >= 26.1
//$$ import net.minecraft.client.DeltaTracker;
//#endif
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
//#if MC >= 26.1
//$$ import net.minecraft.client.renderer.state.level.CameraRenderState;
//#endif
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

//#if MC >= 26.1
//$$ import org.joml.Matrix4fc;
//$$ import org.joml.Vector4f;
//#else
import org.joml.Matrix4f;
//#endif

public final class TntExplosionPreviewRenderer implements IRenderer {
	private static final double RANGE = 64.0D;	//预览范围
	private static final double RANGE_SQUARED = RANGE * RANGE;
	private static final float TNT_POWER = 4.0F;
	private static final float MIN_POWER_MULTIPLIER = 0.7F;
	private static final float MAX_POWER_MULTIPLIER = 1.3F;	//why not?
	private static final float RAY_STEP = 0.30000001192092896F;
	private static final float RAY_DECAY = 0.22500001F;
	private static final int RAY_GRID_SIZE = 16;
	private static final float BOX_INSET = 0.16F;
	private static final Color4f CONSERVATIVE_FILL_COLOR = new Color4f(1.0F, 0.05F, 0.02F, 0.28F);
	private static final Color4f CONSERVATIVE_OUTLINE_COLOR = new Color4f(1.0F, 0.08F, 0.02F, 0.95F);
	private static final Color4f POSSIBLE_FILL_COLOR = new Color4f(1.0F, 0.78F, 0.02F, 0.25F);
	private static final Color4f POSSIBLE_OUTLINE_COLOR = new Color4f(1.0F, 0.82F, 0.05F, 0.90F);
	private static final ExplosionDamageCalculator DAMAGE_CALCULATOR = new ExplosionDamageCalculator();
	private static final TntExplosionPreviewRenderer INSTANCE = new TntExplosionPreviewRenderer();

	// 结果按游戏刻缓存
	private PreviewBlocks previewBlocks = PreviewBlocks.EMPTY;
	private long lastUpdateTick = Long.MIN_VALUE;

	private TntExplosionPreviewRenderer() {
	}

	public static TntExplosionPreviewRenderer getInstance() {
		return INSTANCE;
	}

	//#if MC >= 26.1
	//$$ @Override
	//$$ public void onExtractWorldLast(DeltaTracker deltaTracker, Camera camera, float partialTicks, ProfilerFiller profiler) {
	//$$ 	updatePreviews();
	//$$ }
	//#else
	@Override
	public void onRenderWorldLastAdvanced(RenderTarget framebuffer, Matrix4f posMatrix, Matrix4f projMatrix,
										Frustum frustum, Camera camera, RenderBuffers buffers, ProfilerFiller profiler) {
		updatePreviews();
		if (!this.previewBlocks.isEmpty()) {
			drawMarkers(this.previewBlocks);
		}
	}
	//#endif

	//#if MC >= 26.1
	//$$ @Override
	//$$ public void onRenderWorldLast(RenderTarget framebuffer, Matrix4fc modelViewMatrix, CameraRenderState cameraState,
	//$$ 								  Frustum culling, RenderBuffers buffers, GpuBufferSlice terrainFog,
	//$$ 								  Vector4f fogColor, ProfilerFiller profiler) {
	//$$ 	if (!this.previewBlocks.isEmpty()) {
	//$$ 		drawMarkers(this.previewBlocks);
	//$$ 	}
	//$$ }
	//#endif

	// 每个游戏刻扫描附近TNT并重新生成红黄预览
	private void updatePreviews() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null || !ListMoreConfigs.Generic.TNT_EXPLOSION_PREVIEW.getBooleanValue()) {
			clearResults();
			return;
		}

		long gameTime = level.getGameTime();
		if (gameTime == this.lastUpdateTick) {
			return;
		}

		Set<BlockPos> conservative = new HashSet<>();
		Set<BlockPos> possible = new HashSet<>();
		AABB searchBox = player.getBoundingBox().inflate(RANGE);
		List<PrimedTnt> tnts = level.getEntitiesOfClass(PrimedTnt.class, searchBox,
			tnt -> player.distanceToSqr(tnt) <= RANGE_SQUARED);

		for (PrimedTnt tnt : tnts) {
			Vec3 center = new Vec3(tnt.getX(), tnt.getY(0.0625D), tnt.getZ());
			conservative.addAll(calculateAffectedBlocks(level, center, TNT_POWER * MIN_POWER_MULTIPLIER));
			possible.addAll(calculateAffectedBlocks(level, center, TNT_POWER * MAX_POWER_MULTIPLIER));
		}

		possible.removeAll(conservative);
		this.previewBlocks = new PreviewBlocks(Set.copyOf(conservative), Set.copyOf(possible));
		this.lastUpdateTick = gameTime;
	}

	// 不可用时清空上一帧缓存
	private void clearResults() {
		this.previewBlocks = PreviewBlocks.EMPTY;
		this.lastUpdateTick = Long.MIN_VALUE;
	}

	// 按原版射线和威力衰减规则遍历受影响的方块
	private static Set<BlockPos> calculateAffectedBlocks(ClientLevel level, Vec3 center, float initialPower) {
		Set<BlockPos> affectedBlocks = new HashSet<>();
		for (int xIndex = 0; xIndex < RAY_GRID_SIZE; xIndex++) {
			for (int yIndex = 0; yIndex < RAY_GRID_SIZE; yIndex++) {
				for (int zIndex = 0; zIndex < RAY_GRID_SIZE; zIndex++) {
					if (xIndex != 0 && xIndex != RAY_GRID_SIZE - 1
						&& yIndex != 0 && yIndex != RAY_GRID_SIZE - 1
						&& zIndex != 0 && zIndex != RAY_GRID_SIZE - 1) {
						continue;
					}

					double directionX = xIndex / (double) (RAY_GRID_SIZE - 1) * 2.0D - 1.0D;
					double directionY = yIndex / (double) (RAY_GRID_SIZE - 1) * 2.0D - 1.0D;
					double directionZ = zIndex / (double) (RAY_GRID_SIZE - 1) * 2.0D - 1.0D;
					double length = Math.sqrt(directionX * directionX + directionY * directionY + directionZ * directionZ);
					directionX /= length;
					directionY /= length;
					directionZ /= length;

					float remainingPower = initialPower;
					double currentX = center.x;
					double currentY = center.y;
					double currentZ = center.z;
					while (remainingPower > 0.0F) {
						BlockPos position = BlockPos.containing(currentX, currentY, currentZ);
						if (!level.isInWorldBounds(position)) {
							break;
						}

						BlockState state = level.getBlockState(position);
						FluidState fluidState = level.getFluidState(position);
						// 获取当前坐标方块+流体的爆炸抗性值
						Optional<Float> resistance = DAMAGE_CALCULATOR.getBlockExplosionResistance(
							null, level, position, state, fluidState
						);
						// 剩余威力 = 原威力 - 0.3*(R + 0.3)
						if (resistance.isPresent()) {
							remainingPower -= 0.3F * (resistance.get() + 0.3F);
						}
						if (remainingPower > 0.0F && !state.isAir()) {
							// 存入待摧毁方块集合
							affectedBlocks.add(position.immutable());
						}

						currentX += directionX * RAY_STEP;
						currentY += directionY * RAY_STEP;
						currentZ += directionZ * RAY_STEP;
						remainingPower -= RAY_DECAY;
					}
				}
			}
		}
		return affectedBlocks;
	}

	// 半预览仅绘制红色范围，全预览额外绘制红黄
	private static void drawMarkers(PreviewBlocks blocks) {
		//#if MC >= 26.2
		//$$ Vec3 cameraPosition = Minecraft.getInstance().gameRenderer.mainCamera().position();
		//#elseif MC >= 12111
		//$$ Vec3 cameraPosition = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		//#else
		Vec3 cameraPosition = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		//#endif
		drawMarkers(blocks.conservative(), cameraPosition, "conservative", CONSERVATIVE_FILL_COLOR, CONSERVATIVE_OUTLINE_COLOR);
		if (ListMoreConfigs.Generic.TNT_EXPLOSION_PREVIEW_MODE.getOptionListValue() == TntExplosionPreviewMode.FULL) {
			drawMarkers(blocks.possible(), cameraPosition, "possible", POSSIBLE_FILL_COLOR, POSSIBLE_OUTLINE_COLOR);
		}
	}

	// 标记每个预览方块
	private static void drawMarkers(Set<BlockPos> positions, Vec3 cameraPosition, String type, Color4f fillColor, Color4f outlineColor) {
		if (positions.isEmpty()) {
			return;
		}

		RenderContext fillContext = new RenderContext(
			() -> "listmore:tnt_explosion_" + type + "_fill",
			MaLiLibPipelines.POSITION_COLOR_MASA_NO_DEPTH_NO_CULL
			//#if MC >= 26.2
			//$$ , 0
			//#endif
		);
		RenderContext outlineContext = new RenderContext(
			() -> "listmore:tnt_explosion_" + type + "_outline",
			MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL
			//#if MC >= 26.2
			//$$ , 0
			//#endif
		);
		//#if MC < 12111
		outlineContext.lineWidth(2.0F);
		//#endif

		try {
			BufferBuilder fillBuilder = fillContext.getBuilder();
			BufferBuilder outlineBuilder = outlineContext.getBuilder();
			for (BlockPos position : positions) {
				float minX = (float) (position.getX() + BOX_INSET - cameraPosition.x);
				float minY = (float) (position.getY() + BOX_INSET - cameraPosition.y);
				float minZ = (float) (position.getZ() + BOX_INSET - cameraPosition.z);
				float maxX = minX + 1.0F - BOX_INSET * 2.0F;
				float maxY = minY + 1.0F - BOX_INSET * 2.0F;
				float maxZ = minZ + 1.0F - BOX_INSET * 2.0F;

				RenderUtils.drawBoxAllSidesBatchedQuads(minX, minY, minZ, maxX, maxY, maxZ, fillColor, fillBuilder);
				RenderUtils.drawBoxAllEdgesBatchedLines(minX, minY, minZ, maxX, maxY, maxZ, outlineColor
					//#if MC >= 12111
					//$$ , 2.0F
					//#endif
					, outlineBuilder);
			}

			draw(fillContext, fillBuilder.build());
			draw(outlineContext, outlineBuilder.build());
		} finally {
			closeQuietly(fillContext);
			closeQuietly(outlineContext);
		}
	}

	// 绘制后立即释放
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

	private record PreviewBlocks(Set<BlockPos> conservative, Set<BlockPos> possible) {
		private static final PreviewBlocks EMPTY = new PreviewBlocks(Set.of(), Set.of());

		private boolean isEmpty() {
			return conservative.isEmpty() && possible.isEmpty();
		}
	}
}
