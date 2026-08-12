package com.listmore.schematic.preview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.ILitematicaBlockStatePalette;
import fi.dy.masa.litematica.schematic.container.LitematicaBitArray;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.selection.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class SchematicPreviewModel {
	private final Vec3i size;
	private final float centerX;
	private final float centerY;
	private final float centerZ;
	private final List<Block> blocks;
	private final Map<Long, BlockState> statesByPosition;

	private SchematicPreviewModel(Vec3i size, float centerX, float centerY, float centerZ, List<Block> blocks,
			Map<Long, BlockState> statesByPosition) {
		this.size = size;
		this.centerX = centerX;
		this.centerY = centerY;
		this.centerZ = centerZ;
		this.blocks = Collections.unmodifiableList(blocks);
		this.statesByPosition = Collections.unmodifiableMap(statesByPosition);
	}

	public Vec3i size() { return this.size; }
	public float centerX() { return this.centerX; }
	public float centerY() { return this.centerY; }
	public float centerZ() { return this.centerZ; }
	public List<Block> blocks() { return this.blocks; }

	// 查询预览坐标中的方块状态
	public BlockState blockStateAt(int x, int y, int z) {
		if (x < 0 || y < 0 || z < 0 || x >= this.size.getX() || y >= this.size.getY() || z >= this.size.getZ()) {
			return Blocks.AIR.defaultBlockState();
		}
		return this.statesByPosition.getOrDefault(packPosition(x, y, z), Blocks.AIR.defaultBlockState());
	}

	// 从 Litematica 已解析的数据中提取非空气方块
	// 流程：遍历所有区域 -> 计算包围盒 -> 遍历容器提取非空气方块并转为相对坐标 -> 收集方块实体数据
	public static SchematicPreviewModel from(LitematicaSchematic schematic) {
		Map<String, Box> areas = schematic.getAreas();
		if (areas.isEmpty()) {
			return empty();
		}

		// 遍历所有区域计算包围盒
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxXExclusive = Integer.MIN_VALUE;
		int maxYExclusive = Integer.MIN_VALUE;
		int maxZExclusive = Integer.MIN_VALUE;
		for (Map.Entry<String, Box> entry : areas.entrySet()) {
			LitematicaBlockStateContainer container = schematic.getSubRegionContainer(entry.getKey());
			Box area = entry.getValue();
			BlockPos first = area.getPos1();
			BlockPos second = area.getPos2();
			if (container == null || first == null || second == null) {
				continue;
			}
			// 区域原点取两角点中较小者
			int originX = Math.min(first.getX(), second.getX());
			int originY = Math.min(first.getY(), second.getY());
			int originZ = Math.min(first.getZ(), second.getZ());
			Vec3i regionSize = container.getSize();
			// 更新全局包围盒：原点取最小，远端取 origin+size 的最大值
			minX = Math.min(minX, originX);
			minY = Math.min(minY, originY);
			minZ = Math.min(minZ, originZ);
			maxXExclusive = Math.max(maxXExclusive, originX + regionSize.getX());
			maxYExclusive = Math.max(maxYExclusive, originY + regionSize.getY());
			maxZExclusive = Math.max(maxZExclusive, originZ + regionSize.getZ());
		}
		if (minX == Integer.MAX_VALUE) {
			return empty();
		}

		// 从每个区域中提取非空气方块，转换为相对坐标
		List<Block> blocks = new ArrayList<>();
		Map<Long, BlockState> statesByPosition = new HashMap<>();
		for (Map.Entry<String, Box> entry : areas.entrySet()) {
			LitematicaBlockStateContainer container = schematic.getSubRegionContainer(entry.getKey());
			BlockPos first = entry.getValue().getPos1();
			BlockPos second = entry.getValue().getPos2();
			if (container == null || first == null || second == null) {
				continue;
			}
			Vec3i regionSize = container.getSize();
			int originX = Math.min(first.getX(), second.getX());
			int originY = Math.min(first.getY(), second.getY());
			int originZ = Math.min(first.getZ(), second.getZ());
			// 遍历容器内所有方块，只保留非空气方块
			LitematicaBitArray storage = container.getArray();
			ILitematicaBlockStatePalette palette = container.getPalette();
			boolean[] airIds = findAirIds(palette);
			long storageIndex = 0L;
			for (int y = 0; y < regionSize.getY(); y++) {
				for (int z = 0; z < regionSize.getZ(); z++) {
					for (int x = 0; x < regionSize.getX(); x++) {
					int paletteId = storage.getAt(storageIndex++);
					if (paletteId < 0 || paletteId >= airIds.length || !airIds[paletteId]) {
						BlockState state = palette.getBlockState(paletteId);
						if (state == null || state.isAir()) {
							continue;
						}
						// 绝对坐标 -> 相对坐标：减去全局包围盒原点
						int relativeX = x + originX - minX;
						int relativeY = y + originY - minY;
						int relativeZ = z + originZ - minZ;
						long position = packPosition(relativeX, relativeY, relativeZ);
						// putIfAbsent 防止重叠区域重复添加同一位置的方块
						if (statesByPosition.putIfAbsent(position, state) == null) {
							blocks.add(new Block(relativeX, relativeY, relativeZ, state));
						}
					}
					}
				}
			}
		}

		// 包围盒尺寸 = 远端 - 原点，中心 = 尺寸 * 0.5（几何中心）
		Vec3i size = new Vec3i(maxXExclusive - minX, maxYExclusive - minY, maxZExclusive - minZ);
		return new SchematicPreviewModel(size, size.getX() * 0.5F, size.getY() * 0.5F, size.getZ() * 0.5F,
				blocks, statesByPosition);
	}

	private static SchematicPreviewModel empty() {
		return new SchematicPreviewModel(BlockPos.ZERO, 0.0F, 0.0F, 0.0F, List.of(), Map.of());
	}

	private static boolean[] findAirIds(ILitematicaBlockStatePalette palette) {
		boolean[] airIds = new boolean[palette.getPaletteSize()];
		for (int id = 0; id < airIds.length; id++) {
			BlockState state = palette.getBlockState(id);
			airIds[id] = state == null || state.isAir();
		}
		return airIds;
	}
	//突然想到一个很神的点子，如果用c/c++或者rust去写计算部分呢？真神人了

	// 将坐标打包为 long
	private static long packPosition(int x, int y, int z) {
		return ((long) x << 42) | ((long) y << 21) | z;
	}

	// 单个非空气方块及其相对原理图坐标
	public record Block(int x, int y, int z, BlockState state) {
	}
}
