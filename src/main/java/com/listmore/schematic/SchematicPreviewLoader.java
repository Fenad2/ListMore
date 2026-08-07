package com.listmore.schematic;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;

// 后台读取 litematic 文件
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
		return CompletableFuture.supplyAsync(() ->
				LitematicaSchematic.createFromFile(file.getParent(), file.getFileName().toString()));
	}
}
