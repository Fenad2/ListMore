package com.ohmylist.render;

import com.ohmylist.OhMyList;
import com.ohmylist.config.OhMyListConfigs;

import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// 玩家追踪HUD渲染器类，实现IRender接口用于在游戏界面中绘制玩家追踪线
public class PlayerTracerHudRenderer implements IRenderer {
	private static final PlayerTracerHudRenderer INSTANCE = new PlayerTracerHudRenderer();
	private static final int LINE_THICKNESS = 1;

	private PlayerTracerHudRenderer() {
	}

	// 获取PlayerTracerHudRenderer的单例实例
	public static PlayerTracerHudRenderer getInstance() {
		return INSTANCE;
	}

	// 在提取GUI覆盖层后调用
	@Override
	public void onExtractGuiOverlayPost(GuiContext ctx, float partialTicks, ProfilerFiller profiler) {
		Minecraft client = Minecraft.getInstance();

		if (client.player == null || client.level == null || client.gui.screen() != null || !OhMyListConfigs.Generic.PLAYER_TRACER.getBooleanValue()) {
			return;
		}

		int guiWidth = client.getWindow().getGuiScaledWidth();
		int guiHeight = client.getWindow().getGuiScaledHeight();
		float physicalPixelScale = guiWidth / (float) client.getWindow().getWidth();
		float startX = guiWidth * 0.5F;
		float startY = 0.0F;
		int color = OhMyList.getPlayerTracerColorArgb();

		for (Player player : client.level.players()) {
			if (!OhMyList.shouldRenderPlayerTracer(player)) {
				continue;
			}

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

	// 投影玩家边界框顶部中心点到屏幕坐标
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

	// 投影三维坐标点到屏幕坐标点
	private static ProjectedPoint projectPoint(double x, double y, double z, int guiWidth, int guiHeight) {
		Minecraft client = Minecraft.getInstance();
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

	// 在GUI上下文中绘制从起点到终点的颜色线
	private static void drawLine(GuiContext ctx, float startX, float startY, float endX, float endY, int color, float physicalPixelScale) {
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

	// 记录屏幕投影点的坐标
	private record ProjectedPoint(float x, float y) {
	}
}
