package com.listmore.schematic;

/** 预览区域中的八个固定观察方向。 */
public enum SchematicPreviewDirection {
	NORTH("↑"),
	WEST("←"),
	CENTER("↻"),
	EAST("→"),
	SOUTH("↓");

	private final String label;

	SchematicPreviewDirection(String label) {
		this.label = label;
	}

	public String label() {
		return this.label;
	}
}
