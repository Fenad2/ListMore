package com.listmore.schematic.preview.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.ILitematicaBlockStatePalette;
import fi.dy.masa.litematica.schematic.container.LitematicaBitArray;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.selection.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;

// 将 Litematica 数据按 16^3 Section 扫描并合并为预览模型。
public final class SchematicPreviewScanner {
	private static final int SECTION_SIZE = 16;
	private static final int SECTION_VOLUME = SECTION_SIZE * SECTION_SIZE * SECTION_SIZE;
	private static final int TASKS_PER_WORKER = 2;
	private static final int MIN_PARALLEL_SECTIONS = 8;
	private static final long SERIAL_SCAN_VOLUME_THRESHOLD = (long) SECTION_VOLUME * MIN_PARALLEL_SECTIONS;

	private SchematicPreviewScanner() {
	}

	public static SchematicPreviewModel scan(LitematicaSchematic schematic, ExecutorService executor,
			int workerCount, BooleanSupplier cancelled) {
		List<RegionSource> regions = collectRegions(schematic);
		if (regions.isEmpty()) {
			return SchematicPreviewModel.empty();
		}

		Bounds bounds = findBounds(regions);
		Map<Long, SectionData> mergedSections = new LinkedHashMap<>();
		for (RegionSource region : regions) {
			checkCancelled(cancelled);
			scanRegion(region.relativeTo(bounds.minX(), bounds.minY(), bounds.minZ()), executor,
					Math.max(1, workerCount), cancelled, mergedSections);
		}

		Map<Long, SchematicPreviewModel.Section> sections = new LinkedHashMap<>(capacityFor(mergedSections.size()));
		mergedSections.forEach((position, data) -> sections.put(position,
				new SchematicPreviewModel.Section(data.sectionX(), data.sectionY(), data.sectionZ(), data.states())));
		Vec3i size = new Vec3i(bounds.maxXExclusive() - bounds.minX(),
				bounds.maxYExclusive() - bounds.minY(), bounds.maxZExclusive() - bounds.minZ());
		return new SchematicPreviewModel(size, new SchematicPreviewSectionStorage(sections));
	}

	private static List<RegionSource> collectRegions(LitematicaSchematic schematic) {
		List<RegionSource> regions = new ArrayList<>();
		for (Map.Entry<String, Box> entry : schematic.getAreas().entrySet()) {
			LitematicaBlockStateContainer container = schematic.getSubRegionContainer(entry.getKey());
			BlockPos first = entry.getValue().getPos1();
			BlockPos second = entry.getValue().getPos2();
			if (container == null || first == null || second == null) {
				continue;
			}
			Vec3i size = container.getSize();
			regions.add(new RegionSource(
					Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()),
					Math.min(first.getZ(), second.getZ()), size.getX(), size.getY(), size.getZ(),
					container.getArray(), readPalette(container.getPalette())));
		}
		return regions;
	}

	private static Bounds findBounds(List<RegionSource> regions) {
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxXExclusive = Integer.MIN_VALUE;
		int maxYExclusive = Integer.MIN_VALUE;
		int maxZExclusive = Integer.MIN_VALUE;
		for (RegionSource region : regions) {
			minX = Math.min(minX, region.originX());
			minY = Math.min(minY, region.originY());
			minZ = Math.min(minZ, region.originZ());
			maxXExclusive = Math.max(maxXExclusive, region.originX() + region.sizeX());
			maxYExclusive = Math.max(maxYExclusive, region.originY() + region.sizeY());
			maxZExclusive = Math.max(maxZExclusive, region.originZ() + region.sizeZ());
		}
		return new Bounds(minX, minY, minZ, maxXExclusive, maxYExclusive, maxZExclusive);
	}

	private static BlockState[] readPalette(ILitematicaBlockStatePalette palette) {
		BlockState[] states = new BlockState[palette.getPaletteSize()];
		for (int id = 0; id < states.length; id++) {
			BlockState state = palette.getBlockState(id);
			states[id] = state == null || state.isAir() ? null : state;
		}
		return states;
	}

	private static void scanRegion(RegionSource region, ExecutorService executor, int workerCount,
			BooleanSupplier cancelled, Map<Long, SectionData> mergedSections) {
		SectionCursor cursor = new SectionCursor(region);
		if (workerCount <= 1 || cursor.sectionCount() < MIN_PARALLEL_SECTIONS
				|| region.volume() <= SERIAL_SCAN_VOLUME_THRESHOLD) {
			scanRegionSerial(region, cursor, cancelled, mergedSections);
			return;
		}

		// 只维持有限数量的在途任务
		// 完成顺序无需稳定，合并会按 Section 坐标处理重叠区域
		ExecutorCompletionService<SectionData> completion = new ExecutorCompletionService<>(executor);
		List<Future<SectionData>> submitted = new ArrayList<>();
		int taskLimit = Math.max(1, workerCount * TASKS_PER_WORKER);
		int inFlight = 0;
		try {
			while (cursor.hasNext() && inFlight < taskLimit) {
				submitted.add(submitSection(completion, region, cursor.next(), cancelled));
				inFlight++;
			}
			while (inFlight > 0) {
				checkCancelled(cancelled);
				mergeSection(completion.take().get(), mergedSections);
				inFlight--;
				if (cursor.hasNext()) {
					submitted.add(submitSection(completion, region, cursor.next(), cancelled));
					inFlight++;
				}
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new CancellationException("Schematic preview scan interrupted");
		} catch (ExecutionException exception) {
			throw propagate(exception.getCause());
		} finally {
			submitted.forEach(future -> future.cancel(true));
		}
	}

	private static Future<SectionData> submitSection(ExecutorCompletionService<SectionData> completion,
			RegionSource region, SectionCoordinates coordinates, BooleanSupplier cancelled) {
		return completion.submit(() -> scanSection(region, coordinates.x(), coordinates.y(), coordinates.z(), cancelled));
	}

	private static void scanRegionSerial(RegionSource region, SectionCursor cursor, BooleanSupplier cancelled,
			Map<Long, SectionData> mergedSections) {
		while (cursor.hasNext()) {
			checkCancelled(cancelled);
			SectionCoordinates coordinates = cursor.next();
			mergeSection(scanSection(region, coordinates.x(), coordinates.y(), coordinates.z(), cancelled),
					mergedSections);
		}
	}

	private static void mergeSection(SectionData scanned, Map<Long, SectionData> mergedSections) {
		if (scanned.states() == null) {
			return;
		}
		long key = SchematicPreviewSectionStorage.packPosition(
				scanned.sectionX(), scanned.sectionY(), scanned.sectionZ());
		SectionData target = mergedSections.get(key);
		if (target == null) {
			mergedSections.put(key, scanned);
			return;
		}
		for (int index = 0; index < SECTION_VOLUME; index++) {
			if (target.states()[index] == null && scanned.states()[index] != null) {
				target.states()[index] = scanned.states()[index];
			}
		}
	}

	private static SectionData scanSection(RegionSource region, int sectionX, int sectionY, int sectionZ,
			BooleanSupplier cancelled) {
		int sectionMinX = sectionX << 4;
		int sectionMinY = sectionY << 4;
		int sectionMinZ = sectionZ << 4;
		int minX = Math.max(sectionMinX, region.originX());
		int minY = Math.max(sectionMinY, region.originY());
		int minZ = Math.max(sectionMinZ, region.originZ());
		int maxX = Math.min(sectionMinX + SECTION_SIZE, region.originX() + region.sizeX());
		int maxY = Math.min(sectionMinY + SECTION_SIZE, region.originY() + region.sizeY());
		int maxZ = Math.min(sectionMinZ + SECTION_SIZE, region.originZ() + region.sizeZ());
		BlockState[] states = null;
		int checked = 0;
		int sizeLayer = region.sizeX() * region.sizeZ();

		for (int y = minY; y < maxY; y++) {
			int localY = y - region.originY();
			for (int z = minZ; z < maxZ; z++) {
				int localZ = z - region.originZ();
				long storageIndex = (long) localY * sizeLayer + (long) localZ * region.sizeX()
						+ minX - region.originX();
				for (int x = minX; x < maxX; x++, storageIndex++) {
					if ((checked++ & 255) == 0) {
						checkCancelled(cancelled);
					}
					int paletteId = region.storage().getAt(storageIndex);
					if (paletteId < 0 || paletteId >= region.palette().length) {
						continue;
					}
					BlockState state = region.palette()[paletteId];
					if (state == null) {
						continue;
					}
					if (states == null) {
						states = new BlockState[SECTION_VOLUME];
					}
					// Section 内固定为 16 x 16 x 16，索引布局由 Model 和 Renderer 共同使用。
					states[SchematicPreviewModel.localIndex(x & 15, y & 15, z & 15)] = state;
				}
			}
		}
		return new SectionData(sectionX, sectionY, sectionZ, states);
	}

	private static RuntimeException propagate(Throwable cause) {
		if (cause instanceof CancellationException cancellation) {
			return cancellation;
		}
		if (cause instanceof RuntimeException runtime) {
			return runtime;
		}
		return new IllegalStateException("Failed to scan schematic preview section", cause);
	}

	private static void checkCancelled(BooleanSupplier cancelled) {
		if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) {
			throw new CancellationException("Schematic preview load cancelled");
		}
	}

	private static int capacityFor(int size) {
		return Math.max(16, (int) Math.ceil(size / 0.75D));
	}

	private static final class SectionCursor {
		private final int minSectionX;
		private final int minSectionZ;
		private final int maxSectionX;
		private final int maxSectionY;
		private final int maxSectionZ;
		private int sectionX;
		private int sectionY;
		private int sectionZ;

		private SectionCursor(RegionSource region) {
			this.minSectionX = region.originX() >> 4;
			this.minSectionZ = region.originZ() >> 4;
			this.maxSectionX = (region.originX() + region.sizeX() - 1) >> 4;
			this.maxSectionY = (region.originY() + region.sizeY() - 1) >> 4;
			this.maxSectionZ = (region.originZ() + region.sizeZ() - 1) >> 4;
			this.sectionX = this.minSectionX;
			this.sectionY = region.originY() >> 4;
			this.sectionZ = this.minSectionZ;
		}

		private boolean hasNext() {
			return this.sectionY <= this.maxSectionY;
		}

		private long sectionCount() {
			return (long) (this.maxSectionX - this.minSectionX + 1)
					* (this.maxSectionY - this.sectionY + 1)
					* (this.maxSectionZ - this.minSectionZ + 1);
		}

		private SectionCoordinates next() {
			SectionCoordinates coordinates = new SectionCoordinates(this.sectionX, this.sectionY, this.sectionZ);
			if (++this.sectionX > this.maxSectionX) {
				this.sectionX = this.minSectionX;
				if (++this.sectionZ > this.maxSectionZ) {
					this.sectionZ = this.minSectionZ;
					this.sectionY++;
				}
			}
			return coordinates;
		}
	}

	private record Bounds(int minX, int minY, int minZ, int maxXExclusive, int maxYExclusive,
			int maxZExclusive) {
	}

	private record RegionSource(int originX, int originY, int originZ, int sizeX, int sizeY, int sizeZ,
			LitematicaBitArray storage, BlockState[] palette) {
		private long volume() {
			return (long) this.sizeX * this.sizeY * this.sizeZ;
		}

		private RegionSource relativeTo(int minX, int minY, int minZ) {
			return new RegionSource(this.originX - minX, this.originY - minY, this.originZ - minZ,
					this.sizeX, this.sizeY, this.sizeZ, this.storage, this.palette);
		}
	}

	private record SectionData(int sectionX, int sectionY, int sectionZ, BlockState[] states) {
	}

	private record SectionCoordinates(int x, int y, int z) {
	}
}
