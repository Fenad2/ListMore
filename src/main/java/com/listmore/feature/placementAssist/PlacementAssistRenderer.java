package com.listmore.feature.placementAssist;

//#if MC >= 26.3
//$$ import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
//#elseif MC >= 26.1
//$$ import com.mojang.blaze3d.buffers.GpuBufferSlice;
//#endif

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.listmore.utils.WorldRenderUtils;

import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.util.data.Color4f;

import net.minecraft.client.Camera;
//#if MC >= 26.1
//$$ import net.minecraft.client.DeltaTracker;
//#endif
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
//#if MC >= 26.1
//$$ import net.minecraft.client.renderer.state.level.CameraRenderState;
//#endif
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
//#if MC >= 26.1 && MC < 26.3
//$$ import org.joml.Matrix4fc;
//$$ import org.joml.Vector4f;
//#elseif MC >= 26.3
//$$ import org.joml.Vector4f;
//#else
import org.joml.Matrix4f;
//#endif

import java.util.List;

public final class PlacementAssistRenderer implements IRenderer {
    private static final float BOX_INSET = 0.04F;
    private static final Color4f FILL_COLOR = new Color4f(0.15F, 0.75F, 1.0F, 0.24F);
    private static final Color4f OUTLINE_COLOR = new Color4f(0.25F, 0.9F, 1.0F, 0.95F);
    private static final PlacementAssistRenderer INSTANCE = new PlacementAssistRenderer();

    private PlacementAssistRenderer() {
    }

    public static PlacementAssistRenderer getInstance() {
        return INSTANCE;
    }

    //#if MC >= 26.1
    //$$ @Override
    //$$ public void onExtractWorldLast(DeltaTracker deltaTracker, Camera camera, float partialTicks, ProfilerFiller profiler) {
    //$$     renderPreview();
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
    //$$     renderPreview();
    //$$ }
    //#elseif MC >= 26.1
    //$$ @Override
    //$$ public void onRenderWorldLast(RenderTarget framebuffer, Matrix4fc modelViewMatrix,
    //$$                               CameraRenderState cameraState, Frustum culling, RenderBuffers buffers,
    //$$                               GpuBufferSlice terrainFog, Vector4f fogColor, ProfilerFiller profiler) {
    //$$     renderPreview();
    //$$ }
    //#endif

    private static void renderPreview() {
        Minecraft client = Minecraft.getInstance();
        if (!PlacementAssistHandler.canAssist(client) || client.level == null ||
                !(client.hitResult instanceof BlockHitResult hitResult)) {
            return;
        }

        Direction direction = hitResult.getDirection();
        if (direction != Direction.UP && direction != Direction.DOWN) {
            return;
        }

        BlockPos placementPosition = hitResult.getBlockPos().relative(direction);
        Vec3 cameraPosition = WorldRenderUtils.getCameraPosition();
        WorldRenderUtils.drawFilledOutlinedBlockBoxes(
                "listmore:placement_assist",
                List.of(placementPosition),
                cameraPosition,
                BOX_INSET,
                FILL_COLOR,
                OUTLINE_COLOR
        );
    }
}
