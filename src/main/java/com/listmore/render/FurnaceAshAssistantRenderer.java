package com.listmore.render;

import java.util.ArrayList;
import java.util.List;

//#if MC >= 26.1
//$$ import com.mojang.blaze3d.buffers.GpuBufferSlice;
//#endif
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.listmore.config.ListMoreConfigs;

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

public final class FurnaceAshAssistantRenderer implements IRenderer {
	// 玩家停留在同一区块时限制扫描频率，避免每帧遍历附近所有熔炉
	private static final int SCAN_INTERVAL_TICKS = 10;
	private static final float BOX_INSET = 0.16F;
	private static final Color4f FILL_COLOR = new Color4f(1.0F, 0.23F, 0.02F, 0.28F);
	private static final Color4f OUTLINE_COLOR = new Color4f(1.0F, 0.29F, 0.04F, 0.95F);
	private static final FurnaceAshAssistantRenderer INSTANCE = new FurnaceAshAssistantRenderer();

	private List<BlockPos> invalidFurnaces = List.of();
	private long lastScanTick = Long.MIN_VALUE;
	private ChunkPos lastPlayerChunk;
	private int lastRange = -1;

	private FurnaceAshAssistantRenderer() {
	}

	// 获取渲染器的单例实例
	public static FurnaceAshAssistantRenderer getInstance() {
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
		if (player == null || level == null || !ListMoreConfigs.Generic.FURNACE_ASH_ASSISTANT.getBooleanValue()) {
			clearResults();
			return;
		}

		int range = ListMoreConfigs.Generic.FURNACE_ASH_ASSISTANT_RANGE.getIntegerValue();
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
		//#if MC >= 26.2
		//$$ Vec3 cameraPosition = Minecraft.getInstance().gameRenderer.mainCamera().position();
		//#elseif MC >= 12111
		//$$ Vec3 cameraPosition = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		//#else
		Vec3 cameraPosition = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		//#endif
		RenderContext fillContext = new RenderContext(
			() -> "listmore:furnace_ash_fill",
			MaLiLibPipelines.POSITION_COLOR_MASA_NO_DEPTH_NO_CULL
			//#if MC >= 26.2
			//$$ , 0
			//#endif
		);
		RenderContext outlineContext = new RenderContext(
			() -> "listmore:furnace_ash_outline",
			MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL
			//#if MC >= 26.2
			//$$ , 0
			//#endif
		);
		//#if MC < 12111
		outlineContext.lineWidth(2.0F);
		//#endif

		try {
			// 世界末尾渲染使用相对于当前相机的坐标，使标记与视角保持正确对应
			BufferBuilder fillBuilder = fillContext.getBuilder();
			BufferBuilder outlineBuilder = outlineContext.getBuilder();
			for (BlockPos furnace : furnaces) {
				float minX = (float) (furnace.getX() + BOX_INSET - cameraPosition.x);
				float minY = (float) (furnace.getY() + BOX_INSET - cameraPosition.y);
				float minZ = (float) (furnace.getZ() + BOX_INSET - cameraPosition.z);
				float maxX = minX + 1.0F - BOX_INSET * 2.0F;
				float maxY = minY + 1.0F - BOX_INSET * 2.0F;
				float maxZ = minZ + 1.0F - BOX_INSET * 2.0F;

				RenderUtils.drawBoxAllSidesBatchedQuads(minX, minY, minZ, maxX, maxY, maxZ, FILL_COLOR, fillBuilder);
				RenderUtils.drawBoxAllEdgesBatchedLines(minX, minY, minZ, maxX, maxY, maxZ, OUTLINE_COLOR
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

	// 提交网格并在绘制完成后释放资源
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

	// 渲染上下文关闭失败时不影响后续渲染
	private static void closeQuietly(RenderContext context) {
		try {
			context.close();
		} catch (Exception ignored) {
		}
	}
}
