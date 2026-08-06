package com.listmore.schematic;

/**
 * 原理图预览的版本专属渲染后端。
 *
 * <p>实现类位于各 Minecraft 子版本的源码目录中。公共预览逻辑只通过这个接口调用，
 * 因此不需要在共同源码内引用某个版本独有的图形 API。</p>
 */
public interface SchematicPreviewRenderBackend extends AutoCloseable {
	/** Supplies the parsed schematic to version-specific read-only world adapters. */
	default void setSchematic(Object schematic) {
	}

	/** 将当前快照绘制到预览区域；无法绘制时返回 {@code false}。 */
	boolean render(Object context, SchematicPreviewLayout layout, SchematicPreviewTransform transform,
			SchematicPreviewModel model, long revision);

	@Override
	default void close() {
	}
}
