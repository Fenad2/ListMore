package com.listmore.render;

import com.listmore.ListMore;
import com.listmore.config.ListMoreConfigs;

import fi.dy.masa.malilib.interfaces.IRenderer;
//#if MC >= 12111
//$$ import fi.dy.masa.malilib.render.GuiContext;
//#else
import net.minecraft.client.gui.GuiGraphics;
//#endif
import fi.dy.masa.malilib.render.RenderUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class PlayerTracerHudRenderer implements IRenderer {
	private static final PlayerTracerHudRenderer INSTANCE = new PlayerTracerHudRenderer();
	private static final int LINE_THICKNESS = 1;

	private PlayerTracerHudRenderer() {
	}

	public static PlayerTracerHudRenderer getInstance() {
		return INSTANCE;
	}

	//#if MC >= 26.1
	//$$ @Override
	//$$ public void onExtractGuiOverlayPost(GuiContext ctx, float partialTicks, ProfilerFiller profiler) {
	//$$ 	render(ctx, partialTicks);
	//$$ }
	//#elseif MC >= 12111
	//$$ @Override
	//$$ public void onRenderGameOverlayPostAdvanced(GuiContext ctx, float partialTicks, ProfilerFiller profiler) {
	//$$ 	render(ctx, partialTicks);
	//$$ }
	//#else
	@Override
	public void onRenderGameOverlayPostAdvanced(GuiGraphics ctx, float partialTicks, ProfilerFiller profiler, Minecraft client) {
		render(ctx, partialTicks);
	}
	//#endif

	private static void render(
			//#if MC >= 12111
			//$$ GuiContext ctx,
			//#else
			GuiGraphics ctx,
			//#endif
			float partialTicks) {
		Minecraft client = Minecraft.getInstance();

		if (client.player == null || client.level == null ||
				//#if MC >= 26.2
				//$$ client.gui.screen() != null ||
				//#else
				client.screen != null ||
				//#endif
				!ListMoreConfigs.Generic.PLAYER_TRACER.getBooleanValue()) {
			return;
		}

		int guiWidth = client.getWindow().getGuiScaledWidth();
		int guiHeight = client.getWindow().getGuiScaledHeight();
		float physicalPixelScale = guiWidth / (float) client.getWindow().getWidth();
		float startX = guiWidth * 0.5F;
		float startY = 0.0F;
		int color = ListMore.getPlayerTracerColorArgb();

		for (Player player : client.level.players()) {
			if (!ListMore.shouldRenderPlayerTracer(player)) {
				continue;
			}

			// 将玩家碰撞箱顶部中心投影到屏幕坐标
			ProjectedPoint target = projectBoundingBoxTopCenter(player, partialTicks, guiWidth, guiHeight);
			if (target == null) {
				continue;
			}
			if (target.x < 0 || target.x > guiWidth || target.y < 0 || target.y > guiHeight) {
				continue;
			}

			drawLine(ctx, startX, startY, target.x, target.y, color, physicalPixelScale);
		}
	}

	private static ProjectedPoint projectBoundingBoxTopCenter(Player player, float partialTicks,
			int guiWidth, int guiHeight) {
		Vec3 basePos = player.getPosition(partialTicks);
		double halfWidth = player.getBbWidth() * 0.5D + 0.05D;
		double height = player.getBbHeight() + 0.05D;

		ProjectedPoint center = projectPoint(basePos.x, basePos.y + height, basePos.z, guiWidth, guiHeight);
		if (center != null) {
			return center;
		}

		Vec3[] candidates = new Vec3[]{
				new Vec3(basePos.x - halfWidth, basePos.y + height, basePos.z - halfWidth),
				new Vec3(basePos.x - halfWidth, basePos.y + height, basePos.z + halfWidth),
				new Vec3(basePos.x + halfWidth, basePos.y + height, basePos.z - halfWidth),
				new Vec3(basePos.x + halfWidth, basePos.y + height, basePos.z + halfWidth)
		};

		ProjectedPoint best = null;
		for (Vec3 candidate : candidates) {
			ProjectedPoint projected = projectPoint(candidate.x, candidate.y, candidate.z, guiWidth, guiHeight);
			if (projected == null) {
				continue;
			}
			if (best == null || projected.y < best.y) {
				best = projected;
			}
		}

		return best;
	}

	private static ProjectedPoint projectPoint(double x, double y, double z, int guiWidth, int guiHeight) {
		Minecraft client = Minecraft.getInstance();
		//projectPointToScreen返回NDC坐标
		Vec3 screenPos = client.gameRenderer.projectPointToScreen(new Vec3(x, y, z));

		if (!Double.isFinite(screenPos.x) || !Double.isFinite(screenPos.y) || !Double.isFinite(screenPos.z)) {
			return null;
		}
		if (screenPos.z < 0.0D || screenPos.z > 1.0D) {
			return null;
		}

		float scaledX = (float) ((screenPos.x * 0.5D + 0.5D) * guiWidth);
		float scaledY = (float) ((1.0D - (screenPos.y * 0.5D + 0.5D)) * guiHeight);
		return new ProjectedPoint(scaledX, scaledY);
	}

	private static void drawLine(
			//#if MC >= 12111
			//$$ GuiContext ctx,
			//#else
			GuiGraphics ctx,
			//#endif
			float startX, float startY, float endX, float endY, int color, float physicalPixelScale) {
		float pixelStartX = startX / physicalPixelScale;
		float pixelStartY = startY / physicalPixelScale;
		float pixelEndX = endX / physicalPixelScale;
		float pixelEndY = endY / physicalPixelScale;
		float dx = pixelEndX - pixelStartX;
		float dy = pixelEndY - pixelStartY;
		int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));

		for (int i = 0; i <= steps; i++) {
			float progress = (float) i / (float) steps;
			int x = Math.round(pixelStartX + dx * progress);
			int y = Math.round(pixelStartY + dy * progress);
			RenderUtils.drawRect(ctx, x, y, LINE_THICKNESS, LINE_THICKNESS, color, physicalPixelScale);
		}
	}

	private record ProjectedPoint(float x, float y) {
	}
}
