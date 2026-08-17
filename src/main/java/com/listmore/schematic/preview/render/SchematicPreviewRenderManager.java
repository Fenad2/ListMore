package com.listmore.schematic.preview.render;

import com.listmore.schematic.preview.model.SchematicPreviewModel;
import com.listmore.schematic.preview.SchematicPreviewTransform;
import com.listmore.schematic.preview.gui.SchematicPreviewLayout;

//#if MC >= 1.21.11
//$$ import fi.dy.masa.malilib.render.GuiContext;
//#else
import net.minecraft.client.gui.GuiGraphics;
//#endif

public final class SchematicPreviewRenderManager implements AutoCloseable {
	private long modelRevision;
	private final SchematicPreviewRenderBackend backend = createBackend();

	// 通知后端模型快照已变化
	public void modelChanged() {
		this.modelRevision++;
	}

	// 将预览模型绘制到右侧信息面板
	// 版本差异由渲染后端处理：通过接口委托给版本专属的 SchematicPreviewRenderer 实现
	public boolean render(
			//#if MC >= 1.21.11
			//$$ GuiContext context,
			//#else
			GuiGraphics context,
			//#endif
			SchematicPreviewLayout layout, SchematicPreviewTransform transform,
			SchematicPreviewModel model) {
		return this.backend != null && this.backend.render(context, layout, transform, model, this.modelRevision);
	}

	public void clearModel() {
		if (this.backend != null) {
			this.backend.clearModel();
		}
		this.modelRevision++;
	}

	public boolean hasFailure() {
		return this.backend != null && this.backend.hasFailure();
	}

	@Override
	public void close() {
		if (this.backend != null) {
			this.backend.close();
		}
		this.modelRevision++;
	}

	// 通过反射加载版本专属的渲染后端
	// 如果当前版本没有对应的 SchematicPreviewRenderer 类，回退到 null（GUI 占位预览）
	private static SchematicPreviewRenderBackend createBackend() {
		try {
			Class<?> backendClass = Class.forName("com.listmore.schematic.preview.render.SchematicPreviewRenderer");
			return backendClass.asSubclass(SchematicPreviewRenderBackend.class).getConstructor().newInstance();
		} catch (ReflectiveOperationException ignored) {
			// 当前版本尚未提供专属后端时保留 GUI 占位预览
			return null;
		}
	}
}
