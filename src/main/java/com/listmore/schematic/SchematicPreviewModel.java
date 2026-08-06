package com.listmore.schematic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.selection.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 原理图预览使用的只读方块快照。
 * 方块坐标相对于整个原理图的最小角，center 始终是完整包围盒的中心。
 */
public final class SchematicPreviewModel {
	private final Vec3i size;
	private final float centerX;
	private final float centerY;
	private final float centerZ;
	private final List<Block> blocks;
	private final Map<Long, BlockState> statesByPosition;
	private final Map<Long, Object> blockEntitiesByPosition;

	private SchematicPreviewModel(Vec3i size, float centerX, float centerY, float centerZ, List<Block> blocks,
			Map<Long, BlockState> statesByPosition, Map<Long, Object> blockEntitiesByPosition) {
		this.size = size;
		this.centerX = centerX;
		this.centerY = centerY;
		this.centerZ = centerZ;
		this.blocks = List.copyOf(blocks);
		this.statesByPosition = Map.copyOf(statesByPosition);
		this.blockEntitiesByPosition = Map.copyOf(blockEntitiesByPosition);
	}

	public Vec3i size() { return this.size; }
	public float centerX() { return this.centerX; }
	public float centerY() { return this.centerY; }
	public float centerZ() { return this.centerZ; }
	public List<Block> blocks() { return this.blocks; }

	public Object blockEntityDataAt(int x, int y, int z) {
		return this.blockEntitiesByPosition.get(packPosition(x, y, z));
	}

	/**
	 * 查询预览坐标中的方块状态。
	 * 坐标以原理图整体包围盒的最小角为原点，范围外和空气位置均返回空气，
	 * 因此渲染后端不需要读取客户端当前世界。
	 */
	public BlockState blockStateAt(int x, int y, int z) {
		if (x < 0 || y < 0 || z < 0 || x >= this.size.getX() || y >= this.size.getY() || z >= this.size.getZ()) {
			return Blocks.AIR.defaultBlockState();
		}
		return this.statesByPosition.getOrDefault(packPosition(x, y, z), Blocks.AIR.defaultBlockState());
	}

	/** 从 Litematica 已解析的数据中提取非空气方块，不创建或修改投影世界。 */
	public static SchematicPreviewModel from(LitematicaSchematic schematic) {
		Map<String, Box> areas = schematic.getAreas();
		if (areas.isEmpty()) {
			return empty();
		}

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
			int originX = Math.min(first.getX(), second.getX());
			int originY = Math.min(first.getY(), second.getY());
			int originZ = Math.min(first.getZ(), second.getZ());
			Vec3i regionSize = container.getSize();
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

		List<Block> blocks = new ArrayList<>();
		Map<Long, BlockState> statesByPosition = new HashMap<>();
		Map<Long, Object> blockEntitiesByPosition = new HashMap<>();
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
			for (int y = 0; y < regionSize.getY(); y++) {
				for (int z = 0; z < regionSize.getZ(); z++) {
					for (int x = 0; x < regionSize.getX(); x++) {
					BlockState state = container.get(x, y, z);
					if (!state.isAir()) {
						int relativeX = x + originX - minX;
						int relativeY = y + originY - minY;
						int relativeZ = z + originZ - minZ;
						long position = packPosition(relativeX, relativeY, relativeZ);
						if (statesByPosition.putIfAbsent(position, state) == null) {
							blocks.add(new Block(relativeX, relativeY, relativeZ, state));
						}
					}
					}
				}
			}
			Map<BlockPos, ?> blockEntities = schematic.getBlockEntityMapForRegion(entry.getKey());
			if (blockEntities != null) {
				for (Map.Entry<BlockPos, ?> blockEntity : blockEntities.entrySet()) {
					if (blockEntity.getValue() == null) {
						continue;
					}
					BlockPos position = blockEntity.getKey();
					int relativeX = position.getX() + originX - minX;
					int relativeY = position.getY() + originY - minY;
					int relativeZ = position.getZ() + originZ - minZ;
					blockEntitiesByPosition.putIfAbsent(packPosition(relativeX, relativeY, relativeZ), blockEntity.getValue());
				}
			}
		}

		Vec3i size = new Vec3i(maxXExclusive - minX, maxYExclusive - minY, maxZExclusive - minZ);
		return new SchematicPreviewModel(size, size.getX() * 0.5F, size.getY() * 0.5F, size.getZ() * 0.5F,
				blocks, statesByPosition, blockEntitiesByPosition);
	}

	private static SchematicPreviewModel empty() {
		return new SchematicPreviewModel(BlockPos.ZERO, 0.0F, 0.0F, 0.0F, List.of(), Map.of(), Map.of());
	}

	/**
	 * 每个轴保留 21 位；原理图尺寸远小于该上限，同时避免为每次邻接方块查询创建 BlockPos 对象。
	 */
	private static long packPosition(int x, int y, int z) {
		return ((long) x << 42) | ((long) y << 21) | z;
	}

	/** 单个非空气方块及其相对原理图坐标。 */
	public record Block(int x, int y, int z, BlockState state) {
	}
}
