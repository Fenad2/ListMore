package com.listmore.schematic;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import net.minecraft.core.Vec3i;

// 单个原理图浏览器的预览状态
public final class SchematicPreviewSession {
	private final AtomicLong generation = new AtomicLong();
	private final SchematicPreviewTransform transform = new SchematicPreviewTransform();
	private final SchematicPreviewRenderManager renderer = new SchematicPreviewRenderManager();
	private Path file;
	private CompletableFuture<LitematicaSchematic> loadingTask;
	private SchematicPreviewModel model;
	private Throwable loadFailure;
	// 后台加载线程写入，GUI绘制线程读取
	private volatile LoadResult pendingResult;
	private boolean closed;

	public SchematicPreviewTransform transform() {
		return this.transform;
	}

	// 返回当前浏览器专属的预览渲染器
	public SchematicPreviewRenderManager renderer() {
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

	// 仅在列表中选中新的 litematic 文件时启动新的加载任务
	// 流程：路径规范化 -> 去重检查 -> 重置旧状态 -> 启动异步加载 -> 结果写入 pendingResult
	// generation 防止旧请求的结果覆盖新请求
	public void setFile(Path file) {
		Path normalizedFile = file.toAbsolutePath().normalize();
		if (this.closed || normalizedFile.equals(this.file)) {
			return;
		}

		// 重置所有状态，准备新文件加载
		this.file = normalizedFile;
		this.model = null;
		this.renderer.close();
		this.loadFailure = null;
		this.pendingResult = null;
		this.transform.reset();
		// 递增 generation 使旧请求的回调失效
		long requestGeneration = this.generation.incrementAndGet();
		CompletableFuture<LitematicaSchematic> task = SchematicPreviewLoader.load(normalizedFile);
		this.loadingTask = task;
		task.whenComplete((loaded, throwable) -> {
			// 检查：未关闭 + generation 匹配 + 未被新任务替换
			if (this.closed || requestGeneration != this.generation.get() || task != this.loadingTask) {
				return;
			}
			this.pendingResult = new LoadResult(requestGeneration, loaded, throwable);
		});
	}

	// 在 GUI 绘制线程调用，消费后台加载线程写入的 pendingResult
	// 检查 generation 确保只处理最新请求的结果，忽略已过期的加载任务
	// 加载成功后从 LitematicaSchematic 提取 SchematicPreviewModel 并提交给渲染器
	public void update() {
		LoadResult result = this.pendingResult;
		if (result == null || result.generation() != this.generation.get()) {
			return;
		}

		this.pendingResult = null;
		if (result.throwable() != null) {
			this.loadFailure = result.throwable();
			return;
		}
		if (result.schematic() == null) {
			this.loadFailure = new IllegalStateException("Litematica returned no schematic");
			return;
		}

		try {
			// 加载成功 -> 提取模型并提交给渲染器
			this.model = SchematicPreviewModel.from(result.schematic());
			this.renderer.setSchematic(result.schematic());
			this.renderer.setModel(this.model);
		} catch (Throwable throwable) {
			this.loadFailure = throwable;
		}
	}

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
