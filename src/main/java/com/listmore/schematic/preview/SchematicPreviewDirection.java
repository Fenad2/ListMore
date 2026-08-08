package com.listmore.schematic.preview;

public enum SchematicPreviewDirection {
	UP("↑"),
	LEFT("←"),
	RESET("↻"),
	RIGHT("→"),
	DOWN("↓");

	private final String label;

	SchematicPreviewDirection(String label) {
		this.label = label;
	}

	public String label() {
		return this.label;
	}
}
