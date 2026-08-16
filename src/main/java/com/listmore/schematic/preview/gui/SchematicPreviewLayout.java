package com.listmore.schematic.preview.gui;

// 原理图浏览器右侧预览区域及重置按钮的布局计算
public final class SchematicPreviewLayout {
	private static final int BUTTON_SIZE = 16;
	private static final int EDGE_OFFSET = 4;

	private final int x;
	private final int y;
	private final int width;
	private final int height;

	public SchematicPreviewLayout(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = Math.max(0, width);
		this.height = Math.max(0, height);
	}

	public int x() { return this.x; }
	public int y() { return this.y; }
	public int width() { return this.width; }
	public int height() { return this.height; }

	// 实际可绘制的区域
	public int contentX() { return this.x + 1; }
	public int contentY() { return this.y + 1; }
	public int contentWidth() { return Math.max(0, this.width - 2); }
	public int contentHeight() { return Math.max(0, this.height - 2); }

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= this.x && mouseX < this.x + this.width
				&& mouseY >= this.y && mouseY < this.y + this.height;
	}

	public ButtonBounds resetBounds() {
		return new ButtonBounds(this.x + this.width - BUTTON_SIZE - EDGE_OFFSET,
				this.y + this.height - BUTTON_SIZE - EDGE_OFFSET, BUTTON_SIZE, BUTTON_SIZE);
	}

	public record ButtonBounds(int x, int y, int width, int height) {
		public boolean contains(double mouseX, double mouseY) {
			return mouseX >= this.x && mouseX < this.x + this.width
					&& mouseY >= this.y && mouseY < this.y + this.height;
		}
	}
}
