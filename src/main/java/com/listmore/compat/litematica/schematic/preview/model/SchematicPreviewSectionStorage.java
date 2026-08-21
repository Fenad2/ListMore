package com.listmore.compat.litematica.schematic.preview.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 同一批 Section 的遍历视图和坐标索引
// List 保留扫描顺序供渲染遍历，Map 支持按世界相对坐标快速查询
final class SchematicPreviewSectionStorage {
	private final List<SchematicPreviewModel.Section> values;
	private final Map<Long, SchematicPreviewModel.Section> byPosition;
	private final int blockCount;

	SchematicPreviewSectionStorage(Map<Long, SchematicPreviewModel.Section> sections) {
		this.byPosition = Collections.unmodifiableMap(new LinkedHashMap<>(sections));
		this.values = List.copyOf(this.byPosition.values());
		this.blockCount = this.values.stream().mapToInt(SchematicPreviewModel.Section::blockCount).sum();
	}

	List<SchematicPreviewModel.Section> values() {
		return this.values;
	}

	int blockCount() {
		return this.blockCount;
	}

	boolean isEmpty() {
		return this.values.isEmpty();
	}

	BlockState blockStateAt(int x, int y, int z) {
		SchematicPreviewModel.Section section = this.byPosition.get(packPosition(x >> 4, y >> 4, z >> 4));
		return section != null ? section.stateAt(x & 15, y & 15, z & 15) : Blocks.AIR.defaultBlockState();
	}

	static long packPosition(int x, int y, int z) {
		// 此编码是 Scanner 合并 Section 与模型查询的共同键格式。
		return ((long) x << 42) | ((long) y << 21) | z;
	}

	static SchematicPreviewSectionStorage empty() {
		return new SchematicPreviewSectionStorage(Map.of());
	}
}
