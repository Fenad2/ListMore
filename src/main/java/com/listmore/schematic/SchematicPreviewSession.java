package com.listmore.schematic;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import com.listmore.ListMore;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import net.minecraft.core.Vec3i;

/**
 * 单个原理图浏览器的预览生命周期。
 * 异步加载完成后会检查 generation，避免之前选择的文件覆盖当前预览。
 */
public final class SchematicPreviewSession {
	private final AtomicLong generation = new AtomicLong();
	private final SchematicPreviewTransform transform = new SchematicPreviewTransform();
	private final SchematicPreviewRenderer renderer = new SchematicPreviewRenderer();
	private Path file;
	private CompletableFuture<LitematicaSchematic> loadingTask;
	private SchematicPreviewModel model;
	private Throwable loadFailure;
	/** 后台加载线程写入，GUI 绘制线程读取。 */
	private volatile LoadResult pendingResult;
	private boolean closed;

	public SchematicPreviewTransform transform() {
		return this.transform;
	}

	/** 返回当前浏览器专属的预览渲染器。 */
	public SchematicPreviewRenderer renderer() {
		return this.renderer;
	}

	public SchematicPreviewModel model() {
		return this.model;
	}

	public Vec3i size() {
		return this.model != null ? this.model.size() : null;
	}

	public boolean isLoading() {
		return this.loadingTask != null && !this.loadingTask.isDone();
	}

	public boolean hasFailure() {
		return this.loadFailure != null;
	}

	/** 仅在用户在原理图列表中选中新的 litematic 文件时启动新的加载任务。 */
	public void setFile(Path file) {
		Path normalizedFile = file.toAbsolutePath().normalize();
		if (this.closed || normalizedFile.equals(this.file)) {
			return;
		}

		this.file = normalizedFile;
		ListMore.LOGGER.info("Schematic preview: selected {}", normalizedFile);
		this.model = null;
		this.renderer.close();
		this.loadFailure = null;
		this.pendingResult = null;
		this.transform.reset();
		long requestGeneration = this.generation.incrementAndGet();
		CompletableFuture<LitematicaSchematic> task = SchematicPreviewLoader.load(normalizedFile);
		this.loadingTask = task;
		task.whenComplete((loaded, throwable) -> {
			if (this.closed || requestGeneration != this.generation.get() || task != this.loadingTask) {
				return;
			}
			this.pendingResult = new LoadResult(requestGeneration, loaded, throwable);
		});
	}

	/** 在 GUI 绘制线程应用后台加载结果，避免后台线程修改渲染状态。 */
	public void update() {
		LoadResult result = this.pendingResult;
		if (result == null || result.generation() != this.generation.get()) {
			return;
		}

		this.pendingResult = null;
		if (result.throwable() != null) {
			ListMore.LOGGER.error("Schematic preview: load result failed for {}", this.file, result.throwable());
			this.loadFailure = result.throwable();
			return;
		}
		if (result.schematic() == null) {
			this.loadFailure = new IllegalStateException("Litematica returned no schematic");
			return;
		}

		try {
			this.model = SchematicPreviewModel.from(result.schematic());
			ListMore.LOGGER.info("Schematic preview: model ready for {} (size={}x{}x{}, nonAirBlocks={})", this.file,
					this.model.size().getX(), this.model.size().getY(), this.model.size().getZ(), this.model.blocks().size());
			this.renderer.setSchematic(result.schematic());
			this.renderer.setModel(this.model);
		} catch (Throwable throwable) {
			ListMore.LOGGER.error("Schematic preview: model creation failed for {}", this.file, throwable);
			this.loadFailure = throwable;
		}
	}

	/** 关闭浏览器时取消仍在等待的任务，并使所有回调失效。 */
	public void close() {
		this.closed = true;
		this.generation.incrementAndGet();
		this.loadingTask = null;
		this.pendingResult = null;
		this.model = null;
		this.renderer.close();
	}

	private record LoadResult(long generation, LitematicaSchematic schematic, Throwable throwable) {
	}
}
