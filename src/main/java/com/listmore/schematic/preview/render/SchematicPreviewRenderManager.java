package com.listmore.schematic.preview.render;

import com.listmore.schematic.preview.SchematicPreviewModel;
import com.listmore.schematic.preview.SchematicPreviewTransform;
import com.listmore.schematic.preview.gui.SchematicPreviewLayout;

//#if MC >= 1.21.11
//$$ import fi.dy.masa.malilib.render.GuiContext;
//#else
import net.minecraft.client.gui.GuiGraphics;
//#endif

public final class SchematicPreviewRenderManager implements AutoCloseable {
	private SchematicPreviewModel model;
	private Object schematic;
	private long modelRevision;
	private final SchematicPreviewRenderBackend backend = createBackend();

	// 提交新的原理图快照，模型只会在 GUI 线程调用此方法
	public void setModel(SchematicPreviewModel model) {
		if (this.model == model) {
			return;
		}
		this.model = model;
		this.modelRevision++;
	}

	public void setSchematic(Object schematic) {
		this.schematic = schematic;
		if (this.backend != null) {
			this.backend.setSchematic(schematic);
		}
	}

	// 返回当前快照的版本号
	public long modelRevision() {
		return this.modelRevision;
	}

	// 将预览模型绘制到右侧信息面板
	// 版本差异由渲染后端处理：通过接口委托给版本专属的 SchematicPreviewRenderer 实现
	public boolean render(
			//#if MC >= 1.21.11
			//$$ GuiContext context,
			//#else
			GuiGraphics context,
			//#endif
			SchematicPreviewLayout layout, SchematicPreviewTransform transform) {
		return this.backend != null && this.backend.render(context, layout, transform, this.model, this.modelRevision);
	}

	public SchematicPreviewModel model() {
		return this.model;
	}

	// 关闭预览时释放渲染资源
	@Override
	public void close() {
		if (this.backend != null) {
			this.backend.close();
		}
		this.model = null;
		this.schematic = null;
		this.modelRevision++;
	}

	// 通过反射加载版本专属的渲染后端，避免编译时硬依赖
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
