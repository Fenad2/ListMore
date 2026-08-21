package com.listmore.compat.litematica.schematic.preview.model;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class SchematicPreviewModel {
	// 统一包围盒的全局尺寸；所有 Section 坐标均相对于该包围盒原点
	private final Vec3i size;
	private final SchematicPreviewSectionStorage sections;

	SchematicPreviewModel(Vec3i size, SchematicPreviewSectionStorage sections) {
		this.size = size;
		this.sections = sections;
	}

	public Vec3i size() {
		return this.size;
	}

	public List<Section> sections() {
		return this.sections.values();
	}

	public int blockCount() {
		return this.sections.blockCount();
	}

	public boolean isEmpty() {
		return this.sections.isEmpty();
	}

	public BlockState blockStateAt(int x, int y, int z) {
		if (x < 0 || y < 0 || z < 0 || x >= this.size.getX() || y >= this.size.getY() || z >= this.size.getZ()) {
			return Blocks.AIR.defaultBlockState();
		}
		return this.sections.blockStateAt(x, y, z);
	}

	static SchematicPreviewModel empty() {
		return new SchematicPreviewModel(BlockPos.ZERO, SchematicPreviewSectionStorage.empty());
	}

	public static final class Section {
		private final int sectionX;
		private final int sectionY;
		private final int sectionZ;
		// 空气不存入数组，null 表示该局部位置为空气。
		private final BlockState[] states;
		private final int blockCount;

		Section(int sectionX, int sectionY, int sectionZ, BlockState[] states) {
			this.sectionX = sectionX;
			this.sectionY = sectionY;
			this.sectionZ = sectionZ;
			this.states = states;
			int count = 0;
			for (BlockState state : states) {
				if (state != null) {
					count++;
				}
			}
			this.blockCount = count;
		}

		public int sectionX() { return this.sectionX; }
		public int sectionY() { return this.sectionY; }
		public int sectionZ() { return this.sectionZ; }
		public int minX() { return this.sectionX << 4; }
		public int minY() { return this.sectionY << 4; }
		public int minZ() { return this.sectionZ << 4; }
		public int blockCount() { return this.blockCount; }

		public BlockState stateAt(int x, int y, int z) {
			BlockState state = this.states[localIndex(x, y, z)];
			return state != null ? state : Blocks.AIR.defaultBlockState();
		}

		public BlockState stateAtIndex(int index) {
			return this.states[index];
		}
	}

	static int localIndex(int x, int y, int z) {
		// 固定布局：x 为最低 4 位，随后是 z，最高 4 位为 y
		return (y << 8) | (z << 4) | x;
	}
}
