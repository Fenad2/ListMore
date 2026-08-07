package com.listmore.schematic;

// 原理图预览的版本专属渲染后端，公共预览逻辑只通过这个接口调用
public interface SchematicPreviewRenderBackend extends AutoCloseable {
	default void setSchematic(Object schematic) {
	}

	// 将当前快照绘制到预览区域；无法绘制时返回false
	boolean render(Object context, SchematicPreviewLayout layout, SchematicPreviewTransform transform,
			SchematicPreviewModel model, long revision);

	@Override
	default void close() {
	}
}
