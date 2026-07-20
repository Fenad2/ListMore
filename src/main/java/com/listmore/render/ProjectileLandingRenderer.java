package com.listmore.render;

import com.listmore.config.ListMoreConfigs;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
//#if MC >= 26.1
//$$ import com.mojang.blaze3d.buffers.GpuBufferSlice;
//#endif
import com.mojang.blaze3d.pipeline.RenderTarget;
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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
//#if MC >= 26.1
//$$ import net.minecraft.client.renderer.state.level.CameraRenderState;
//#endif
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
//#if MC >= 26.1
//$$ import org.joml.Matrix4fc;
//$$ import org.joml.Vector4f;
//#else
import org.joml.Matrix4f;
//#endif
//弓、三叉戟、雪球、末影珍珠、鸡蛋。"弩"？
public final class ProjectileLandingRenderer implements IRenderer {
	private static final int MAX_TICKS = 200;
	private static final double HIT_RADIUS = 0.1D;
	private static final float HALF_SIZE = 0.125F;
	private static final ProjectileLandingRenderer INSTANCE = new ProjectileLandingRenderer();
	private LandingPoint pendingLanding;

	private ProjectileLandingRenderer() {
	}

	public static ProjectileLandingRenderer getInstance() {
		return INSTANCE;
	}

	//#if MC >= 26.1
	//$$ @Override
	//$$ public void onExtractWorldLast(DeltaTracker deltaTracker, Camera camera, float partialTicks, ProfilerFiller profiler) {
	//$$ 	updateLanding();
	//$$ }
	//#else
	@Override
	public void onRenderWorldLastAdvanced(RenderTarget framebuffer, Matrix4f posMatrix, Matrix4f projMatrix,
									Frustum frustum, Camera camera, RenderBuffers buffers, ProfilerFiller profiler) {
		updateLanding();
		if (this.pendingLanding != null) {
			drawMarker(this.pendingLanding);
		}
	}
	//#endif

	//#if MC >= 26.1
	//$$ @Override
	//$$ public void onRenderWorldLast(RenderTarget framebuffer, Matrix4fc modelViewMatrix, CameraRenderState cameraState,
	//$$ 								  Frustum culling, RenderBuffers buffers, GpuBufferSlice terrainFog,
	//$$ 								  Vector4f fogColor, ProfilerFiller profiler) {
	//$$ 	if (this.pendingLanding != null) {
	//$$ 		drawMarker(this.pendingLanding);
	//$$ 	}
	//$$ }
	//#endif

	private void updateLanding() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		Level level = client.level;
		if (player == null || level == null || !ListMoreConfigs.Generic.PROJECTILE_LANDING_PREDICTION.getBooleanValue()) {
			this.pendingLanding = null;
			return;
		}

		//获取主手物品弹射物信息
		ProjectileSpec spec = ProjectileSpec.from(player.getMainHandItem().getItem());
		//如果为空或未蓄力则null
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
			if (pull < 0.1F) {	// 力度太小，忽略
				this.pendingLanding = null;
				return;
			}
			speed *= pull;
		}

		this.pendingLanding = predict(player, level, spec, speed);
	}

	private static LandingPoint predict(LocalPlayer player, Level level, ProjectileSpec spec, double speed) {
		Vec3 current = player.getEyePosition().add(0.0D, -0.1D, 0.0D);
		Vec3 velocity = player.getLookAngle().scale(speed);

		for (int tick = 1; tick <= MAX_TICKS; tick++) {
			Vec3 next = current.add(velocity);
			HitResult blockHit = level.clip(new ClipContext(current, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			EntityHit entityHit = findEntityHit(level, player, current, next);

			// 取方块和实体中更近的碰撞点
			double blockDistance = blockHit.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : current.distanceToSqr(blockHit.getLocation());
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
			// 检测射线是否穿过实体的碰撞箱
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
		//#if MC >= 26.2
		//$$ Vec3 position = landing.position.subtract(Minecraft.getInstance().gameRenderer.mainCamera().position());
		//#elseif MC >= 12111
		//$$ Vec3 position = landing.position.subtract(Minecraft.getInstance().gameRenderer.getMainCamera().position());
		//#else
		Vec3 position = landing.position.subtract(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
		//#endif
		Color4f color = landing.hitEntity ? new Color4f(1.0F, 0.0F, 0.0F, 0.6F) : new Color4f(0.0F, 1.0F, 0.2F, 0.6F);
		RenderContext context = new RenderContext(
				() -> "listmore:projectile_landing",
				MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL
				//#if MC >= 26.2
				//$$ , 0
				//#endif
		);
		//#if MC < 12111
		context.lineWidth(2.0F);
		//#endif

		try {
			BufferBuilder builder = context.getBuilder();
			RenderUtils.drawBoxAllEdgesBatchedLines(
					(float) (position.x - HALF_SIZE), (float) (position.y - HALF_SIZE), (float) (position.z - HALF_SIZE),
					(float) (position.x + HALF_SIZE), (float) (position.y + HALF_SIZE), (float) (position.z + HALF_SIZE),
					color
					//#if MC >= 12111
					//$$ , 2.0F
					//#endif
					, builder
			);
			draw(context, builder.build());
		} finally {
			closeQuietly(context);
		}
	}

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
