package com.listmore.schematic;

import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;

//#if MC >= 1.21.11
//$$ import fi.dy.masa.malilib.render.GuiContext;
//#else
import net.minecraft.client.gui.GuiGraphics;
//#endif

/** 原理图浏览器右侧区域的 GUI 覆盖层。实际方块渲染由后续独立渲染器负责。 */
public final class SchematicPreviewOverlay {
	private static final int BACKGROUND_COLOR = 0xEE111820;
	private static final int BORDER_COLOR = 0xFF5C7185;
	private static final int BUTTON_COLOR = 0xFF263846;
	private static final int BUTTON_HOVER_COLOR = 0xFF3E6572;

	private SchematicPreviewOverlay() {
	}

	/** 绘制预览背景、加载状态和八方向操作按钮。 */
	public static void draw(
			//#if MC >= 1.21.11
			//$$ GuiContext context,
			//#else
			GuiGraphics context,
			//#endif
			SchematicPreviewLayout layout, SchematicPreviewSession session, int mouseX, int mouseY) {
		RenderUtils.drawRect(context, layout.x(), layout.y(), layout.width(), layout.height(), BACKGROUND_COLOR);
		drawBorder(context, layout);

		if (session.isLoading()) {
			drawCentered(context, layout, StringUtils.translate("listmore.schematic_preview.loading"));
		} else if (session.hasFailure()) {
			drawCentered(context, layout, StringUtils.translate("listmore.schematic_preview.failed"));
		} else if (session.model() != null) {
			boolean rendered = session.renderer().render(context, layout, session.transform());
			if (!rendered) {
				drawModelPlaceholder(context, layout, session.model().blocks().size());
			}
		} else {
			drawCentered(context, layout, StringUtils.translate("listmore.schematic_preview.empty"));
		}

		for (SchematicPreviewDirection direction : SchematicPreviewDirection.values()) {
			SchematicPreviewLayout.ButtonBounds button = layout.buttonBounds(direction);
			drawButton(context, button, direction.label(), mouseX, mouseY);
		}
		drawButton(context, layout.zoomOutBounds(), "-", mouseX, mouseY);
		drawButton(context, layout.zoomInBounds(), "+", mouseX, mouseY);
	}

	private static void drawButton(
			//#if MC >= 1.21.11
			//$$ GuiContext context,
			//#else
			GuiGraphics context,
			//#endif
			SchematicPreviewLayout.ButtonBounds button, String label, int mouseX, int mouseY) {
		int color = button.contains(mouseX, mouseY) ? BUTTON_HOVER_COLOR : BUTTON_COLOR;
		RenderUtils.drawRect(context, button.x(), button.y(), button.width(), button.height(), color);
		drawText(context, button.x() + 5, button.y() + 4, 0xFFFFFFFF, label);
	}

	private static void drawBorder(
			//#if MC >= 1.21.11
			//$$ GuiContext context,
			//#else
			GuiGraphics context,
			//#endif
			SchematicPreviewLayout layout) {
		RenderUtils.drawRect(context, layout.x(), layout.y(), layout.width(), 1, BORDER_COLOR);
		RenderUtils.drawRect(context, layout.x(), layout.y() + layout.height() - 1, layout.width(), 1, BORDER_COLOR);
		RenderUtils.drawRect(context, layout.x(), layout.y(), 1, layout.height(), BORDER_COLOR);
		RenderUtils.drawRect(context, layout.x() + layout.width() - 1, layout.y(), 1, layout.height(), BORDER_COLOR);
	}

	private static void drawModelPlaceholder(
			//#if MC >= 1.21.11
			//$$ GuiContext context,
			//#else
			GuiGraphics context,
			//#endif
		SchematicPreviewLayout layout, int blockCount) {
		int centerX = layout.x() + layout.width() / 2;
		int centerY = layout.y() + Math.max(20, (layout.height() - 56) / 2);
		int halfWidth = Math.min(36, Math.max(12, layout.width() / 4));
		int halfHeight = Math.min(25, Math.max(8, layout.height() / 6));
		RenderUtils.drawRect(context, centerX - halfWidth, centerY - halfHeight, halfWidth * 2, 1, 0xFF8CC6A2);
		RenderUtils.drawRect(context, centerX - halfWidth, centerY + halfHeight, halfWidth * 2, 1, 0xFF8CC6A2);
		RenderUtils.drawRect(context, centerX - halfWidth, centerY - halfHeight, 1, halfHeight * 2, 0xFF8CC6A2);
		RenderUtils.drawRect(context, centerX + halfWidth, centerY - halfHeight, 1, halfHeight * 2, 0xFF8CC6A2);
		drawCenteredAt(context, centerX, centerY - 4, 0xFFBFD9C7,
			StringUtils.translate("listmore.schematic_preview.building"));
		drawCenteredAt(context, centerX, centerY + 8, 0xFF9FB4C2,
			StringUtils.translate("listmore.schematic_preview.blocks", blockCount));
	}

	private static void drawCentered(
			//#if MC >= 1.21.11
			//$$ GuiContext context,
			//#else
			GuiGraphics context,
			//#endif
			SchematicPreviewLayout layout, String text) {
		int x = layout.x() + Math.max(4, (layout.width() - text.length() * 6) / 2);
		int y = layout.y() + Math.max(8, layout.height() / 2 - 4);
		drawText(context, x, y, 0xFFB9C6D0, text);
	}

	private static void drawCenteredAt(
			//#if MC >= 1.21.11
			//$$ GuiContext context,
			//#else
			GuiGraphics context,
			//#endif
			int centerX, int y, int color, String text) {
		drawText(context, centerX - text.length() * 3, y, color, text);
	}

	private static void drawText(
			//#if MC >= 1.21.11
			//$$ GuiContext context,
			//#else
			GuiGraphics context,
			//#endif
			int x, int y, int color, String text) {
		//#if MC >= 1.21.11
		//$$ StringUtils.drawString(context, x, y, color, text);
		//#else
		StringUtils.drawString(x, y, color, text, context);
		//#endif
	}
}
