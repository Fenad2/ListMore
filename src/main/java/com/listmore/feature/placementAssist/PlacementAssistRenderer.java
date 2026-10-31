package com.listmore.feature.placementAssist;

//#if MC >= 26.3
//$$ import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
//#elseif MC >= 26.1
//$$ import com.mojang.blaze3d.buffers.GpuBufferSlice;
//#endif

import com.mojang.blaze3d.pipeline.RenderTarget;
//#if MC >= 26.1
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//$$ import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
//$$ import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
//$$ import net.fabricmc.fabric.api.client.renderer.v1.render.FabricBlockModelRenderState;
//$$ import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
//#endif
//#if MC < 26.1
import com.mojang.blaze3d.vertex.VertexConsumer;
//#endif
import com.listmore.utils.WorldRenderUtils;

import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.util.data.Color4f;
//#if MC >= 26.1
//#if MC >= 26.2
//$$ import fi.dy.masa.malilib.render.RenderUtils;
//#endif
//#endif

import net.minecraft.client.Camera;
//#if MC >= 26.1
//$$ import net.minecraft.client.DeltaTracker;
//#endif
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
//#if MC >= 26.1
//$$ import net.minecraft.client.renderer.SubmitNodeStorage;
//$$ import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
//$$ import net.minecraft.client.renderer.block.BlockModelRenderState;
//$$ import net.minecraft.client.renderer.texture.OverlayTexture;
//#endif
//#if MC < 26.1
//#if MC >= 1.21.11
//$$ import net.minecraft.client.renderer.rendertype.RenderType;
//$$ import net.minecraft.client.renderer.rendertype.RenderTypes;
//#else
import net.minecraft.client.renderer.RenderType;
//#endif
import net.minecraft.client.renderer.texture.TextureAtlas;
//#endif
import net.minecraft.client.renderer.culling.Frustum;
//#if MC < 26.1
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.RandomSource;
//#endif
//#if MC >= 26.1
//$$ import net.minecraft.client.renderer.state.level.CameraRenderState;
//#endif
import net.minecraft.core.BlockPos;
//#if MC >= 26.1
//$$ import net.minecraft.util.LightCoordsUtil;
//#endif
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
//#if MC >= 26.1 && MC < 26.3
//$$ import org.joml.Matrix4fc;
//$$ import org.joml.Matrix4f;
//$$ import org.joml.Vector4f;
//#elseif MC >= 26.3
//$$ import org.joml.Matrix4f;
//$$ import org.joml.Vector4f;
//#else
import org.joml.Matrix4f;
//#endif

import java.util.List;

public final class PlacementAssistRenderer implements IRenderer {
    private static final float BOX_INSET = 0.04F;
    private static final Color4f FILL_COLOR = new Color4f(0.15F, 0.75F, 1.0F, 0.24F);
    private static final Color4f OUTLINE_COLOR = new Color4f(0.25F, 0.9F, 1.0F, 0.95F);
    //#if MC < 26.1
    private static final float GHOST_ALPHA = 0.4F;
    //#endif
    private static final PlacementAssistRenderer INSTANCE = new PlacementAssistRenderer();

    private PlacementAssistRenderer() {
    }

    public static PlacementAssistRenderer getInstance() {
        return INSTANCE;
    }

    //#if MC >= 26.1
    //$$ @Override
    //$$ public void onExtractWorldLast(DeltaTracker deltaTracker, Camera camera, float partialTicks, ProfilerFiller profiler) {
    //$$     renderPreview(camera);
    //$$ }
    //#else
    @Override
    public void onRenderWorldLastAdvanced(RenderTarget framebuffer, Matrix4f posMatrix, Matrix4f projMatrix,
                                          Frustum frustum, Camera camera, RenderBuffers buffers, ProfilerFiller profiler) {
        renderPreview();
    }
    //#endif

    //#if MC >= 26.3
    //$$ @Override
    //$$ public void onRenderWorldLast(RenderTarget framebuffer, CameraRenderState cameraState,
    //$$                               Frustum culling, RenderBuffers buffers, GpuBufferSlice terrainFog,
    //$$                               Vector4f fogColor, ProfilerFiller profiler) {
    //$$ }
    //#elseif MC >= 26.1
    //$$ @Override
    //$$ public void onRenderWorldLast(RenderTarget framebuffer, Matrix4fc modelViewMatrix,
    //$$                               CameraRenderState cameraState, Frustum culling, RenderBuffers buffers,
    //$$                               GpuBufferSlice terrainFog, Vector4f fogColor, ProfilerFiller profiler) {
    //$$ }
    //#endif

    private static void renderPreview() {
        Minecraft client = Minecraft.getInstance();
        PlacementAssistHandler.PlacementOffset offset = PlacementAssistHandler.getOffset(client);
        if (offset == null) {
            return;
        }

        //#if MC < 26.1
        renderBlockGhost(client, offset);
        //#endif
    }

    //#if MC >= 26.1
    //$$ private static void renderPreview(Camera camera) {
    //$$     Minecraft client = Minecraft.getInstance();
    //$$     PlacementAssistHandler.PlacementOffset offset = PlacementAssistHandler.getOffset(client);
    //$$     if (offset == null) {
    //$$         return;
    //$$     }
    //$$
    //$$     renderBlockGhost(client, offset, camera);
    //$$ }
    //#endif

    //#if MC >= 26.1
    //$$ private static void renderBlockGhost(Minecraft client, PlacementAssistHandler.PlacementOffset offset, Camera camera) {
    //$$     if (client.level == null || offset.state().getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL) {
    //$$         return;
    //$$     }
    //$$
    //$$     SubmitNodeStorage nodeStorage = getSubmitNodeStorage(client);
    //$$     BlockModelRenderState renderState = new BlockModelRenderState();
    //$$     BlockStateModel model = client.getModelManager().getBlockStateModelSet().get(offset.state());
    //$$     QuadEmitter emitter = ((FabricBlockModelRenderState) renderState).setupMesh(new Matrix4f(), true);
    //$$     emitter.pushTransform(quad -> {
    //$$         quad.chunkLayer(ChunkSectionLayer.TRANSLUCENT);
    //$$         quad.multiplyColor(0x66FFFFFF);
    //$$         return true;
    //$$     });
    //$$     ((FabricBlockStateModel) model).emitQuads(
    //$$             emitter,
    //$$             client.level,
    //$$             offset.position(),
    //$$             offset.state(),
    //$$             renderState.scratchRandomSource(offset.state().getSeed(offset.position())),
    //$$             direction -> false
    //$$     );
    //$$     emitter.popTransform();
    //$$     Vec3 cameraPosition = camera.position();
    //$$     PoseStack poseStack = new PoseStack();
    //$$     poseStack.translate(
    //$$             offset.position().getX() - cameraPosition.x,
    //$$             offset.position().getY() - cameraPosition.y,
    //$$             offset.position().getZ() - cameraPosition.z
    //$$     );
    //$$     renderState.submit(poseStack, nodeStorage, LightCoordsUtil.pack(15, 15),
    //$$             OverlayTexture.NO_OVERLAY, 0);
    //$$ }
    //#endif

    //#if MC >= 26.2
    //$$ private static SubmitNodeStorage getSubmitNodeStorage(Minecraft client) {
    //$$     return RenderUtils.nodes();
    //$$ }
    //#elseif MC >= 26.1
    //$$ private static SubmitNodeStorage getSubmitNodeStorage(Minecraft client) {
    //$$     return client.gameRenderer.getFeatureRenderDispatcher().getSubmitNodeStorage();
    //$$ }
    //#endif

    //#if MC < 26.1
    private static void renderBlockGhost(Minecraft client, PlacementAssistHandler.PlacementOffset offset) {
        if (offset.state().getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL) {
            return;
        }

        Vec3 cameraPosition = WorldRenderUtils.getCameraPosition();
        PoseStack poseStack = new PoseStack();
        poseStack.translate(
                offset.position().getX() - cameraPosition.x,
                offset.position().getY() - cameraPosition.y,
                offset.position().getZ() - cameraPosition.z
        );

        MultiBufferSource.BufferSource bufferSource = client.renderBuffers().bufferSource();
        RandomSource random = RandomSource.create(offset.state().getSeed(offset.position()));
        RenderType renderType = getGhostRenderType();
        VertexConsumer ghostConsumer = new GhostVertexConsumer(bufferSource.getBuffer(renderType));
        client.getBlockRenderer().renderBatched(
                offset.state(),
                offset.position(),
                client.level,
                poseStack,
                ghostConsumer,
                true,
                client.getBlockRenderer().getBlockModel(offset.state()).collectParts(random)
        );
        bufferSource.endBatch(renderType);
    }

    private static RenderType getGhostRenderType() {
        //#if MC >= 1.21.11
        //$$ return RenderTypes.translucentMovingBlock();
        //#else
        return RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
        //#endif
    }

    private record GhostVertexConsumer(VertexConsumer delegate) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, Math.round(alpha * GHOST_ALPHA));
            return this;
        }

        //#if MC >= 1.21.11
        //$$ @Override
        //$$ public VertexConsumer setColor(int color) {
        //$$     int alpha = Math.round(((color >>> 24) & 255) * GHOST_ALPHA);
        //$$     delegate.setColor((color & 0x00FFFFFF) | (alpha << 24));
        //$$     return this;
        //$$ }
        //#endif

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }

        //#if MC >= 1.21.11
        //$$ @Override
        //$$ public VertexConsumer setLineWidth(float width) {
        //$$     delegate.setLineWidth(width);
        //$$     return this;
        //$$ }
        //#endif
    }
    //#endif
}
