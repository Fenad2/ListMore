package com.listmore.compat.litematica.schematic.browser;

import java.util.Date;

import com.listmore.ListMore;
import com.listmore.compat.litematica.schematic.preview.SchematicPreviewLoader.SchematicInfo;
import com.listmore.compat.litematica.schematic.preview.SchematicPreviewSession;
import com.listmore.compat.litematica.schematic.preview.gui.SchematicPreviewLayout;
import com.listmore.compat.litematica.schematic.preview.gui.SchematicPreviewOverlay;
import org.jetbrains.annotations.Nullable;

import fi.dy.masa.litematica.gui.GuiSchematicBrowserBase;
import fi.dy.masa.litematica.gui.widgets.WidgetSchematicBrowser;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
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

public final class ListMoreSchematicBrowser extends WidgetSchematicBrowser {
    private final SchematicPreviewSession previewSession = new SchematicPreviewSession();
    @Nullable
    private SchematicPreviewLayout previewLayout;
    private int lastMouseX;
    private int lastMouseY;
    private int previewDragButton = -1;
    //#if MC >= 26.3
    //$$ private static final int PREVIEW_LEFT_BUTTON = 1;
    //$$ private static final int PREVIEW_RIGHT_BUTTON = 3;
    //#else
    private static final int PREVIEW_LEFT_BUTTON = 0;
    private static final int PREVIEW_RIGHT_BUTTON = 1;
    //#endif

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
        this.previewSession.setFile(entry.getFullPath());
        this.previewSession.update();
        SchematicInfo meta = this.previewSession.info();
        int x = this.posX + this.totalWidth - this.infoWidth;
        int y = this.posY;
        int height = Math.min(this.infoHeight, this.parent.getMaxInfoHeight());
        RenderUtils.drawOutlinedBox(context, x, y, this.infoWidth, height, 0xA0000000, COLOR_HORIZONTAL_BAR);
        if (meta == null) {
            this.previewLayout = null;
            String statusKey = this.previewSession.hasFailure()
                    ? "listmore.schematic_preview.failed" : "listmore.schematic_preview.loading";
            this.drawString(context, StringUtils.translate(statusKey), x + 8,
                    y + Math.max(8, height / 2 - 4), 0xFFB9C6D0);
            return;
        }
        x += 3;
        y += 3;
        int textColor = 0xC0C0C0C0;
        int valueColor = 0xFFFFFFFF;
        SchematicSchema version = meta.schema();

        this.drawString(context, StringUtils.translate("litematica.gui.label.schematic_info.name"), x, y, textColor);
        y += 12;
        this.drawString(context, meta.name(), x + 4, y, valueColor);
        y += 12;
        this.drawString(context, StringUtils.translate("litematica.gui.label.schematic_info.schematic_author", meta.author()), x, y, textColor);
        y += 12;
        this.drawString(context, StringUtils.translate("litematica.gui.label.schematic_info.time_created", DATE_FORMAT.format(new Date(meta.timeCreated()))), x, y, textColor);
        y += 12;
        if (meta.modified()) {
            this.drawString(context, StringUtils.translate("litematica.gui.label.schematic_info.time_modified", DATE_FORMAT.format(new Date(meta.timeModified()))), x, y, textColor);
            y += 12;
        }
        this.drawString(context, StringUtils.translate("litematica.gui.label.schematic_info.region_count", meta.regionCount()), x, y, textColor);
        y += 12;
        String size = String.format("%d x %d x %d", meta.sizeX(), meta.sizeY(), meta.sizeZ());
        if (this.parent.getScreenHeight() >= 340) {
            this.drawString(context, StringUtils.translate("litematica.gui.label.schematic_info.total_volume", meta.totalVolume()), x, y, textColor);
            y += 12;
            if (meta.totalBlocks() > 0) {
                this.drawString(context, StringUtils.translate("litematica.gui.label.schematic_info.total_blocks", meta.totalBlocks()), x, y, textColor);
                y += 12;
            }
            this.drawString(context, StringUtils.translate("litematica.gui.label.schematic_info.enclosing_size"), x, y, textColor);
            y += 12;
            this.drawString(context, size, x + 4, y, valueColor);
            y += 12;
        } else {
            String total = meta.totalBlocks() > 0
                    ? StringUtils.translate("litematica.gui.label.schematic_info.total_blocks_and_volume", meta.totalBlocks(), meta.totalVolume())
                    : StringUtils.translate("litematica.gui.label.schematic_info.total_volume", meta.totalVolume());
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
            SchematicPreviewOverlay.draw(context, this.previewLayout, this.previewSession, this.lastMouseX, this.lastMouseY);
        }
    }

    @Override
    //#if MC >= 1.21.10
    //$$ public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
    //$$ 		if (click.input() == PREVIEW_LEFT_BUTTON && this.previewLayout != null
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
        if (button == PREVIEW_LEFT_BUTTON && this.previewLayout != null
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
    @Override
    public boolean onMouseReleased(int mouseX, int mouseY, int button) {
        this.handlePreviewMouseReleased(button);
        return super.onMouseReleased(mouseX, mouseY, button);
    }
    //#endif

    @Override
    public void onClose() {
        this.closePreviewSession();
        super.onClose();
    }

    // 界面销毁时由 GuiListBase.removed() 调用 widget.removed()
    @Override
    public void removed() {
        this.closePreviewSession();
        super.removed();
    }

    // 关闭预览会话
    private void closePreviewSession() {
        try {
            this.previewSession.close();
        } catch (Exception exception) {
            ListMore.LOGGER.warn("Failed to release schematic preview resources", exception);
        }
    }

    private static boolean isLitematic(@Nullable DirectoryEntry entry) {
        return entry != null && entry.getName().endsWith(LitematicaSchematic.FILE_EXTENSION);
    }

    private boolean isPreviewControl(double mouseX, double mouseY) {
        return this.previewLayout.resetBounds().contains(mouseX, mouseY);
    }

    private boolean beginPreviewDrag(double mouseX, double mouseY, int button) {
        if ((button == PREVIEW_LEFT_BUTTON || button == PREVIEW_RIGHT_BUTTON) && this.previewLayout != null
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
        if (button == PREVIEW_LEFT_BUTTON) {
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
