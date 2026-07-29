package com.listmore.render;

import java.util.ArrayList;
import java.util.List;

//#if MC >= 26.1
//$$ import com.mojang.blaze3d.buffers.GpuBufferSlice;
//#endif
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.listmore.config.ListMoreConfigs;
import com.listmore.utils.WorldRenderUtils;

import fi.dy.masa.malilib.interfaces.IRenderer;
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
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
//#if MC >= 26.1
//$$ import org.joml.Matrix4fc;
//$$ import org.joml.Vector4f;
//#endif

public final class InvalidFurnaceInputRenderer implements IRenderer {
	// 玩家停留在同一区块时限制扫描频率，避免每帧遍历附近所有熔炉
	private static final int SCAN_INTERVAL_TICKS = 10;
	private static final float BOX_INSET = 0.16F;
	private static final Color4f FILL_COLOR = new Color4f(1.0F, 0.23F, 0.02F, 0.28F);
	private static final Color4f OUTLINE_COLOR = new Color4f(1.0F, 0.29F, 0.04F, 0.95F);
	private static final InvalidFurnaceInputRenderer INSTANCE = new InvalidFurnaceInputRenderer();

	private List<BlockPos> invalidFurnaces = List.of();
	private long lastScanTick = Long.MIN_VALUE;
	private ChunkPos lastPlayerChunk;
	private int lastRange = -1;

	private InvalidFurnaceInputRenderer() {
	}

	// 获取渲染器的单例实例
	public static InvalidFurnaceInputRenderer getInstance() {
		return INSTANCE;
	}

	// 在世界渲染前更新当前范围内需要高亮的熔炉位置
	//#if MC >= 26.1
	//$$ @Override
	//$$ public void onExtractWorldLast(DeltaTracker deltaTracker, Camera camera, float partialTicks, ProfilerFiller profiler) {
	//$$ 	updateMarkers();
	//$$ }
	//#else
	@Override
	public void onRenderWorldLastAdvanced(RenderTarget framebuffer, Matrix4f posMatrix, Matrix4f projMatrix,
										Frustum frustum, Camera camera, RenderBuffers buffers, ProfilerFiller profiler) {
		updateMarkers();
		if (!this.invalidFurnaces.isEmpty()) {
			drawMarkers(this.invalidFurnaces);
		}
	}
	//#endif

	private void updateMarkers() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null || !ListMoreConfigs.Generic.INVALID_FURNACE_INPUT_HIGHLIGHTER.getBooleanValue()) {
			clearResults();
			return;
		}

		int range = ListMoreConfigs.Generic.INVALID_FURNACE_INPUT_HIGHLIGHTER_RANGE.getIntegerValue();
		//#if MC >= 26.1
		//$$ ChunkPos playerChunk = ChunkPos.containing(player.blockPosition());
		//#else
		ChunkPos playerChunk = new ChunkPos(player.blockPosition());
		//#endif
		long gameTime = level.getGameTime();
		if (playerChunk.equals(lastPlayerChunk) && range == lastRange && gameTime - lastScanTick < SCAN_INTERVAL_TICKS) {
			return;
		}

		this.invalidFurnaces = scan(level, getIntegratedServerLevel(client, level), playerChunk, range);
		this.lastScanTick = gameTime;
		this.lastPlayerChunk = playerChunk;
		this.lastRange = range;
	}

	// 在世界末尾阶段绘制已扫描到的高亮标记
	//#if MC >= 26.1
	//$$ @Override
	//$$ public void onRenderWorldLast(RenderTarget framebuffer, Matrix4fc modelViewMatrix, CameraRenderState cameraState,
	//$$ 								  Frustum culling, RenderBuffers buffers, GpuBufferSlice terrainFog,
	//$$ 								  Vector4f fogColor, ProfilerFiller profiler) {
	//$$ 	if (!this.invalidFurnaces.isEmpty()) {
	//$$ 		drawMarkers(this.invalidFurnaces);
	//$$ 	}
	//$$ }
	//#endif

	// 功能关闭或离开世界时清除缓存结果
	private void clearResults() {
		this.invalidFurnaces = List.of();
		this.lastScanTick = Long.MIN_VALUE;
		this.lastPlayerChunk = null;
		this.lastRange = -1;
	}

	// 获取与客户端维度对应的整合服务器世界
	private static ServerLevel getIntegratedServerLevel(Minecraft client, ClientLevel clientLevel) {
		// 客户端不会完整同步熔炉库存，单人模式从整合服务器读取实际输入槽数据
		IntegratedServer server = client.getSingleplayerServer();
		return server == null ? null : server.getLevel(clientLevel.dimension());
	}

	// 扫描范围内普通熔炉的输入槽，收集没有对应烧制配方的位置
	private static List<BlockPos> scan(ClientLevel clientLevel, ServerLevel serverLevel, ChunkPos center, int range) {
		RecipePropertySet furnaceInputs = clientLevel.recipeAccess().propertySet(RecipePropertySet.FURNACE_INPUT);
		List<BlockPos> results = new ArrayList<>();

		//#if MC >= 26.1
		//$$ int centerX = center.x();
		//$$ int centerZ = center.z();
		//#else
		int centerX = center.x;
		int centerZ = center.z;
		//#endif
		for (int chunkX = centerX - range; chunkX <= centerX + range; chunkX++) {
			for (int chunkZ = centerZ - range; chunkZ <= centerZ + range; chunkZ++) {
				LevelChunk chunk = getLoadedChunk(clientLevel, serverLevel, chunkX, chunkZ);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					if (!(blockEntity instanceof FurnaceBlockEntity furnace) || furnace.isRemoved()) {
						continue;
					}
					ItemStack input = furnace.getItem(0);
					if (!input.isEmpty() && !furnaceInputs.test(input)) {
						results.add(furnace.getBlockPos().immutable());
					}
				}
			}
		}

		return List.copyOf(results);
	}

	// 优先读取单人模式服务端区块，无法取得时使用客户端区块
	private static LevelChunk getLoadedChunk(ClientLevel clientLevel, ServerLevel serverLevel, int chunkX, int chunkZ) {
		// 仅扫描已加载区块，避免高亮功能改变区块加载状态
		if (serverLevel != null) {
			ChunkAccess serverChunk = serverLevel.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
			if (serverChunk instanceof LevelChunk levelChunk) {
				return levelChunk;
			}
		}
		return clientLevel.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
	}

	// 为每个目标熔炉绘制
	private static void drawMarkers(List<BlockPos> furnaces) {
		Vec3 cameraPosition = WorldRenderUtils.getCameraPosition();
		WorldRenderUtils.drawFilledOutlinedBlockBoxes(
			"listmore:invalid_furnace_input",
			furnaces,
			cameraPosition,
			BOX_INSET,
			FILL_COLOR,
			OUTLINE_COLOR
		);
	}
}
