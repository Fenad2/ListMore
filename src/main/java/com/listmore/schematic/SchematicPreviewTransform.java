package com.listmore.schematic;

/** Camera state for the fixed preview directions. */
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

	public void applyDirection(SchematicPreviewDirection direction) {
		if (direction == SchematicPreviewDirection.CENTER) {
			this.reset();
			return;
		}

		switch (direction) {
			case NORTH -> this.rotate(0.0F, -ROTATION_STEP);
			case WEST -> this.rotate(ROTATION_STEP, 0.0F);
			case EAST -> this.rotate(-ROTATION_STEP, 0.0F);
			case SOUTH -> this.rotate(0.0F, ROTATION_STEP);
			case CENTER -> { }
		}
	}

	private void rotate(float yawDelta, float pitchDelta) {
		this.yaw = (this.yaw + yawDelta) % 360.0F;
		if (this.yaw < 0.0F) {
			this.yaw += 360.0F;
		}
		this.pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, this.pitch + pitchDelta));
	}
}
