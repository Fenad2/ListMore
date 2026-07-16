package com.ohmylist.render;

import com.ohmylist.config.OhMyListConfigs;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.MaLiLibPipelines;
import fi.dy.masa.malilib.render.RenderContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public final class ProjectileLandingRenderer implements IRenderer {
    private static final int MAX_TICKS = 200;
    private static final double HIT_RADIUS = 0.1D;
    private static final float HALF_SIZE = 0.125F;
    private static final ProjectileLandingRenderer INSTANCE = new ProjectileLandingRenderer();
    // MaLiLib把计算和实际绘制拆成了两个阶段
    private LandingPoint pendingLanding;

    private ProjectileLandingRenderer() {
    }

    public static ProjectileLandingRenderer getInstance() {
        return INSTANCE;
    }

    @Override
    public void onExtractWorldLast(DeltaTracker deltaTracker, Camera camera, float partialTicks, ProfilerFiller profiler) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        Level level = client.level;
        if (player == null || level == null || !OhMyListConfigs.Generic.PROJECTILE_LANDING_PREDICTION.getBooleanValue()) {
            this.pendingLanding = null;
            return;
        }

        ProjectileSpec spec = ProjectileSpec.from(player.getMainHandItem().getItem());
        if (spec == null || (spec.requiresUse && !player.isUsingItem())) {
            this.pendingLanding = null;
            return;
        }
        if (spec.requiresUse && ProjectileSpec.from(player.getUseItem().getItem()) != spec) {
            this.pendingLanding = null;
            return;
        }

        double speed = spec.speed;
        if (spec == ProjectileSpec.BOW) {
            float pull = BowItem.getPowerForTime(player.getTicksUsingItem());
            if (pull < 0.1F) {
                this.pendingLanding = null;
                return;
            }
            // 弓没拉满就不能按满蓄力速度来算
            speed *= pull;
        }

        this.pendingLanding = predict(player, level, spec, speed);
    }

    @Override
    public void onRenderWorldLast(RenderTarget framebuffer, Matrix4fc modelViewMatrix, CameraRenderState cameraState,
                                  Frustum culling, RenderBuffers buffers, GpuBufferSlice terrainFog,
                                  Vector4f fogColor, ProfilerFiller profiler) {
        if (this.pendingLanding != null) {
            drawMarker(this.pendingLanding);
        }
    }

    private static LandingPoint predict(LocalPlayer player, Level level, ProjectileSpec spec, double speed) {
        Vec3 current = player.getEyePosition().add(0.0D, -0.1D, 0.0D);
        Vec3 velocity = player.getLookAngle().scale(speed);

        for (int tick = 1; tick <= MAX_TICKS; tick++) {
            Vec3 next = current.add(velocity);
            HitResult blockHit = level.clip(new ClipContext(current, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            EntityHit entityHit = findEntityHit(level, player, current, next);

            double blockDistance = blockHit.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : current.distanceToSqr(blockHit.getLocation());
            // 这一 tick 谁更近，投射物先撞到谁。
            if (entityHit != null && entityHit.distanceSquared < blockDistance) {
                return new LandingPoint(entityHit.position, true);
            }
            if (blockDistance != Double.MAX_VALUE) {
                return new LandingPoint(blockHit.getLocation(), false);
            }

            current = next;
            velocity = new Vec3(velocity.x * spec.drag, velocity.y * spec.drag - spec.gravity, velocity.z * spec.drag);
            if (current.y < -64.0D || velocity.lengthSqr() < 0.0001D) {
                return new LandingPoint(current, false);
            }
        }

        return new LandingPoint(current, false);
    }

    private static EntityHit findEntityHit(Level level, LocalPlayer player, Vec3 start, Vec3 end) {
        AABB searchBox = new AABB(start, end).inflate(HIT_RADIUS);
        EntityHit closest = null;
        for (Entity entity : level.getEntities(player, searchBox, entity -> entity.isAlive() && entity.isPickable())) {
            var hit = entity.getBoundingBox().inflate(HIT_RADIUS).clip(start, end);
            if (hit.isEmpty()) {
                continue;
            }
            double distance = start.distanceToSqr(hit.get());
            if (closest == null || distance < closest.distanceSquared) {
                closest = new EntityHit(hit.get(), distance);
            }
        }
        return closest;
    }

    private static void drawMarker(LandingPoint landing) {
        // 这条渲染管线要的是相对相机的坐标
        Vec3 position = landing.position.subtract(Minecraft.getInstance().gameRenderer.mainCamera().position());
        Color4f color = landing.hitEntity ? new Color4f(1.0F, 0.0F, 0.0F, 0.6F) : new Color4f(0.0F, 1.0F, 0.2F, 0.6F);
        RenderContext context = new RenderContext(
                () -> "ohmylist:projectile_landing",
                MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL,
                0
        );

        try {
            BufferBuilder builder = context.getBuilder();
            RenderUtils.drawBoxAllEdgesBatchedLines(
                    (float) (position.x - HALF_SIZE), (float) (position.y - HALF_SIZE), (float) (position.z - HALF_SIZE),
                    (float) (position.x + HALF_SIZE), (float) (position.y + HALF_SIZE), (float) (position.z + HALF_SIZE),
                    color, 2.0F, builder
            );
            MeshData mesh = builder.build();
            if (mesh != null) {
                context.draw(mesh, false, true);
                mesh.close();
            }
        } catch (Exception ignored) {
        } finally {
            try {
                context.close();
            } catch (Exception ignored) {
            }
        }
    }

    private enum ProjectileSpec {
        BOW(3.0D, 0.05D, 0.99D, true),
        TRIDENT(2.5D, 0.05D, 0.99D, true),
        SNOWBALL(1.5D, 0.03D, 0.99D, false),
        ENDER_PEARL(1.5D, 0.03D, 0.99D, false),
        EGG(1.5D, 0.03D, 0.99D, false);

        private final double speed;
        private final double gravity;
        private final double drag;
        private final boolean requiresUse;

        ProjectileSpec(double speed, double gravity, double drag, boolean requiresUse) {
            this.speed = speed;
            this.gravity = gravity;
            this.drag = drag;
            this.requiresUse = requiresUse;
        }

        private static ProjectileSpec from(Item item) {
            if (item instanceof BowItem) return BOW;
            if (item instanceof TridentItem) return TRIDENT;
            if (item instanceof SnowballItem) return SNOWBALL;
            if (item instanceof EnderpearlItem) return ENDER_PEARL;
            if (item instanceof EggItem) return EGG;
            return null;
        }
    }

    private record LandingPoint(Vec3 position, boolean hitEntity) {
    }

    private record EntityHit(Vec3 position, double distanceSquared) {
    }
}
