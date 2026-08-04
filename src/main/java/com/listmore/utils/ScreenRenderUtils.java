package com.listmore.utils;

import fi.dy.masa.malilib.render.RenderUtils;

//#if MC >= 1.21.11
//$$ import fi.dy.masa.malilib.render.GuiContext;
//#else
import net.minecraft.client.gui.GuiGraphics;
//#endif

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** TODO:该类暂时没有具体用处，只是抽离出来的一个工具类。 (-玩家追踪线)
 * 屏幕渲染相关的通用工具。
 * projectWorldPoint() 用于将任意世界坐标投影到 GUI 坐标。
 * projectBoundingBoxTopCenter() 用于将实体碰撞箱顶部中心投影到 GUI 坐标。
 * drawLine() 用于在 GUI 层绘制两个屏幕坐标之间的线段。
 */
public final class ScreenRenderUtils {
	private static final int LINE_THICKNESS = 1;

	private ScreenRenderUtils() {
	}

	/**
	 * @param worldPosition 世界空间坐标
	 * @return 投影后的 GUI 坐标；不可见或投影无效时返回 null
	 */
	public static ProjectedPoint projectWorldPoint(Vec3 worldPosition) {
		Minecraft client = Minecraft.getInstance();
		Vec3 screenPos = client.gameRenderer.projectPointToScreen(worldPosition);

		if (!Double.isFinite(screenPos.x) || !Double.isFinite(screenPos.y) || !Double.isFinite(screenPos.z)) {
			return null;
		}
		if (screenPos.z < 0.0D || screenPos.z > 1.0D) {
			return null;
		}

		int guiWidth = client.getWindow().getGuiScaledWidth();
		int guiHeight = client.getWindow().getGuiScaledHeight();
		float scaledX = (float) ((screenPos.x * 0.5D + 0.5D) * guiWidth);
		float scaledY = (float) ((1.0D - (screenPos.y * 0.5D + 0.5D)) * guiHeight);
		return new ProjectedPoint(scaledX, scaledY);
	}

	/**
	 * 将实体碰撞箱顶部中心投影到当前窗口的 GUI 缩放坐标。
	 * 如果顶部中心无法投影，则从四个顶部角点中选择屏幕 Y 坐标最小的可见点。
	 *
	 * @param entity 要投影的实体
	 * @param partialTicks 当前渲染帧的部分 tick，用于插值实体位置
	 * @return 投影后的 GUI 坐标；所有候选点均不可见时返回 null
	 */
	public static ProjectedPoint projectBoundingBoxTopCenter(Entity entity, float partialTicks) {
		Vec3 basePos = entity.getPosition(partialTicks);
		double halfWidth = entity.getBbWidth() * 0.5D + 0.05D;
		double height = entity.getBbHeight() + 0.05D;

		ProjectedPoint center = projectWorldPoint(new Vec3(basePos.x, basePos.y + height, basePos.z));
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
			ProjectedPoint projected = projectWorldPoint(candidate);
			if (projected != null && (best == null || projected.y() < best.y())) {
				best = projected;
			}
		}

		return best;
	}

	/**
	 * 绘制一条只有描边的 HUD 线段。
	 *
	 * @param context 当前版本的 GUI 绘制上下文
	 * @param startX 线段起点相对于 GUI 缩放坐标的 X 坐标
	 * @param startY 线段起点相对于 GUI 缩放坐标的 Y 坐标
	 * @param endX 线段终点相对于 GUI 缩放坐标的 X 坐标
	 * @param endY 线段终点相对于 GUI 缩放坐标的 Y 坐标
	 * @param color 线段颜色，使用 ARGB 格式
	 * @param physicalPixelScale GUI 缩放像素与物理像素的比例
	 */
	public static void drawLine(
			//#if MC >= 1.21.11
			//$$ GuiContext context,
			//#else
			GuiGraphics context,
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
			RenderUtils.drawRect(context, x, y, LINE_THICKNESS, LINE_THICKNESS, color, physicalPixelScale);
		}
	}

	/** 世界坐标经过投影后得到的 GUI 缩放坐标。 */
	public record ProjectedPoint(float x, float y) {
	}
}
