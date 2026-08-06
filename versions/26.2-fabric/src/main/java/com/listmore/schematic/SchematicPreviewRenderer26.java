package com.listmore.schematic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import com.listmore.ListMore;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.GpuFormat;
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
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import fi.dy.masa.litematica.render.schematic.BlockModelRendererSchematic;
import fi.dy.masa.litematica.render.schematic.IBlockOutputSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Matrix4fStack;
import org.joml.Matrix3x2f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

/** 26.x 鐨勭鏈?framebuffer 鏂瑰潡棰勮鍚庣銆?*/
public final class SchematicPreviewRenderer26 implements SchematicPreviewRenderBackend {
	private final List<ChunkMesh> meshes = new ArrayList<>();
	private long builtRevision = Long.MIN_VALUE;
	private LitematicaSchematic schematic;
	private SchematicPreviewWorld26 world;
	private TextureTarget target;
	private GpuSampler sampler;
	private ProjectionMatrixBuffer projection;
	private final Projection previewProjection = new Projection();
	private GpuBuffer globalUniform;
	private long loggedRenderRevision = Long.MIN_VALUE;
	private long loggedDrawRevision = Long.MIN_VALUE;
	private long loggedFrustumRevision = Long.MIN_VALUE;
	private final FluidRenderer fluidRenderer = new FluidRenderer(Minecraft.getInstance().getModelManager().getFluidStateModelSet());
	private final CameraRenderState cameraRenderState = new CameraRenderState();

	@Override
	public void setSchematic(Object schematic) {
		this.schematic = schematic instanceof LitematicaSchematic loaded ? loaded : null;
	}

	public boolean render(Object rawContext, SchematicPreviewLayout layout, SchematicPreviewTransform transform,
			SchematicPreviewModel model, long revision) {
		GuiContext context = (GuiContext) rawContext;
		if (model == null || model.blocks().isEmpty() || layout.contentWidth() <= 0 || layout.contentHeight() <= 0) {
			return false;
		}
		this.ensureTarget(layout.contentWidth(), layout.contentHeight());
		if (this.builtRevision != revision) {
			try {
				this.rebuild(model, revision);
			} catch (Throwable throwable) {
				ListMore.LOGGER.error("Schematic preview: mesh rebuild failed (revision={})", revision, throwable);
				return false;
			}
		}
		if (this.meshes.isEmpty()) {
			return false;
		}

		RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
				this.target.getColorTexture(), new org.joml.Vector4f(0.105F, 0.125F, 0.157F, 1.0F),
				this.target.getDepthTexture(), 0.0D);
		try {
			this.renderTarget(model, transform);
		} catch (Throwable throwable) {
			ListMore.LOGGER.error("Schematic preview: framebuffer render failed (revision={})", revision, throwable);
			return false;
		}
		context.addSimpleElement(new BlitRenderState(
				RenderPipelines.GUI_TEXTURED,
				TextureSetup.singleTexture(this.target.getColorTextureView(), this.sampler),
				new Matrix3x2f(context.pose()),
				layout.contentX(), layout.contentY(),
				layout.contentX() + layout.contentWidth(), layout.contentY() + layout.contentHeight(),
				0.0F, 1.0F, 1.0F, 0.0F, 0xFFFFFFFF, context.scissorStack.peek()));
		return true;
	}

	private void ensureTarget(int width, int height) {
		int scaledWidth = Math.max(1, Math.round(width * Minecraft.getInstance().getWindow().getGuiScale()));
		int scaledHeight = Math.max(1, Math.round(height * Minecraft.getInstance().getWindow().getGuiScale()));
		if (this.target == null) {
			this.target = new TextureTarget("ListMore schematic preview", scaledWidth, scaledHeight, true, GpuFormat.RGBA8_UNORM);
		} else if (this.target.width != scaledWidth || this.target.height != scaledHeight) {
			this.target.resize(scaledWidth, scaledHeight);
		}
		if (this.sampler == null) {
			this.sampler = RenderSystem.getDevice().createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
					FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
		}
	}

	private void rebuild(SchematicPreviewModel model, long revision) {
		this.closeMeshes();
		this.builtRevision = revision;
		this.loggedRenderRevision = Long.MIN_VALUE;
		this.loggedDrawRevision = Long.MIN_VALUE;
		this.loggedFrustumRevision = Long.MIN_VALUE;
		if (this.schematic == null) {
			return;
		}
		if (this.world == null) {
			this.world = new SchematicPreviewWorld26(Minecraft.getInstance());
		}
		this.world.setSchematic(this.schematic);
		SchematicPreviewWorld26 view = this.world;
		ModelManager modelManager = Minecraft.getInstance().getModelManager();
		Map<ChunkPos, ChunkMeshBuilder> chunks = new HashMap<>();
		int modelBlocks = 0;
		int fluidBlocks = 0;
		BlockModelRendererSchematic renderer = new BlockModelRendererSchematic();
		renderer.enableCache();
		try {
			for (SchematicPreviewModel.Block block : model.blocks()) {
				BlockState state = block.state();
				BlockPos position = new BlockPos(block.x(), block.y(), block.z());
				ChunkMeshBuilder chunk = chunks.computeIfAbsent(new ChunkPos(block.x() >> 4, block.z() >> 4), ignored -> new ChunkMeshBuilder());
				if (!state.getFluidState().isEmpty()) {
					fluidBlocks++;
					this.fluidRenderer.tesselate(view, position, chunk::builder, state, state.getFluidState());
				}
				if (state.getRenderShape() != RenderShape.MODEL) {
					continue;
				}
				modelBlocks++;
				IBlockOutputSchematic output = (x, y, z, quad, instance) -> chunk.builder(quad.materialInfo().layer())
						.putBlockBakedQuad(x, y, z, quad, instance);
				renderer.tessellateBlock(view, state, position, new Vec3(block.x() & 15, block.y(), block.z() & 15),
						modelManager.getBlockStateModelSet().get(state), state.getSeed(position), output);
			}
		} finally {
			renderer.disableCache();
		}
		chunks.forEach((position, chunk) -> {
			ChunkMesh built = chunk.build(position);
			if (built != null) {
				this.meshes.add(built);
			}
		});
		int layers = this.meshes.stream().mapToInt(mesh -> mesh.buffers().size()).sum();
		int indices = this.meshes.stream().flatMap(mesh -> mesh.buffers().values().stream()).mapToInt(SectionBuffers::indexCount).sum();
		ListMore.LOGGER.info("Schematic preview: mesh rebuild complete (revision={}, sourceBlocks={}, modelBlocks={}, fluidBlocks={}, chunks={}, layers={}, indices={})",
				revision, model.blocks().size(), modelBlocks, fluidBlocks, this.meshes.size(), layers, indices);
	}

	private void renderTarget(SchematicPreviewModel model, SchematicPreviewTransform transform) {
		float distance = transform.distance(model.size().getX(), model.size().getY(), model.size().getZ());
		float yaw = transform.yaw() * Mth.DEG_TO_RAD;
		float pitch = transform.pitch() * Mth.DEG_TO_RAD;
		float horizontal = Mth.cos(pitch);
		Vector3f camera = new Vector3f(
				model.centerX() - Mth.sin(yaw) * horizontal * distance,
				model.centerY() - Mth.sin(pitch) * distance,
				model.centerZ() + Mth.cos(yaw) * horizontal * distance);
		if (this.loggedRenderRevision != this.builtRevision) {
			this.loggedRenderRevision = this.builtRevision;
			ListMore.LOGGER.info("Schematic preview: rendering revision={} (framebuffer={}x{}, camera={}, yaw={}, pitch={}, distance={})",
					this.builtRevision, this.target.width, this.target.height, camera, transform.yaw(), transform.pitch(), distance);
		}
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		// Match SchematicPreview's orbit convention: UI yaw describes the viewing
		// direction, while the terrain view matrix uses the inverse camera yaw.
		Quaternionf rotation = new Quaternionf().rotationYXZ(-yaw, pitch, 0.0F).conjugate();
		modelView.set(new Matrix4f().rotation(rotation));
		this.previewProjection.setupPerspective(0.05F, 4096.0F, 70.0F,
				this.target.width, this.target.height);
		Matrix4f projectionMatrix = this.previewProjection.getMatrix(new Matrix4f());
		this.logFrustum(model, camera, modelView, projectionMatrix);
		this.cameraRenderState.initialized = true;
		this.cameraRenderState.orientation = rotation;
		this.cameraRenderState.pos = new Vec3(camera.x, camera.y, camera.z);
		this.cameraRenderState.blockPos = BlockPos.containing(this.cameraRenderState.pos);
		this.cameraRenderState.xRot = transform.pitch();
		this.cameraRenderState.yRot = -transform.yaw();
		RenderSystem.backupProjectionMatrix();
		if (this.projection == null) {
			this.projection = new ProjectionMatrixBuffer("ListMore schematic preview");
		}
		RenderSystem.setProjectionMatrix(this.projection.getBuffer(projectionMatrix), ProjectionType.PERSPECTIVE);
		GpuBuffer previousGlobalUniform = RenderSystem.getGlobalSettingsUniform();
		this.writeGlobalUniform(camera);
		try {
			Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.LEVEL);
			this.renderMeshes(camera);
		} finally {
			RenderSystem.setGlobalSettingsUniform(previousGlobalUniform);
			RenderSystem.restoreProjectionMatrix();
			modelView.popMatrix();
		}
	}

	private void writeGlobalUniform(Vector3f camera) {
		if (this.globalUniform == null) {
			this.globalUniform = RenderSystem.getDevice().createBuffer(() -> "ListMore preview global settings",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, GlobalSettingsUniform.UBO_SIZE);
		}
		int floorX = Mth.floor(camera.x);
		int floorY = Mth.floor(camera.y);
		int floorZ = Mth.floor(camera.z);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.globalUniform.slice(),
					Std140Builder.onStack(stack, GlobalSettingsUniform.UBO_SIZE)
							.putIVec3(floorX, floorY, floorZ)
							.putVec3(floorX - camera.x, floorY - camera.y, floorZ - camera.z)
					.putVec2(Minecraft.getInstance().getWindow().getWidth(), Minecraft.getInstance().getWindow().getHeight())
					.putFloat(1.0F).putFloat(0.0F).putInt(0).putInt(0).get());
		}
		RenderSystem.setGlobalSettingsUniform(this.globalUniform);
	}

	/** Logs the terrain shader's world-to-clip transform once for each preview. */
	private void logFrustum(SchematicPreviewModel model, Vector3f camera, Matrix4fc modelView,
			Matrix4fc projectionMatrix) {
		if (this.loggedFrustumRevision == this.builtRevision) {
			return;
		}
		this.loggedFrustumRevision = this.builtRevision;
		Matrix4f transform = new Matrix4f(projectionMatrix).mul(modelView);
		int visibleCorners = 0;
		float minX = Float.POSITIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		for (int x : new int[] { 0, model.size().getX() }) {
			for (int y : new int[] { 0, model.size().getY() }) {
				for (int z : new int[] { 0, model.size().getZ() }) {
					org.joml.Vector4f clip = transform.transform(new org.joml.Vector4f(
							x - camera.x, y - camera.y, z - camera.z, 1.0F));
					if (clip.w > 0.0F) {
						float ndcX = clip.x / clip.w;
						float ndcY = clip.y / clip.w;
						minX = Math.min(minX, ndcX);
						minY = Math.min(minY, ndcY);
						maxX = Math.max(maxX, ndcX);
						maxY = Math.max(maxY, ndcY);
						if (Math.abs(ndcX) <= 1.0F && Math.abs(ndcY) <= 1.0F) {
							visibleCorners++;
						}
					}
				}
			}
		}
		ListMore.LOGGER.info("Schematic preview: CPU frustum revision={} visibleCorners={}/8 ndcX=[{}, {}] ndcY=[{}, {}]",
				this.builtRevision, visibleCorners, minX, maxX, minY, maxY);
	}

	private void renderMeshes(Vector3f camera) {
		var atlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
		List<ChunkMesh> orderedMeshes = new ArrayList<>(this.meshes);
		orderedMeshes.sort(Comparator.comparingDouble(mesh -> -mesh.distanceTo(camera)));
		DynamicUniforms.ChunkSectionInfo[] infos = new DynamicUniforms.ChunkSectionInfo[orderedMeshes.size()];
		for (int i = 0; i < orderedMeshes.size(); i++) {
			ChunkMesh mesh = orderedMeshes.get(i);
			infos[i] = new DynamicUniforms.ChunkSectionInfo(RenderSystem.getModelViewMatrixCopy(), mesh.chunk().getMinBlockX(), 0,
					mesh.chunk().getMinBlockZ(), 1.0F, atlas.getWidth(0), atlas.getHeight(0));
		}
		GpuBufferSlice[] sectionUniforms = RenderSystem.getDynamicUniforms().writeChunkSections(infos);
		RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
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
		this.renderLayerGroup(orderedMeshes, sectionUniforms, atlas, sharedIndex, sharedIndexType,
				ChunkSectionLayerGroup.OPAQUE, false);
		this.renderLayerGroup(orderedMeshes, sectionUniforms, atlas, sharedIndex, sharedIndexType,
				ChunkSectionLayerGroup.TRANSLUCENT, true);
	}

	private void renderLayerGroup(List<ChunkMesh> orderedMeshes, GpuBufferSlice[] sectionUniforms,
			GpuTextureView atlas, GpuBuffer sharedIndex, IndexType sharedIndexType,
			ChunkSectionLayerGroup group, boolean reverse) {
		int drawCount = 0;
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> "ListMore schematic preview " + group.label(), this.target.getColorTextureView(), Optional.empty(),
				this.target.getDepthTextureView(), OptionalDouble.empty())) {
			RenderSystem.bindDefaultUniforms(pass);
			pass.bindTexture("Sampler2", Minecraft.getInstance().gameRenderer.lightmap(),
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			for (ChunkSectionLayer layer : group.layers()) {
				pass.setPipeline(layer.pipeline());
				pass.bindTexture("Sampler0", atlas, this.sampler);
				List<RenderPass.Draw<GpuBufferSlice[]>> draws = new ArrayList<>();
				for (int i = 0; i < orderedMeshes.size(); i++) {
					SectionBuffers mesh = orderedMeshes.get(i).buffers().get(layer);
					if (mesh == null || mesh.indexCount() <= 0 || mesh.vertexBuffer().isClosed()) continue;
					int uniformIndex = i;
					draws.add(new RenderPass.Draw<>(0, mesh.vertexBuffer(), mesh.indexBuffer(),
							mesh.indexBuffer() == null ? null : mesh.indexType(), 0, mesh.indexCount(), 0,
							(slices, uploader) -> uploader.upload("ChunkSection", slices[uniformIndex])));
				}
				if (!draws.isEmpty()) {
					if (reverse) draws = draws.reversed();
					drawCount += draws.size();
					if (this.loggedDrawRevision != this.builtRevision) {
						int layerIndices = draws.stream().mapToInt(RenderPass.Draw::indexCount).sum();
						ListMore.LOGGER.info("Schematic preview: layer {} has {} draws and {} indices (group={})",
								layer, draws.size(), layerIndices, group.label());
					}
					pass.drawMultipleIndexed(draws, sharedIndex, sharedIndexType, List.of("ChunkSection"), sectionUniforms);
				}
			}
		}
		if (this.loggedDrawRevision != this.builtRevision) {
			this.loggedDrawRevision = this.builtRevision;
			ListMore.LOGGER.info("Schematic preview: submitted {} draws for {} (sections={}, sharedIndex={})",
					drawCount, group.label(), sectionUniforms.length, sharedIndex != null);
		}
	}

	private void closeMeshes() {
		this.meshes.forEach(ChunkMesh::close);
		this.meshes.clear();
	}

	/** 姣忔蹇収鍙樺寲鏃跺缓绔嬩竴娆?GPU 缂撳啿锛屽抚闂寸洿鎺ュ鐢ㄣ€?*/
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

	private static final class ChunkMeshBuilder implements AutoCloseable {
		private final Map<ChunkSectionLayer, ByteBufferBuilder> allocators = new EnumMap<>(ChunkSectionLayer.class);
		private final Map<ChunkSectionLayer, BufferBuilder> builders = new EnumMap<>(ChunkSectionLayer.class);

		private BufferBuilder builder(ChunkSectionLayer layer) {
			return this.builders.computeIfAbsent(layer, ignored -> new BufferBuilder(
					this.allocators.computeIfAbsent(layer, unused -> new ByteBufferBuilder(4096)),
					layer.pipeline().getPrimitiveTopology(), layer.vertexFormat()));
		}

		private ChunkMesh build(ChunkPos position) {
			Map<ChunkSectionLayer, SectionBuffers> buffers = new EnumMap<>(ChunkSectionLayer.class);
			List<MeshData> meshData = new ArrayList<>();
			this.builders.forEach((layer, builder) -> {
				MeshData mesh = builder.build();
				if (mesh == null) {
					return;
				}
				meshData.add(mesh);
				GpuBuffer vertex = RenderSystem.getDevice().createBuffer(() -> "ListMore preview vertex buffer",
						GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer());
				GpuBuffer index = mesh.indexBuffer() == null ? null : RenderSystem.getDevice().createBuffer(
						() -> "ListMore preview index buffer", GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST,
						mesh.indexBuffer());
				buffers.put(layer, new SectionBuffers(vertex, index, mesh.drawState().indexCount(), mesh.drawState().indexType()));
			});
			if (buffers.isEmpty()) {
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

	private record ChunkMesh(ChunkPos chunk, Map<ChunkSectionLayer, SectionBuffers> buffers,
			List<MeshData> meshData, List<ByteBufferBuilder> allocators) implements AutoCloseable {
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
		this.schematic = null;
		this.world = null;
		if (this.target != null) { this.target.destroyBuffers(); this.target = null; }
		if (this.sampler != null) { this.sampler.close(); this.sampler = null; }
		if (this.projection != null) { this.projection.close(); this.projection = null; }
		if (this.globalUniform != null) { this.globalUniform.close(); this.globalUniform = null; }
	}
}
