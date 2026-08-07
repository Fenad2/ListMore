package com.listmore.schematic;

// 固定预览方向的相机状态
public final class SchematicPreviewTransform {
	private static final float DEFAULT_YAW = 45.0F;
	private static final float DEFAULT_PITCH = -25.0F;
	private static final float ROTATION_STEP = 45.0F;
	private static final float MAX_PITCH = 89.0F;
	private static final float ZOOM_STEP = 1.25F;
	private static final float MIN_ZOOM = 0.5F;
	private static final float MAX_ZOOM = 2.0F;

	private float yaw = DEFAULT_YAW;
	private float pitch = DEFAULT_PITCH;
	private float zoom = 1.0F;

	public float yaw() {
		return this.yaw;
	}

	public float pitch() {
		return this.pitch;
	}

	// 根据模型包围盒尺寸计算相机距离，半径取包围盒对角线的一半，乘以 2.4 倍再除以缩放因子
	// 最小距离 4.0 防止模型过小时相机穿透
	public float distance(float sizeX, float sizeY, float sizeZ) {
		float radius = (float) Math.sqrt(sizeX * sizeX + sizeY * sizeY + sizeZ * sizeZ) * 0.5F;
		return Math.max(4.0F, radius * 2.4F) / this.zoom;
	}

	public void reset() {
		this.yaw = DEFAULT_YAW;
		this.pitch = DEFAULT_PITCH;
		this.zoom = 1.0F;
	}

	public void zoomIn() {
		this.zoom = Math.min(MAX_ZOOM, this.zoom * ZOOM_STEP);
	}

	public void zoomOut() {
		this.zoom = Math.max(MIN_ZOOM, this.zoom / ZOOM_STEP);
	}

	// 将 UI 方向按钮映射为相机旋转，每次旋转 45°
	// UP/DOWN 改变俯仰角，LEFT/RIGHT 改变偏航角，RESET 恢复默认视角
	public void applyDirection(SchematicPreviewDirection direction) {
		if (direction == SchematicPreviewDirection.RESET) {
			this.reset();
			return;
		}

		switch (direction) {
			case UP -> this.rotate(0.0F, -ROTATION_STEP);
			case LEFT -> this.rotate(ROTATION_STEP, 0.0F);
			case RIGHT -> this.rotate(-ROTATION_STEP, 0.0F);
			case DOWN -> this.rotate(0.0F, ROTATION_STEP);
			case RESET -> { }
		}
	}
	//TODO:话说如果未来要换成鼠标拖曳的话还是四元数好点

	// 偏航角累加后归一化到 [0, 360)，俯仰角限制在 [-89°, 89°] 防止万向节锁
	private void rotate(float yawDelta, float pitchDelta) {
		this.yaw = (this.yaw + yawDelta) % 360.0F;
		if (this.yaw < 0.0F) {
			this.yaw += 360.0F;
		}
		this.pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, this.pitch + pitchDelta));
	}
}
