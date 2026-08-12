package com.listmore.schematic.preview;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;

// 后台读取 litematic 文件并提取预览模型
public final class SchematicPreviewLoader {
	//TODO:目前来看4已经够用了，但真的够吗？
	private static final int MAX_CACHE_ENTRIES = 4;
	private static final ConcurrentMap<Path, CacheEntry> CACHE = new ConcurrentHashMap<>();
	private static final ConcurrentLinkedQueue<CacheReference> CACHE_ORDER = new ConcurrentLinkedQueue<>();

	private SchematicPreviewLoader() {
	}

	public static CompletableFuture<LoadedPreview> load(Path file) {
		Path normalizedFile = file.toAbsolutePath().normalize();
		FileStamp stamp;
		try {
			stamp = new FileStamp(Files.getLastModifiedTime(normalizedFile).toMillis(), Files.size(normalizedFile));
		} catch (IOException exception) {
			return CompletableFuture.failedFuture(exception);
		}

		CacheEntry entry = CACHE.compute(normalizedFile, (path, cached) -> {
			if (cached != null && cached.stamp().equals(stamp)) {
				return cached;
			}
			CacheEntry created = new CacheEntry(stamp, startLoad(path));
			CACHE_ORDER.add(new CacheReference(path, created));
			return created;
		});
		trimCache();
		entry.task().whenComplete((preview, throwable) -> {
			if (throwable != null || preview == null) {
				CACHE.remove(normalizedFile, entry);
			}
		});
		return entry.task();
	}

	private static CompletableFuture<LoadedPreview> startLoad(Path file) {
		return CompletableFuture.supplyAsync(() -> {
			LitematicaSchematic schematic = LitematicaSchematic.createFromFile(file.getParent(), file.getFileName().toString());
			if (schematic == null) {
				throw new IllegalStateException("Litematica returned no schematic");
			}
			return new LoadedPreview(schematic, SchematicPreviewModel.from(schematic));
		});
	}

	private static void trimCache() {
		while (CACHE.size() > MAX_CACHE_ENTRIES) {
			CacheReference oldest = CACHE_ORDER.poll();
			if (oldest == null) {
				return;
			}
			CACHE.remove(oldest.file(), oldest.entry());
		}
	}

	public record LoadedPreview(LitematicaSchematic schematic, SchematicPreviewModel model) {
	}

	private record FileStamp(long modifiedTime, long size) {
	}

	private record CacheEntry(FileStamp stamp, CompletableFuture<LoadedPreview> task) {
	}

	private record CacheReference(Path file, CacheEntry entry) {
	}
}
