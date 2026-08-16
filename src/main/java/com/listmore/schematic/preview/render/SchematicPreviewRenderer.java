package com.listmore.schematic.preview.render;

import com.listmore.ListMore;
import com.listmore.schematic.preview.SchematicPreviewModel;
import com.listmore.schematic.preview.SchematicPreviewTransform;
import com.listmore.schematic.preview.gui.SchematicPreviewLayout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

//#if MC >= 26.2
//$$ import com.mojang.blaze3d.IndexType;
//$$ import com.mojang.blaze3d.PrimitiveTopology;
//$$ import com.mojang.blaze3d.GpuFormat;
//#else
import com.mojang.blaze3d.vertex.VertexFormat.IndexType;
//#endif
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
//#if MC >= 1.21.11
//$$ import com.mojang.blaze3d.textures.GpuSampler;
//#endif
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
//#if MC < 26.2
import com.mojang.blaze3d.vertex.VertexFormat;
//#endif
//#if MC >= 26.1
//$$ import fi.dy.masa.litematica.render.schematic.BlockModelRendererSchematic;
//$$ import fi.dy.masa.litematica.render.schematic.IBlockOutputSchematic;
//$$ import fi.dy.masa.litematica.schematic.LitematicaSchematic;
//#else
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.util.RandomSource;
//#endif
//#if MC >= 1.21.11
//$$ import fi.dy.masa.malilib.render.GuiContext;
//#else
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.GlobalSettingsUniform;
//#if MC >= 26.1
//$$ import net.minecraft.client.renderer.Projection;
//#endif
//#if MC >= 26.1
//$$ import net.minecraft.client.renderer.block.FluidRenderer;
//$$ import net.minecraft.client.renderer.ProjectionMatrixBuffer;
//#else
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
//#endif
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
//#if MC >= 26.1
//$$ import net.minecraft.client.renderer.state.gui.BlitRenderState;
//#else
import net.minecraft.client.gui.render.state.BlitRenderState;
//#endif
import net.minecraft.client.renderer.texture.TextureAtlas;
//#if MC >= 26.1
//$$ import net.minecraft.world.phys.Vec3;
//#endif
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix3x2f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

public final class SchematicPreviewRenderer implements SchematicPreviewRenderBackend {
	private final List<ChunkMesh> meshes = new ArrayList<>();
	private long builtRevision = Long.MIN_VALUE;
	//#if MC >= 26.1
	//$$ private LitematicaSchematic schematic;
	//#endif
	private SchematicPreviewWorld world;
	private TextureTarget target;
	//#if MC >= 1.21.11
	//$$ private GpuSampler sampler;
	//#endif
	//#if MC >= 26.1
	//$$ private ProjectionMatrixBuffer projection;
	//#else
	private PerspectiveProjectionMatrixBuffer projection;
	//#endif
//#if MC >= 26.1
	//$$ private final Projection previewProjection = new Projection();
//#endif
	private GpuBuffer globalUniform;
	private boolean rebuildFailureLogged;
	private boolean renderFailureLogged;
	//#if MC >= 26.1
	//$$ private final FluidRenderer fluidRenderer = new FluidRenderer(Minecraft.getInstance().getModelManager().getFluidStateModelSet());
	//#endif
	@Override
	public void setSchematic(Object schematic) {
		//#if MC >= 26.1
		//$$ this.schematic = schematic instanceof LitematicaSchematic loaded ? loaded : null;
		//#endif
	}

	// 将原理图模型渲染到离屏帧缓冲，再将结果贴图绘制到 GUI 面板
	// 流程：校验模型 -> 确保帧缓冲尺寸 -> 增量重建网格 -> 清空帧缓冲 -> 3D 渲染 -> 贴图到 GUI
	public boolean render(Object rawContext, SchematicPreviewLayout layout, SchematicPreviewTransform transform,
			SchematicPreviewModel model, long revision) {
		//#if MC >= 1.21.11
		//$$ GuiContext context = (GuiContext) rawContext;
		//#else
		GuiGraphics context = (GuiGraphics) rawContext;
		//#endif
		if (model == null || model.blocks().isEmpty() || layout.contentWidth() <= 0 || layout.contentHeight() <= 0) {
			return false;
		}
		// 确保离屏帧缓冲尺寸与当前预览区域匹配
		this.ensureTarget(layout.contentWidth(), layout.contentHeight());
		// 仅当模型快照版本号变化时才重建网格，避免每帧重建
		if (this.builtRevision != revision) {
			try {
				this.rebuild(model, revision);
				this.rebuildFailureLogged = false;
			} catch (Throwable throwable) {
				if (!this.rebuildFailureLogged) {
					ListMore.LOGGER.error("Failed to build schematic preview meshes", throwable);
					this.rebuildFailureLogged = true;
				}
				return false;
			}
		}
		if (this.meshes.isEmpty()) {
			return false;
		}

		// 清空帧缓冲的颜色和深度，背景色为0xFF1B2028
		//#if MC >= 26.2
		//$$ RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
		//$$ 		this.target.getColorTexture(), new org.joml.Vector4f(0.105F, 0.125F, 0.157F, 1.0F),
		//$$ 		this.target.getDepthTexture(), 0.0D);
		//#else
		RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
				this.target.getColorTexture(), 0xFF1B2028, this.target.getDepthTexture(), 1.0D);
		//#endif
		try {
			// 3D渲染阶段：设置相机投影并绘制所有网格到离屏帧缓冲
			this.renderTarget(model, transform);
			this.renderFailureLogged = false;
		} catch (Throwable throwable) {
			if (!this.renderFailureLogged) {
				ListMore.LOGGER.error("Failed to render schematic preview", throwable);
				this.renderFailureLogged = true;
			}
			return false;
		}
		// 将离屏帧缓冲的纹理贴图到 GUI 面板的指定区域
		BlitRenderState blit = new BlitRenderState(
				RenderPipelines.GUI_TEXTURED,
				//#if MC >= 1.21.11
				//$$ TextureSetup.singleTexture(this.target.getColorTextureView(), this.sampler),
				//#else
				TextureSetup.singleTexture(this.target.getColorTextureView()),
				//#endif
				new Matrix3x2f(context.pose()),
				layout.contentX(), layout.contentY(),
				layout.contentX() + layout.contentWidth(), layout.contentY() + layout.contentHeight(),
				0.0F, 1.0F, 1.0F, 0.0F, 0xFFFFFFFF, context.scissorStack.peek());
		//#if MC >= 1.21.11
		//$$ context.addSimpleElement(blit);
		//#else
		context.guiRenderState.submitBlitToCurrentLayer(blit);
		//#endif
		return true;
	}

	private void ensureTarget(int width, int height) {
		int scaledWidth = Math.max(1, Math.round(width * Minecraft.getInstance().getWindow().getGuiScale()));
		int scaledHeight = Math.max(1, Math.round(height * Minecraft.getInstance().getWindow().getGuiScale()));
		if (this.target == null) {
			//#if MC >= 26.2
			//$$ this.target = new TextureTarget("ListMore schematic preview", scaledWidth, scaledHeight, true, GpuFormat.RGBA8_UNORM);
			//#else
			this.target = new TextureTarget("ListMore schematic preview", scaledWidth, scaledHeight, true);
			//#endif
		} else if (this.target.width != scaledWidth || this.target.height != scaledHeight) {
			this.target.resize(scaledWidth, scaledHeight);
		}
		//#if MC >= 1.21.11
		//$$ if (this.sampler == null) {
		//$$ 	this.sampler = RenderSystem.getDevice().createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
		//$$ 			FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
		//$$ }
		//#endif
	}

	// 从原理图模型重建所有网格数据并上传到 GPU
	// 流程：释放旧网格 -> 创建虚拟世界视图 -> 遍历方块按区块分组 -> 调用原版渲染器生成网格 -> 构建 GPU 缓冲区
	private void rebuild(SchematicPreviewModel model, long revision) {
		this.closeMeshes();
		this.builtRevision = revision;
		//#if MC >= 26.1
		//$$ if (this.schematic == null) {
		//$$ 	return;
		//$$ }
		//#endif
		if (this.world == null) {
			this.world = new SchematicPreviewWorld(Minecraft.getInstance());
		}
		this.world.setModel(model);
		SchematicPreviewWorld view = this.world;
		//#if MC >= 26.1
		//$$ ModelManager modelManager = Minecraft.getInstance().getModelManager();
		//#endif
		// 按区块位置分组构建网格，每个 ChunkPos 对应一个 ChunkMeshBuilder
		Map<ChunkPos, ChunkMeshBuilder> chunks = new HashMap<>();
		//#if MC >= 26.1
		//$$ BlockModelRendererSchematic renderer = new BlockModelRendererSchematic();
		//$$ renderer.enableCache();
		//#endif
		try {
			// 遍历所有非空气方块，分别渲染流体和固体模型
			for (SchematicPreviewModel.Block block : model.blocks()) {
				BlockState state = block.state();
				BlockPos position = new BlockPos(block.x(), block.y(), block.z());
				// >> 4 将方块坐标转为区块坐标，computeIfAbsent 保证每个区块只有一个 Builder
				ChunkMeshBuilder chunk = chunks.computeIfAbsent(new ChunkPos(block.x() >> 4, block.z() >> 4), ignored -> new ChunkMeshBuilder());
				// 先渲染流体（水、岩浆等），再渲染固体方块模型
				//#if MC >= 26.1
				//$$ if (!state.getFluidState().isEmpty()) {
				//$$ 	this.fluidRenderer.tesselate(view, position, chunk::builder, state, state.getFluidState());
				//$$ }
				//#else
				if (!state.getFluidState().isEmpty()) {
					Minecraft.getInstance().getBlockRenderer().renderLiquid(position, view,
							chunk.builder(ItemBlockRenderTypes.getRenderLayer(state.getFluidState())), state, state.getFluidState());
				}
				//#endif
				if (state.getRenderShape() != RenderShape.MODEL) {
					continue;
				}
				//#if MC >= 26.1
				//$$ IBlockOutputSchematic output = (x, y, z, quad, instance) -> chunk.builder(quad.materialInfo().layer())
				//$$ 		.putBlockBakedQuad(x, y, z, quad, instance);
				//$$ renderer.tessellateBlock(view, state, position, new Vec3(block.x() & 15, block.y(), block.z() & 15),
				//$$ 		modelManager.getBlockStateModelSet().get(state), state.getSeed(position), output);
				//#else
				PoseStack pose = new PoseStack();
				// & 15 取区块内局部坐标（0-15），模型顶点需要相对区块原点的偏移
				pose.translate(block.x() & 15, block.y(), block.z() & 15);
				Minecraft.getInstance().getBlockRenderer().renderBatched(state, position, view, pose,
						chunk.builder(ItemBlockRenderTypes.getChunkRenderType(state)), true,
						Minecraft.getInstance().getBlockRenderer().getBlockModel(state)
								.collectParts(RandomSource.create(state.getSeed(position))));
				//#endif
			}
		} finally {
			//#if MC >= 26.1
			//$$ renderer.disableCache();
			//#endif
		}
		// 所有方块遍历完毕后，将每个区块的网格数据上传到 GPU
		chunks.forEach((position, chunk) -> {
			ChunkMesh built = chunk.build(position);
			if (built != null) {
				this.meshes.add(built);
			}
		});
	}

	// 设置相机、投影矩阵和全局 Uniform，然后将所有网格渲染到离屏帧缓冲
	// 相机位置根据偏航角/俯仰角/缩放计算，围绕模型包围盒的中心旋转
	private void renderTarget(SchematicPreviewModel model, SchematicPreviewTransform transform) {
		// 计算相机在模型包围球上的位置
		// 球坐标 -> 空间坐标：x = centerX - sin(yaw)*cos(pitch)*dist, y = centerY - sin(pitch)*dist, z = centerZ + cos(yaw)*cos(pitch)*dist
		float distance = transform.distance(model.size().getX(), model.size().getY(), model.size().getZ());
		float yaw = transform.yaw() * Mth.DEG_TO_RAD;
		float pitch = transform.pitch() * Mth.DEG_TO_RAD;
		float horizontal = Mth.cos(pitch);
		float sinYaw = Mth.sin(yaw);
		float cosYaw = Mth.cos(yaw);
		Vector3f camera = new Vector3f(
				model.centerX() - sinYaw * horizontal * distance,
				model.centerY() - Mth.sin(pitch) * distance,
				model.centerZ() + cosYaw * horizontal * distance);
		float panScale = 2.0F * distance * (float) Math.tan(35.0F * Mth.DEG_TO_RAD);
		Vector3f screenRight = new Vector3f(cosYaw, 0.0F, sinYaw);
		Vector3f screenUp = new Vector3f(-sinYaw * Mth.sin(pitch), horizontal,
				cosYaw * Mth.sin(pitch));
		camera.add(screenRight.mul(-transform.panX() * panScale))
				.add(screenUp.mul(transform.panY() * panScale));
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		// 与 SchematicPreview 的轨道约定一致：UI 偏航角描述观察方向
		// 而地形视图矩阵使用相机偏航角的逆，因此取 -yaw 并共轭
		Quaternionf rotation = new Quaternionf().rotationYXZ(-yaw, pitch, 0.0F).conjugate();
		modelView.set(new Matrix4f().rotation(rotation));
		// 构建透视投影矩阵，近平面 0.05，远平面 4096，FOV 70°
		//#if MC >= 26.1
		//$$ this.previewProjection.setupPerspective(0.05F, 4096.0F, 70.0F,
		//$$ 		this.target.width, this.target.height);
		//$$ Matrix4f projectionMatrix = this.previewProjection.getMatrix(new Matrix4f());
		//#else
		Matrix4f projectionMatrix = new Matrix4f().perspective(70.0F * Mth.DEG_TO_RAD,
				(float) this.target.width / this.target.height, 0.05F, 4096.0F);
		//#endif
		// 备份原版投影矩阵和全局 Uniform，渲染完成后恢复
		RenderSystem.backupProjectionMatrix();
		if (this.projection == null) {
			//#if MC >= 26.1
			//$$ this.projection = new ProjectionMatrixBuffer("ListMore schematic preview");
			//#else
			this.projection = new PerspectiveProjectionMatrixBuffer("ListMore schematic preview");
			//#endif
		}
		RenderSystem.setProjectionMatrix(this.projection.getBuffer(projectionMatrix), ProjectionType.PERSPECTIVE);
		GpuBuffer previousGlobalUniform = RenderSystem.getGlobalSettingsUniform();
		this.writeGlobalUniform(camera);
		try {
			//#if MC >= 26.2
			//$$ Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.LEVEL);
			//#else
			Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.LEVEL);
			//#endif
			this.renderMeshes(camera);
		} finally {
			RenderSystem.setGlobalSettingsUniform(previousGlobalUniform);
			RenderSystem.restoreProjectionMatrix();
			modelView.popMatrix();
		}
	}

	// 向 GPU 写入全局 Uniform 缓冲区，包含相机位置和窗口尺寸
	// 使用 Std140 布局，与着色器中的 GlobalSettings 结构体对齐
	private void writeGlobalUniform(Vector3f camera) {
		if (this.globalUniform == null) {
			this.globalUniform = RenderSystem.getDevice().createBuffer(() -> "ListMore preview global settings",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, GlobalSettingsUniform.UBO_SIZE);
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			//#if MC >= 1.21.11
			//$$ int floorX = Mth.floor(camera.x);
			//$$ int floorY = Mth.floor(camera.y);
			//$$ int floorZ = Mth.floor(camera.z);
			//$$ RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.globalUniform.slice(),
			//$$ 		Std140Builder.onStack(stack, GlobalSettingsUniform.UBO_SIZE)
			//$$ 				.putIVec3(floorX, floorY, floorZ)
			//$$ 				.putVec3(floorX - camera.x, floorY - camera.y, floorZ - camera.z)
			//$$ 				.putVec2(Minecraft.getInstance().getWindow().getWidth(), Minecraft.getInstance().getWindow().getHeight())
			//$$ 				.putFloat(1.0F).putFloat(0.0F).putInt(0).putInt(0)
			//$$ 				.get());
			//#else
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.globalUniform.slice(),
					Std140Builder.onStack(stack, GlobalSettingsUniform.UBO_SIZE)
							.putVec2(Minecraft.getInstance().getWindow().getWidth(), Minecraft.getInstance().getWindow().getHeight())
							.putFloat(1.0F).putFloat(0.0F).putInt(0)
							.get());
			//#endif
		}
		RenderSystem.setGlobalSettingsUniform(this.globalUniform);
	}

	// 按距离排序所有网格，为每个网格构建 Transform/SectionInfo Uniform，然后分不透明/半透明两层渲染
	private void renderMeshes(Vector3f camera) {
		var atlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
		List<ChunkMesh> orderedMeshes = new ArrayList<>(this.meshes);
		// 按距离降序排列（远的先画），负号使 Comparator 从大到小
		orderedMeshes.sort(Comparator.comparingDouble(mesh -> -mesh.distanceTo(camera)));
		//#if MC >= 1.21.11
		//$$ DynamicUniforms.ChunkSectionInfo[] infos = new DynamicUniforms.ChunkSectionInfo[orderedMeshes.size()];
		//$$ for (int i = 0; i < orderedMeshes.size(); i++) {
		//$$ 	ChunkMesh mesh = orderedMeshes.get(i);
		//$$ 	infos[i] = new DynamicUniforms.ChunkSectionInfo(
			//#if MC >= 26.2
			//$$ 			RenderSystem.getModelViewMatrixCopy(),
			//#else
		//$$ 			new Matrix4f(RenderSystem.getModelViewMatrix()),
			//#endif
		//$$ 			mesh.chunk().getMinBlockX(), 0,
		//$$ 			mesh.chunk().getMinBlockZ(), 1.0F, atlas.getWidth(0), atlas.getHeight(0));
		//$$ }
		//$$ GpuBufferSlice[] sectionUniforms = RenderSystem.getDynamicUniforms().writeChunkSections(infos);
		//#else
		DynamicUniforms.Transform[] transforms = new DynamicUniforms.Transform[orderedMeshes.size()];
		for (int i = 0; i < orderedMeshes.size(); i++) {
			ChunkMesh mesh = orderedMeshes.get(i);
			//#if MC < 1.21.11
			transforms[i] = new DynamicUniforms.Transform(new Matrix4f(RenderSystem.getModelViewMatrix()),
					new Vector4f(1.0F), new Vector3f(mesh.chunk().getMinBlockX() - camera.x, -camera.y,
					mesh.chunk().getMinBlockZ() - camera.z), new Matrix4f(), 1.0F);
			//#else
			//$$ transforms[i] = new DynamicUniforms.Transform(new Matrix4f(RenderSystem.getModelViewMatrix()),
			//$$ 		new Vector4f(1.0F), new Vector3f(mesh.chunk().getMinBlockX() - camera.x, -camera.y,
			//$$ 		mesh.chunk().getMinBlockZ() - camera.z), new Matrix4f());
			//#endif
		}
		GpuBufferSlice[] transformUniforms = RenderSystem.getDynamicUniforms().writeTransforms(transforms);
		//#endif
		// 获取顺序索引缓冲区，供没有独立索引缓冲的网格共享使用
		RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(
				//#if MC >= 26.2
				//$$ PrimitiveTopology.QUADS
				//#else
				VertexFormat.Mode.QUADS
				//#endif
		);
		// 找出所有网格中最大的顺序索引需求，确保共享缓冲区足够大
		int maxSequentialIndices = 0;
		for (ChunkMesh chunk : orderedMeshes) {
			for (SectionBuffers mesh : chunk.buffers().values()) {
				if (mesh.indexBuffer() == null) {
					maxSequentialIndices = Math.max(maxSequentialIndices, mesh.indexCount());
				}
			}
		}
		GpuBuffer sharedIndex = maxSequentialIndices == 0 ? null : sequential.getBuffer(maxSequentialIndices);
		IndexType sharedIndexType = maxSequentialIndices == 0 ? null : sequential.type();
		// 先画不透明层（reverse=false），再画半透明层（reverse=true，从远到近保证混合正确）
		//#if MC >= 1.21.11
		//$$ this.renderLayerGroup(orderedMeshes, sectionUniforms, atlas, sharedIndex, sharedIndexType,
		//$$ 		ChunkSectionLayerGroup.OPAQUE, false);
		//$$ this.renderLayerGroup(orderedMeshes, sectionUniforms, atlas, sharedIndex, sharedIndexType,
		//$$ 		ChunkSectionLayerGroup.TRANSLUCENT, true);
		//#else
		this.renderLayerGroup(orderedMeshes, transformUniforms, atlas, sharedIndex, sharedIndexType,
				ChunkSectionLayerGroup.OPAQUE, false);
		this.renderLayerGroup(orderedMeshes, transformUniforms, atlas, sharedIndex, sharedIndexType,
				ChunkSectionLayerGroup.TRANSLUCENT, true);
		//#endif
	}

	// 为指定的渲染层组（不透明或半透明）创建 RenderPass 并绘制所有网格
	// 每个 ChunkSectionLayer 对应一种渲染管线（如 solid、cutout、translucent）
	// reverse 参数控制绘制顺序：半透明层需要从远到近绘制以保证混合正确
	private void renderLayerGroup(List<ChunkMesh> orderedMeshes, GpuBufferSlice[] meshUniforms,
			GpuTextureView atlas, GpuBuffer sharedIndex, IndexType sharedIndexType,
			ChunkSectionLayerGroup group, boolean reverse) {
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> "ListMore schematic preview " + group.label(), this.target.getColorTextureView(),
				//#if MC >= 26.2
				//$$ Optional.empty(),
				//#else
				OptionalInt.empty(),
				//#endif
				this.target.getDepthTextureView(), OptionalDouble.empty())) {
			RenderSystem.bindDefaultUniforms(pass);
			// 绑定光照纹理（Sampler2），所有渲染管线都需要
			//#if MC >= 26.1
			//$$ pass.bindTexture("Sampler2", Minecraft.getInstance().gameRenderer.lightmap(), this.sampler);
			//#elseif MC >= 1.21.11
			//$$ pass.bindTexture("Sampler2", Minecraft.getInstance().gameRenderer.lightTexture().getTextureView(), this.sampler);
			//#else
			pass.bindSampler("Sampler2", Minecraft.getInstance().gameRenderer.lightTexture().getTextureView());
			//#endif
			for (ChunkSectionLayer layer : group.layers()) {
				pass.setPipeline(layer.pipeline());
				// 绑定方块纹理图集（Sampler0）
				//#if MC >= 26.1
				//$$ pass.bindTexture("Sampler0", atlas, this.sampler);
				//#elseif MC >= 1.21.11
				//$$ pass.bindTexture("Sampler0", atlas, this.sampler);
				//#else
				pass.bindSampler("Sampler0", atlas);
				//#endif
				List<RenderPass.Draw<GpuBufferSlice[]>> draws = new ArrayList<>();
				for (int i = 0; i < orderedMeshes.size(); i++) {
					SectionBuffers mesh = orderedMeshes.get(i).buffers().get(layer);
					if (mesh == null || mesh.indexCount() <= 0 || mesh.vertexBuffer().isClosed()) continue;
					// 捕获 i 到局部变量，lambda 内不能直接引用循环变量
					int uniformIndex = i;
					//#if MC >= 26.2
					//$$ draws.add(new RenderPass.Draw<>(0, mesh.vertexBuffer(), mesh.indexBuffer(),
					//$$ 		mesh.indexBuffer() == null ? null : mesh.indexType(), 0, mesh.indexCount(), 0,
					//$$ 		(slices, uploader) -> uploader.upload("ChunkSection", slices[uniformIndex])));
					//#elseif MC >= 26.1
					//$$ draws.add(new RenderPass.Draw<>(0, mesh.vertexBuffer(), mesh.indexBuffer(),
					//$$ 		mesh.indexBuffer() == null ? null : mesh.indexType(), 0, mesh.indexCount(), 0,
					//$$ 		(slices, uploader) -> uploader.upload("ChunkSection", slices[uniformIndex])));
					//#elseif MC >= 1.21.11
					//$$ draws.add(new RenderPass.Draw<>(0, mesh.vertexBuffer(), mesh.indexBuffer(),
					//$$ 		mesh.indexBuffer() == null ? null : mesh.indexType(), 0, mesh.indexCount(),
					//$$ 		(slices, uploader) -> uploader.upload("ChunkSection", slices[uniformIndex])));
					//#else
					draws.add(new RenderPass.Draw<>(0, mesh.vertexBuffer(), mesh.indexBuffer(),
							mesh.indexBuffer() == null ? null : mesh.indexType(), 0, mesh.indexCount(),
							(slices, uploader) -> uploader.upload("DynamicTransforms", slices[uniformIndex])));
					//#endif
				}
				if (!draws.isEmpty()) {
					// 半透明层需要反转绘制顺序（远 -> 近），不透明层保持原序（近 -> 远，利用深度测试）
					if (reverse) draws = draws.reversed();
					//#if MC >= 1.21.11
					//$$ pass.drawMultipleIndexed(draws, sharedIndex, sharedIndexType, List.of("ChunkSection"), meshUniforms);
					//#else
					pass.drawMultipleIndexed(draws, sharedIndex, sharedIndexType, List.of("DynamicTransforms"), meshUniforms);
					//#endif
				}
			}
		}
	}

	private void closeMeshes() {
		this.meshes.forEach(ChunkMesh::close);
		this.meshes.clear();
	}

	// 快照变化时重建 GPU 缓冲区
	private record SectionBuffers(GpuBuffer vertexBuffer, GpuBuffer indexBuffer, int indexCount,
			IndexType indexType) implements AutoCloseable {
		@Override
		public void close() {
			this.vertexBuffer.close();
			if (this.indexBuffer != null) {
				this.indexBuffer.close();
			}
		}
	}

	// 每个区块的网格构建器，按 ChunkSectionLayer 分组管理 BufferBuilder
	private static final class ChunkMeshBuilder implements AutoCloseable {
		private final Map<ChunkSectionLayer, ByteBufferBuilder> allocators = new EnumMap<>(ChunkSectionLayer.class);
		private final Map<ChunkSectionLayer, BufferBuilder> builders = new EnumMap<>(ChunkSectionLayer.class);

		private BufferBuilder builder(ChunkSectionLayer layer) {
			return this.builders.computeIfAbsent(layer, ignored -> new BufferBuilder(
					this.allocators.computeIfAbsent(layer, unused -> new ByteBufferBuilder(4096)),
					//#if MC >= 26.2
					//$$ layer.pipeline().getPrimitiveTopology(), layer.vertexFormat()
					//#else
					layer.pipeline().getVertexFormatMode(), layer.pipeline().getVertexFormat()
					//#endif
			));
		}

		// 将 BufferBuilder 中的网格数据上传到 GPU，创建顶点和索引缓冲区
		// 返回包含所有 GPU 缓冲区和原始 MeshData 的 ChunkMesh
		private ChunkMesh build(ChunkPos position) {
			Map<ChunkSectionLayer, SectionBuffers> buffers = new EnumMap<>(ChunkSectionLayer.class);
			List<MeshData> meshData = new ArrayList<>();
			this.builders.forEach((layer, builder) -> {
				// BufferBuilder.build() 将 CPU 端顶点数据打包为 MeshData
				MeshData mesh = builder.build();
				if (mesh == null) {
					return;
				}
				meshData.add(mesh);
				// 将 MeshData 上传到 GPU，创建顶点和索引缓冲区
				GpuBuffer vertex = RenderSystem.getDevice().createBuffer(() -> "ListMore preview vertex buffer",
						GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer());
				// 索引缓冲区可能为空（使用共享顺序索引时）
				GpuBuffer index = mesh.indexBuffer() == null ? null : RenderSystem.getDevice().createBuffer(
						() -> "ListMore preview index buffer", GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST,
						mesh.indexBuffer());
				buffers.put(layer, new SectionBuffers(vertex, index, mesh.drawState().indexCount(), mesh.drawState().indexType()));
			});
			if (buffers.isEmpty()) {
				// 所有层都为空时释放资源并返回 null
				meshData.forEach(MeshData::close);
				this.close();
				return null;
			}
			return new ChunkMesh(position, buffers, meshData, new ArrayList<>(this.allocators.values()));
		}

		@Override
		public void close() {
			this.allocators.values().forEach(ByteBufferBuilder::close);
			this.allocators.clear();
		}
	}

	// 一个区块位置的完整网格数据，包含各渲染层的 GPU 缓冲区和原始构建数据
	private record ChunkMesh(ChunkPos chunk, Map<ChunkSectionLayer, SectionBuffers> buffers,
			List<MeshData> meshData, List<ByteBufferBuilder> allocators) implements AutoCloseable {
		// 计算区块中心到相机的距离平方，用于排序（远到近）
		private double distanceTo(Vector3f camera) {
			float x = this.chunk.getMinBlockX() + 8.0F;
			float z = this.chunk.getMinBlockZ() + 8.0F;
			return Mth.square(x - camera.x) + Mth.square(z - camera.z);
		}

		@Override
		public void close() {
			this.allocators.forEach(ByteBufferBuilder::close);
			this.meshData.forEach(MeshData::close);
			this.buffers.values().forEach(SectionBuffers::close);
		}
	}

	@Override
	public void close() {
		this.closeMeshes();
		this.builtRevision = Long.MIN_VALUE;
		//#if MC >= 26.1
		//$$ this.schematic = null;
		//#endif
		this.world = null;
		if (this.target != null) { this.target.destroyBuffers(); this.target = null; }
		//#if MC >= 1.21.11
		//$$ if (this.sampler != null) { this.sampler.close(); this.sampler = null; }
		//#endif
		if (this.projection != null) { this.projection.close(); this.projection = null; }
		if (this.globalUniform != null) { this.globalUniform.close(); this.globalUniform = null; }
	}
}
