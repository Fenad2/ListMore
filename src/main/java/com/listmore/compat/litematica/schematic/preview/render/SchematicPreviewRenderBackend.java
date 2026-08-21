package com.listmore.compat.litematica.schematic.preview.render;

import com.listmore.compat.litematica.schematic.preview.model.SchematicPreviewModel;
import com.listmore.compat.litematica.schematic.preview.SchematicPreviewTransform;
import com.listmore.compat.litematica.schematic.preview.gui.SchematicPreviewLayout;
// 原理图预览的版本专属渲染后端，公共预览逻辑只通过这个接口调用
public interface SchematicPreviewRenderBackend extends AutoCloseable {
	// clearModel 只清除当前模型的网格并保留后端, close 才会彻底释放后端资源
	default void clearModel() {
	}

	default boolean hasFailure() {
		return false;
	}

	// 将当前快照绘制到预览区域；无法绘制时返回false
	boolean render(Object context, SchematicPreviewLayout layout, SchematicPreviewTransform transform,
			SchematicPreviewModel model, long revision);

	@Override
	default void close() {
	}
}
