package com.listmore.compat.litematica.schematic.preview;

// 固定预览方向的相机状态
public final class SchematicPreviewTransform {
	private static final float DEFAULT_YAW = 45.0F;
	private static final float DEFAULT_PITCH = -25.0F;
	private static final float MAX_PITCH = 89.0F;
	private static final float SCROLL_ZOOM_SPEED = 0.12F;
	private static final float MIN_ZOOM = 0.5F;
	private static final float MAX_ZOOM = 4.0F;

	private float yaw = DEFAULT_YAW;
	private float pitch = DEFAULT_PITCH;
	private float zoom = 1.0F;
	private float panX;
	private float panY;

	public float yaw() {
		return this.yaw;
	}

	public float pitch() {
		return this.pitch;
	}

	public float panX() {
		return this.panX;
	}

	public float panY() {
		return this.panY;
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
		this.panX = 0.0F;
		this.panY = 0.0F;
	}

	public void zoomByScroll(double amount) {
		float factor = (float) Math.exp(amount * SCROLL_ZOOM_SPEED);
		this.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, this.zoom * factor));
	}

	public void rotateBy(float yawDelta, float pitchDelta) {
		this.rotate(yawDelta, pitchDelta);
	}

	public void panBy(float deltaX, float deltaY, float viewportHeight) {
		if (viewportHeight > 0.0F) {
			this.panX += deltaX / viewportHeight;
			this.panY += deltaY / viewportHeight;
		}
	}
	//TODO:话说鼠标拖曳的话还是四元数好点，但既然都已经限制了俯仰角了就不改了。能跑就是好代码！

	// 偏航角累加后归一化到 [0, 360)，俯仰角限制在 [-89°, 89°] 防止万向节锁
	private void rotate(float yawDelta, float pitchDelta) {
		this.yaw = (this.yaw + yawDelta) % 360.0F;
		if (this.yaw < 0.0F) {
			this.yaw += 360.0F;
		}
		this.pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, this.pitch + pitchDelta));
	}
}
