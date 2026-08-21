package com.listmore.compat.litematica.schematic.preview;

import com.listmore.compat.litematica.schematic.preview.model.SchematicPreviewModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import com.listmore.compat.litematica.schematic.preview.SchematicPreviewLoader.LoadRequest;
import com.listmore.compat.litematica.schematic.preview.render.SchematicPreviewRenderManager;

// 单个原理图浏览器的预览状态
public final class SchematicPreviewSession {
	private final AtomicLong generation = new AtomicLong();
	private final SchematicPreviewTransform transform = new SchematicPreviewTransform();
	private final SchematicPreviewRenderManager renderer = new SchematicPreviewRenderManager();
	private Path file;
	private FileStamp fileStamp;
	private long nextFileCheckNanos;
	private LoadRequest loadRequest;
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

	public boolean isLoading() {
		return this.loadRequest != null && !this.loadRequest.future().isDone();
	}

	public boolean hasFailure() {
		return this.loadFailure != null;
	}

	// 仅在文件路径或文件版本变化时启动新的加载任务
	// generation 防止旧请求的结果覆盖新请求
	public void setFile(Path file) {
		Path normalizedFile = file.toAbsolutePath().normalize();
		if (this.closed) {
			return;
		}
		long now = System.nanoTime();
		if (normalizedFile.equals(this.file) && now < this.nextFileCheckNanos) {
			return;
		}
		FileStamp stamp = FileStamp.read(normalizedFile);
		this.nextFileCheckNanos = now + 1_000_000_000L;
		if (normalizedFile.equals(this.file) && Objects.equals(stamp, this.fileStamp)) {
			return;
		}

		// 重置所有状态，准备新文件加载
		if (this.loadRequest != null) {
			this.loadRequest.cancel();
		}
		this.file = normalizedFile;
		this.fileStamp = stamp;
		this.model = null;
		this.renderer.clearModel();
		this.loadFailure = null;
		this.pendingResult = null;
		this.transform.reset();
		// 递增 generation 使旧请求的回调失效
		long requestGeneration = this.generation.incrementAndGet();
		LoadRequest request = SchematicPreviewLoader.load(normalizedFile);
		this.loadRequest = request;
		request.future().whenComplete((loaded, throwable) -> {
			// 检查：未关闭 + generation 匹配 + 未被新任务替换
			if (this.closed || requestGeneration != this.generation.get() || request != this.loadRequest) {
				return;
			}
			this.pendingResult = new LoadResult(requestGeneration, loaded, throwable);
		});
	}

	// 预览区域暂时不可见时释放模型和网格，但保留可复用的渲染后端资源
	public void clear() {
		if (this.file == null && this.model == null && this.loadRequest == null) {
			return;
		}
		this.generation.incrementAndGet();
		if (this.loadRequest != null) {
			this.loadRequest.cancel();
		}
		this.file = null;
		this.fileStamp = null;
		this.nextFileCheckNanos = 0L;
		this.loadRequest = null;
		this.pendingResult = null;
		this.model = null;
		this.loadFailure = null;
		this.renderer.clearModel();
	}

	// 在 GUI 绘制线程调用，消费后台加载线程写入的 pendingResult
	// 检查 generation 确保只处理最新请求的结果，忽略已过期的加载任务
	// 加载成功后将后台生成的模型提交给渲染器
	public void update() {
		LoadResult result = this.pendingResult;
		if (result == null || result.generation() != this.generation.get()) {
			return;
		}

		this.pendingResult = null;
		if (this.loadRequest != null) {
			this.loadRequest.cancel();
			this.loadRequest = null;
		}
		if (result.throwable() != null) {
			this.loadFailure = result.throwable();
			return;
		}
		if (result.model() == null) {
			this.loadFailure = new IllegalStateException("Litematica returned no preview");
			return;
		}

		try {
			// 加载成功 -> 提交模型给渲染器
			this.model = result.model();
			this.renderer.modelChanged();
		} catch (Throwable throwable) {
			this.loadFailure = throwable;
		}
	}

	// clear() 保留渲染后端以便复用；close() 同时销毁后端持有的 GPU 资源
	public void close() {
		this.closed = true;
		this.generation.incrementAndGet();
		if (this.loadRequest != null) {
			this.loadRequest.cancel();
		}
		this.loadRequest = null;
		this.pendingResult = null;
		this.model = null;
		this.renderer.close();
	}

	private record LoadResult(long generation, SchematicPreviewModel model, Throwable throwable) {
	}

	private record FileStamp(long modifiedTime, long size) {
		private static FileStamp read(Path file) {
			try {
				return new FileStamp(Files.getLastModifiedTime(file).toMillis(), Files.size(file));
			} catch (IOException ignored) {
				return null;
			}
		}
	}
}
