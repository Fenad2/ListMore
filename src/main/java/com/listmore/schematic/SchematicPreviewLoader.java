package com.listmore.schematic;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.listmore.ListMore;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;

/** 负责在后台读取 litematic 文件，不持有也不修改 Litematica 的全局投影状态。 */
public final class SchematicPreviewLoader {
	private static final ConcurrentMap<Path, CompletableFuture<LitematicaSchematic>> CACHE = new ConcurrentHashMap<>();

	private SchematicPreviewLoader() {
	}

	public static CompletableFuture<LitematicaSchematic> load(Path file) {
		Path normalizedFile = file.toAbsolutePath().normalize();
		CompletableFuture<LitematicaSchematic> task = CACHE.computeIfAbsent(normalizedFile, SchematicPreviewLoader::startLoad);
		task.whenComplete((schematic, throwable) -> {
			if (throwable != null || schematic == null) {
				CACHE.remove(normalizedFile, task);
			}
		});
		return task;
	}

	private static CompletableFuture<LitematicaSchematic> startLoad(Path file) {
		return CompletableFuture.supplyAsync(() -> {
			ListMore.LOGGER.info("Schematic preview: loading {}", file);
			try {
				LitematicaSchematic schematic = LitematicaSchematic.createFromFile(file.getParent(), file.getFileName().toString());
				ListMore.LOGGER.info("Schematic preview: loaded {} (regions={})", file, schematic != null ? schematic.getAreas().size() : 0);
				return schematic;
			} catch (Throwable throwable) {
				ListMore.LOGGER.error("Schematic preview: failed to load {}", file, throwable);
				throw throwable;
			}
		});
	}
}
