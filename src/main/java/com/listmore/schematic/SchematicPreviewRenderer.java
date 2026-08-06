package com.listmore.schematic;

//#if MC >= 1.21.11
//$$ import fi.dy.masa.malilib.render.GuiContext;
//#else
import net.minecraft.client.gui.GuiGraphics;
//#endif

/**
 * 原理图预览渲染器的公共生命周期入口。
 *
 * <p>它只持有 ListMore 自己生成的只读模型快照，不读取或修改 Litematica 的全局投影世界、
 * 投影摆放和世界渲染器。不同 Minecraft 图形管线的具体实现放在独立适配器中。</p>
 */
public final class SchematicPreviewRenderer implements AutoCloseable {
	private SchematicPreviewModel model;
	private Object schematic;
	private long modelRevision;
	private final SchematicPreviewRenderBackend backend = createBackend();

	/**
	 * 提交新的原理图快照。
	 * 模型只会在 GUI 线程调用此方法，因此适配器可以在下一帧安全地重建自己的网格和帧缓冲资源。
	 */
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

	/** 返回当前快照的递增版本号，供版本渲染适配器判断是否需要重建网格。 */
	public long modelRevision() {
		return this.modelRevision;
	}

	/**
	 * 将预览模型绘制到右侧的信息面板中。
	 * 渲染后端和 GUI 上下文的 API 差异被限制在这里，公共会话层不需要了解 Minecraft 的图形管线。
	 */
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

	/**
	 * 预览浏览器关闭时释放渲染器持有的快照。
	 * GPU 帧缓冲和网格资源会由对应版本的渲染适配器在这里一并释放。
	 */
	@Override
	public void close() {
		if (this.backend != null) {
			this.backend.close();
		}
		this.model = null;
		this.schematic = null;
		this.modelRevision++;
	}

	private static SchematicPreviewRenderBackend createBackend() {
		try {
			Class<?> backendClass = Class.forName("com.listmore.schematic.SchematicPreviewRenderer26");
			return backendClass.asSubclass(SchematicPreviewRenderBackend.class).getConstructor().newInstance();
		} catch (ReflectiveOperationException ignored) {
			// 当前版本尚未提供专属后端时保留 GUI 占位预览。
			return null;
		}
	}
}
