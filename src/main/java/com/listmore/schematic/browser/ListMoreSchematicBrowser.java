package com.listmore.schematic.browser;

import java.util.Date;
import com.listmore.schematic.preview.SchematicPreviewSession;
import com.listmore.schematic.preview.gui.SchematicPreviewLayout;
import com.listmore.schematic.preview.gui.SchematicPreviewOverlay;
import org.jetbrains.annotations.Nullable;

import fi.dy.masa.litematica.gui.GuiSchematicBrowserBase;
import fi.dy.masa.litematica.gui.widgets.WidgetSchematicBrowser;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.SchematicMetadata;
import fi.dy.masa.litematica.schematic.SchematicSchema;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase.DirectoryEntry;
//#if MC >= 1.21.11
//$$ import fi.dy.masa.malilib.render.GuiContext;
//#else
import net.minecraft.client.gui.GuiGraphics;
//#endif
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.data.Schema;
//#if MC >= 1.21.10
//$$ import net.minecraft.client.input.MouseButtonEvent;
//#endif
import net.minecraft.core.Vec3i;
import org.apache.commons.lang3.tuple.Pair;

public final class ListMoreSchematicBrowser extends WidgetSchematicBrowser {
	private final SchematicPreviewSession previewSession = new SchematicPreviewSession();
	@Nullable private SchematicPreviewLayout previewLayout;
	private int lastMouseX;
	private int lastMouseY;
	private int previewDragButton = -1;

	public ListMoreSchematicBrowser(int x, int y, int width, int height, GuiSchematicBrowserBase parent,
			@Nullable ISelectionListener<DirectoryEntry> selectionListener) {
		super(x, y, width, height, parent, selectionListener);
	}

	@Override
	protected void drawAdditionalContents(
			//#if MC >= 1.21.11
			//$$ GuiContext context,
			//#else
			GuiGraphics context,
			//#endif
			int mouseX, int mouseY) {
		this.lastMouseX = mouseX;
		this.lastMouseY = mouseY;
		super.drawAdditionalContents(context, mouseX, mouseY);
	}

	@Override
	protected void drawSelectedSchematicInfo(
			//#if MC >= 1.21.11
			//$$ GuiContext context,
			//#else
			GuiGraphics context,
			//#endif
			@Nullable DirectoryEntry entry) {
		if (!isLitematic(entry)) {
			this.previewLayout = null;
			this.previewSession.clear();
			super.drawSelectedSchematicInfo(context, entry);
			return;
		}

		Pair<SchematicSchema, SchematicMetadata> metaPair = this.getSchematicVersionAndMetadata(entry);
		if (metaPair == null || metaPair.getRight() == null) {
			this.previewLayout = null;
			this.previewSession.clear();
			super.drawSelectedSchematicInfo(context, entry);
			return;
		}

		int x = this.posX + this.totalWidth - this.infoWidth;
		int y = this.posY;
		int height = Math.min(this.infoHeight, this.parent.getMaxInfoHeight());
		RenderUtils.drawOutlinedBox(context, x, y, this.infoWidth, height, 0xA0000000, COLOR_HORIZONTAL_BAR);
		x += 3;
		y += 3;
		int textColor = 0xC0C0C0C0;
		int valueColor = 0xFFFFFFFF;
		SchematicMetadata meta = metaPair.getRight();
		SchematicSchema version = metaPair.getLeft();

		this.drawString(context, StringUtils.translate("litematica.gui.label.schematic_info.name"), x, y, textColor);
		y += 12;
		this.drawString(context, meta.getName(), x + 4, y, valueColor);
		y += 12;
		this.drawString(context, StringUtils.translate("litematica.gui.label.schematic_info.schematic_author", meta.getAuthor()), x, y, textColor);
		y += 12;
		this.drawString(context, StringUtils.translate("litematica.gui.label.schematic_info.time_created", DATE_FORMAT.format(new Date(meta.getTimeCreated()))), x, y, textColor);
		y += 12;
		if (meta.hasBeenModified()) {
			this.drawString(context, StringUtils.translate("litematica.gui.label.schematic_info.time_modified", DATE_FORMAT.format(new Date(meta.getTimeModified()))), x, y, textColor);
			y += 12;
		}
		this.drawString(context, StringUtils.translate("litematica.gui.label.schematic_info.region_count", meta.getRegionCount()), x, y, textColor);
		y += 12;
		if (this.parent.getScreenHeight() >= 340) {
			this.drawString(context, StringUtils.translate("litematica.gui.label.schematic_info.total_volume", meta.getTotalVolume()), x, y, textColor);
			y += 12;
			if (meta.getTotalBlocks() > 0) {
				this.drawString(context, StringUtils.translate("litematica.gui.label.schematic_info.total_blocks", meta.getTotalBlocks()), x, y, textColor);
				y += 12;
			}
			this.drawString(context, StringUtils.translate("litematica.gui.label.schematic_info.enclosing_size"), x, y, textColor);
			y += 12;
			Vec3i areaSize = meta.getEnclosingSize();
			this.drawString(context, String.format("%d x %d x %d", areaSize.getX(), areaSize.getY(), areaSize.getZ()), x + 4, y, valueColor);
			y += 12;
		} else {
			String size = String.format("%d x %d x %d", meta.getEnclosingSize().getX(), meta.getEnclosingSize().getY(), meta.getEnclosingSize().getZ());
			String total = meta.getTotalBlocks() > 0
					? StringUtils.translate("litematica.gui.label.schematic_info.total_blocks_and_volume", meta.getTotalBlocks(), meta.getTotalVolume())
					: StringUtils.translate("litematica.gui.label.schematic_info.total_volume", meta.getTotalVolume());
			this.drawString(context, total, x, y, textColor);
			y += 12;
			this.drawString(context, StringUtils.translate("litematica.gui.label.schematic_info.enclosing_size_value", size), x, y, textColor);
			y += 12;
		}
		if (version != null) {
			this.drawString(context, StringUtils.translate("litematica.gui.label.schematic_info.version", version.litematicVersion()), x, y, textColor);
			y += 12;
			Schema schema = Schema.getSchemaByDataVersion(version.minecraftDataVersion());
			if (schema != null) {
				String key = version.minecraftDataVersion() - LitematicaSchematic.MINECRAFT_DATA_VERSION > 100
						? "litematica.gui.label.schematic_info.schema.newer" : "litematica.gui.label.schematic_info.schema";
				this.drawString(context, StringUtils.translate(key, schema.getString(), version.minecraftDataVersion()), x, y, textColor);
				y += 12;
			}
		}

		y += 12;
		int previewHeight = height - (y - this.posY) - 6;
		this.previewLayout = previewHeight >= 64 ? new SchematicPreviewLayout(x + 4, y, this.infoWidth - 10, previewHeight) : null;
		if (this.previewLayout != null) {
			this.previewSession.setFile(entry.getFullPath());
			this.previewSession.update();
			SchematicPreviewOverlay.draw(context, this.previewLayout, this.previewSession, this.lastMouseX, this.lastMouseY);
		} else {
			this.previewSession.clear();
		}
	}

	@Override
	//#if MC >= 1.21.10
	//$$ public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
	//$$ 		if (click.input() == 0 && this.previewLayout != null
	//$$ 				&& this.previewLayout.resetBounds().contains(click.x(), click.y())) {
	//$$ 			this.previewSession.transform().reset();
	//$$ 			return true;
	//$$ 		}
	//$$ 		if (this.beginPreviewDrag(click.x(), click.y(), click.input())) {
	//$$ 			return true;
	//$$ 		}
	//$$ 		return super.onMouseClicked(click, doubleClick);
	//$$ }
	//#else
	public boolean onMouseClicked(int mouseX, int mouseY, int button) {
		if (button == 0 && this.previewLayout != null
				&& this.previewLayout.resetBounds().contains(mouseX, mouseY)) {
			this.previewSession.transform().reset();
			return true;
		}
		if (this.beginPreviewDrag(mouseX, mouseY, button)) {
			return true;
		}
		return super.onMouseClicked(mouseX, mouseY, button);
	}
	//#endif

	@Override
	//#if MC >= 1.21.10
	//$$ public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount,
	//$$ 		double verticalAmount) {
	//#else
	public boolean onMouseScrolled(int mouseX, int mouseY, double horizontalAmount,
			double verticalAmount) {
	//#endif
		if (verticalAmount != 0.0 && this.previewLayout != null
				&& this.previewLayout.contains(mouseX, mouseY)) {
			this.previewSession.transform().zoomByScroll(verticalAmount);
			return true;
		}
		return super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	//#if MC >= 1.21.11
	//$$ @Override
	//$$ public boolean onMouseDragged(MouseButtonEvent click, double dragXAmount, double dragYAmount) {
	//$$ 	if (this.handlePreviewMouseDragged(click.x(), click.y(), click.input(), dragXAmount, dragYAmount)) {
	//$$ 		return true;
	//$$ 	}
	//$$ 	return super.onMouseDragged(click, dragXAmount, dragYAmount);
	//$$ }

	//$$ @Override
	//$$ public boolean onMouseReleased(MouseButtonEvent click) {
	//$$ 	if (click.input() == this.previewDragButton) {
	//$$ 		this.previewDragButton = -1;
	//$$ 	}
	//$$ 	return super.onMouseReleased(click);
	//$$ }
	//#endif

	//#if MC >= 1.21.10 && MC < 1.21.11
	//$$ @Override
	//$$ public boolean onMouseReleased(MouseButtonEvent click) {
	//$$ 	this.handlePreviewMouseReleased(click.input());
	//$$ 	return super.onMouseReleased(click);
	//$$ }
	//#elseif MC < 1.21.10
	//$$ @Override
	//$$ public boolean onMouseReleased(int mouseX, int mouseY, int button) {
	//$$ 	this.handlePreviewMouseReleased(button);
	//$$ 	return super.onMouseReleased(mouseX, mouseY, button);
	//$$ }
	//#endif

	@Override
	public void onClose() {
		this.previewSession.close();
		super.onClose();
	}

	private static boolean isLitematic(@Nullable DirectoryEntry entry) {
		return entry != null && entry.getName().endsWith(LitematicaSchematic.FILE_EXTENSION);
	}

	private boolean isPreviewControl(double mouseX, double mouseY) {
		return this.previewLayout.resetBounds().contains(mouseX, mouseY);
	}

	private boolean beginPreviewDrag(double mouseX, double mouseY, int button) {
		if ((button == 0 || button == 1) && this.previewLayout != null
				&& this.previewLayout.contains(mouseX, mouseY)
				&& !this.isPreviewControl(mouseX, mouseY)) {
			this.previewDragButton = button;
			return true;
		}
		return false;
	}

	public boolean handlePreviewMouseDragged(double mouseX, double mouseY, int button,
			double dragXAmount, double dragYAmount) {
		if (this.previewDragButton != button || this.previewLayout == null) {
			return false;
		}
		if (button == 0) {
			this.previewSession.transform().rotateBy((float) dragXAmount,
					(float) -dragYAmount);
		} else {
			this.previewSession.transform().panBy((float) dragXAmount, (float) dragYAmount,
					this.previewLayout.contentHeight());
		}
		return true;
	}

	public void handlePreviewMouseReleased(int button) {
		if (this.previewDragButton == button) {
			this.previewDragButton = -1;
		}
	}
}
