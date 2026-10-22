package com.listmore.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;

/**
 * 把一组高亮方块视为一个体素并集，提取并集的暴露面（填充用）和硬边（描边用）。
 * <p>
 * 只有暴露面会产生候选棱：先跳过与相邻高亮方块共享的内部面，再检查暴露面四周的 4 条棱。
 * 一条棱是否保留由它周围的 4 个方块决定：0/4 个方块、或 2 个共面相邻方块时只是平坦表面的
 * 延续，不画；其余情况（1/3 个方块、2 个对角方块）都是并集的硬边。
 * <p>
 * 结果按 int 数组缓存，重建时复用数组；只允许在客户端渲染线程调用。
 */
public final class BlockHighlightGeometry {
	private static final int FACE_COUNT = 6;
	private static final int EDGES_PER_FACE = 4;
	private static final int CORNERS_PER_FACE = 4;
	private static final int INTS_PER_EDGE = 4; // x, y, z, axis
	private static final int INTS_PER_FACE = 4; // x, y, z, face
	// 每个方块的面：+X、-X、+Y、-Y、+Z、-Z
	private static final int[][] FACE_NORMALS = {
		{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
	};
	// 每个面的面内方向 (u, v)，满足 u × v = 法线
	private static final int[][][] FACE_AXES = {
		{{0, 1, 0}, {0, 0, 1}},
		{{0, 0, 1}, {0, 1, 0}},
		{{0, 0, 1}, {1, 0, 0}},
		{{1, 0, 0}, {0, 0, 1}},
		{{1, 0, 0}, {0, 1, 0}},
		{{0, 1, 0}, {1, 0, 0}}
	};
	// 按 面 * 4 + 棱 索引预计算：棱起点相对方块最小角的偏移、轴向，以及面内另一侧方块的偏移
	private static final int[] EDGE_ORIGIN_X = new int[FACE_COUNT * EDGES_PER_FACE];
	private static final int[] EDGE_ORIGIN_Y = new int[FACE_COUNT * EDGES_PER_FACE];
	private static final int[] EDGE_ORIGIN_Z = new int[FACE_COUNT * EDGES_PER_FACE];
	private static final byte[] EDGE_AXIS = new byte[FACE_COUNT * EDGES_PER_FACE];
	private static final int[] EDGE_OTHER_X = new int[FACE_COUNT * EDGES_PER_FACE];
	private static final int[] EDGE_OTHER_Y = new int[FACE_COUNT * EDGES_PER_FACE];
	private static final int[] EDGE_OTHER_Z = new int[FACE_COUNT * EDGES_PER_FACE];
	// 每个面的 4 个角相对方块最小角的偏移，按 面 * 4 + 角 索引；供渲染填充时取顶点
	static final int[] FACE_CORNER_X = new int[FACE_COUNT * CORNERS_PER_FACE];
	static final int[] FACE_CORNER_Y = new int[FACE_COUNT * CORNERS_PER_FACE];
	static final int[] FACE_CORNER_Z = new int[FACE_COUNT * CORNERS_PER_FACE];

	static {
		// 面的 4 个角：(u,v) = (0,0)、(1,0)、(1,1)、(0,1)
		int[][] cornerUv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

		for (int face = 0; face < FACE_COUNT; face++) {
			int[] normal = FACE_NORMALS[face];
			int[] u = FACE_AXES[face][0];
			int[] v = FACE_AXES[face][1];
			// 正面位于方块坐标 +1 一侧，负面位于方块坐标本身
			int offsetX = Math.max(normal[0], 0);
			int offsetY = Math.max(normal[1], 0);
			int offsetZ = Math.max(normal[2], 0);
			int[][] corners = new int[4][3];

			for (int corner = 0; corner < 4; corner++) {
				corners[corner][0] = offsetX + u[0] * cornerUv[corner][0] + v[0] * cornerUv[corner][1];
				corners[corner][1] = offsetY + u[1] * cornerUv[corner][0] + v[1] * cornerUv[corner][1];
				corners[corner][2] = offsetZ + u[2] * cornerUv[corner][0] + v[2] * cornerUv[corner][1];

				int cornerIndex = face * CORNERS_PER_FACE + corner;
				FACE_CORNER_X[cornerIndex] = corners[corner][0];
				FACE_CORNER_Y[cornerIndex] = corners[corner][1];
				FACE_CORNER_Z[cornerIndex] = corners[corner][2];
			}

			// 棱 0..3 分别是面的 v=0、u=1、v=1、u=0 边界，面内另一侧方向为 -v、+u、+v、-u
			int[][] otherOffsets = {
				{-v[0], -v[1], -v[2]},
				{u[0], u[1], u[2]},
				{v[0], v[1], v[2]},
				{-u[0], -u[1], -u[2]}
			};

			for (int edge = 0; edge < EDGES_PER_FACE; edge++) {
				int index = face * EDGES_PER_FACE + edge;
				int[] start = corners[edge];
				int[] end = corners[(edge + 1) % EDGES_PER_FACE];
				int axis = start[0] != end[0] ? 0 : (start[1] != end[1] ? 1 : 2);

				// 统一取坐标较小的一端作为起点，保证不同面发现同一条棱时 key 一致
				EDGE_ORIGIN_X[index] = Math.min(start[0], end[0]);
				EDGE_ORIGIN_Y[index] = Math.min(start[1], end[1]);
				EDGE_ORIGIN_Z[index] = Math.min(start[2], end[2]);
				EDGE_AXIS[index] = (byte) axis;
				EDGE_OTHER_X[index] = otherOffsets[edge][0];
				EDGE_OTHER_Y[index] = otherOffsets[edge][1];
				EDGE_OTHER_Z[index] = otherOffsets[edge][2];
			}
		}
	}

	private final LongOpenHashSet occupied = new LongOpenHashSet();
	private final Long2ByteOpenHashMap visitedEdges = new Long2ByteOpenHashMap();
	private final ArrayList<BlockPos> uniqueBlocks = new ArrayList<>();
	private int[] edges = new int[INTS_PER_EDGE * 16];
	private int edgeCount;
	private int[] faces = new int[INTS_PER_FACE * 16];
	private int faceCount;

	public BlockHighlightGeometry() {
	}

	// 按当前方块集合重建暴露面和硬边；调用方应在数据变化时调用，而不是每帧调用
	public void rebuild(Collection<BlockPos> positions) {
		this.edgeCount = 0;
		this.faceCount = 0;
		this.occupied.clear();
		this.visitedEdges.clear();
		this.uniqueBlocks.clear();

		// 先建立占用集合，同时去掉重复坐标，避免重复输出面或重复判定棱
		for (BlockPos position : positions) {
			if (this.occupied.add(pack(position.getX(), position.getY(), position.getZ()))) {
				this.uniqueBlocks.add(position);
			}
		}

		for (int blockIndex = 0; blockIndex < this.uniqueBlocks.size(); blockIndex++) {
			BlockPos position = this.uniqueBlocks.get(blockIndex);
			int blockX = position.getX();
			int blockY = position.getY();
			int blockZ = position.getZ();

			for (int face = 0; face < FACE_COUNT; face++) {
				int normalX = FACE_NORMALS[face][0];
				int normalY = FACE_NORMALS[face][1];
				int normalZ = FACE_NORMALS[face][2];

				// 内部面不产生候选棱，先跳过
				if (this.occupied.contains(pack(blockX + normalX, blockY + normalY, blockZ + normalZ))) {
					continue;
				}

				// 暴露面同时是体素并集的外表面，填充只画这些面
				this.addFace(blockX, blockY, blockZ, face);

				for (int edge = 0; edge < EDGES_PER_FACE; edge++) {
					int tableIndex = face * EDGES_PER_FACE + edge;
					int originX = blockX + EDGE_ORIGIN_X[tableIndex];
					int originY = blockY + EDGE_ORIGIN_Y[tableIndex];
					int originZ = blockZ + EDGE_ORIGIN_Z[tableIndex];
					int axis = EDGE_AXIS[tableIndex];
					long edgeKey = pack(originX, originY, originZ);
					int edgeBit = 1 << axis;
					int visitedMask = this.visitedEdges.get(edgeKey);

					// 同一条硬边会被 2 或 4 个暴露面看到，先按 起点 + 轴向 去重
					if ((visitedMask & edgeBit) != 0) {
						continue;
					}
					this.visitedEdges.put(edgeKey, (byte) (visitedMask | edgeBit));

					int otherX = blockX + EDGE_OTHER_X[tableIndex];
					int otherY = blockY + EDGE_OTHER_Y[tableIndex];
					int otherZ = blockZ + EDGE_OTHER_Z[tableIndex];

					// 面内另一侧有方块、且它更外侧为空时，这条棱只是平坦表面的延续
					if (this.occupied.contains(pack(otherX, otherY, otherZ))
						&& !this.occupied.contains(pack(otherX + normalX, otherY + normalY, otherZ + normalZ))) {
						continue;
					}

					this.addEdge(originX, originY, originZ, axis);
				}
			}
		}
	}

	public void clear() {
		this.edgeCount = 0;
		this.faceCount = 0;
		this.occupied.clear();
		this.visitedEdges.clear();
		this.uniqueBlocks.clear();
	}

	public boolean isEmpty() {
		return this.edgeCount == 0 && this.faceCount == 0;
	}

	int[] edges() {
		return this.edges;
	}

	int edgeCount() {
		return this.edgeCount;
	}

	int[] faces() {
		return this.faces;
	}

	int faceCount() {
		return this.faceCount;
	}

	private void addEdge(int x, int y, int z, int axis) {
		int requiredLength = (this.edgeCount + 1) * INTS_PER_EDGE;

		if (requiredLength > this.edges.length) {
			int newLength = Math.max(requiredLength, this.edges.length + (this.edges.length >> 1) + INTS_PER_EDGE);
			this.edges = Arrays.copyOf(this.edges, newLength);
		}

		int index = this.edgeCount * INTS_PER_EDGE;
		this.edges[index] = x;
		this.edges[index + 1] = y;
		this.edges[index + 2] = z;
		this.edges[index + 3] = axis;
		this.edgeCount++;
	}

	private void addFace(int x, int y, int z, int face) {
		int requiredLength = (this.faceCount + 1) * INTS_PER_FACE;

		if (requiredLength > this.faces.length) {
			int newLength = Math.max(requiredLength, this.faces.length + (this.faces.length >> 1) + INTS_PER_FACE);
			this.faces = Arrays.copyOf(this.faces, newLength);
		}

		int index = this.faceCount * INTS_PER_FACE;
		this.faces[index] = x;
		this.faces[index + 1] = y;
		this.faces[index + 2] = z;
		this.faces[index + 3] = face;
		this.faceCount++;
	}

	// 坐标打包成 long；x/z 支持 ±3300 万，y 支持 -2048..2047，覆盖原版世界范围
	private static long pack(int x, int y, int z) {
		return ((long) x & 0x3FFFFFFL) << 38
			| ((long) z & 0x3FFFFFFL) << 12
			| ((long) y & 0xFFFL);
	}
}
