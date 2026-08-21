package com.listmore.compat.litematica.schematic.preview;

import com.listmore.compat.litematica.schematic.preview.model.SchematicPreviewModel;
import com.listmore.compat.litematica.schematic.preview.model.SchematicPreviewScanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;

// 后台读取 litematic 文件并提取预览模型
// 文件解析和 Section 扫描使用不同线程池
public final class SchematicPreviewLoader {
	//TODO:目前来看4已经够用了，但真的够吗？ --> 6
	private static final int MAX_CACHE_ENTRIES = 4;
	private static final int SCAN_WORKERS = Math.max(1,
			Math.min(6, Runtime.getRuntime().availableProcessors() - 2));
	private static final ExecutorService FILE_EXECUTOR = Executors.newFixedThreadPool(2,
			threadFactory("ListMore preview file"));
	private static final ExecutorService SCAN_EXECUTOR = Executors.newFixedThreadPool(
			SCAN_WORKERS, threadFactory("ListMore preview scan"));
	// 缓存项由多个 Session 共享
	private static final ConcurrentMap<Path, CacheEntry> CACHE = new ConcurrentHashMap<>();
	private static final ConcurrentLinkedQueue<CacheReference> CACHE_ORDER = new ConcurrentLinkedQueue<>();

	private SchematicPreviewLoader() {
	}

	public static LoadRequest load(Path file) {
		Path normalizedFile = file.toAbsolutePath().normalize();
		FileStamp stamp;
		try {
			stamp = new FileStamp(Files.getLastModifiedTime(normalizedFile).toMillis(), Files.size(normalizedFile));
		} catch (IOException exception) {
			return new LoadRequest(CompletableFuture.failedFuture(exception), () -> {});
		}

		CacheEntry entry = CACHE.compute(normalizedFile, (path, cached) -> {
			if (cached != null && cached.stamp.equals(stamp) && !cached.load.future().isCompletedExceptionally()) {
				return cached;
			}
			if (cached != null) {
				CACHE_ORDER.remove(new CacheReference(path, cached));
				cached.cancelIfUnused();
			}
			CacheEntry created = new CacheEntry(stamp, startLoad(path));
			CACHE_ORDER.add(new CacheReference(path, created));
			return created;
		});
		LoadRequest request = entry.acquire(normalizedFile);
		trimCache();
		entry.load.future().whenComplete((model, throwable) -> {
			if (throwable != null || model == null) {
				removeEntry(normalizedFile, entry);
			}
		});
		return request;
	}

	private static SharedLoad startLoad(Path file) {
		AtomicBoolean cancelled = new AtomicBoolean();
		CompletableFuture<SchematicPreviewModel> result = new CompletableFuture<>();
		Future<?> worker = FILE_EXECUTOR.submit(() -> {
			try {
				LitematicaSchematic schematic = LitematicaSchematic.createFromFile(
						file.getParent(), file.getFileName().toString());
				if (schematic == null) {
					throw new IllegalStateException("Litematica returned no schematic");
				}
				if (cancelled.get()) {
					throw new CancellationException("Schematic preview load cancelled");
				}
				SchematicPreviewModel model = SchematicPreviewScanner.scan(
						schematic, SCAN_EXECUTOR, SCAN_WORKERS, cancelled::get);
				result.complete(model);
			} catch (Throwable throwable) {
				result.completeExceptionally(throwable);
			}
		});
		return new SharedLoad(result, () -> {
			cancelled.set(true);
			worker.cancel(true);
			result.cancel(false);
		});
	}

	private static ThreadFactory threadFactory(String prefix) {
		AtomicInteger nextId = new AtomicInteger();
		return runnable -> {
			Thread thread = new Thread(runnable, prefix + "-" + nextId.incrementAndGet());
			thread.setDaemon(true);
			thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
			return thread;
		};
	}

	private static void trimCache() {
		while (CACHE.size() > MAX_CACHE_ENTRIES) {
			CacheReference oldest = CACHE_ORDER.poll();
			if (oldest == null) {
				return;
			}
			if (CACHE.remove(oldest.file(), oldest.entry())) {
				oldest.entry().cancelIfUnused();
			}
		}
	}

	private static boolean removeEntry(Path file, CacheEntry entry) {
		if (!CACHE.remove(file, entry)) {
			return false;
		}
		CACHE_ORDER.remove(new CacheReference(file, entry));
		return true;
	}

	public static final class LoadRequest {
		private final CompletableFuture<SchematicPreviewModel> future;
		private final Runnable cancellation;

		private LoadRequest(CompletableFuture<SchematicPreviewModel> future, Runnable cancellation) {
			this.future = future;
			this.cancellation = cancellation;
		}

		public CompletableFuture<SchematicPreviewModel> future() {
			return this.future;
		}

		public void cancel() {
			this.cancellation.run();
		}
	}

	private record FileStamp(long modifiedTime, long size) {
	}

	private static final class CacheEntry {
		private final FileStamp stamp;
		private final SharedLoad load;
		private final AtomicInteger subscribers = new AtomicInteger();

		private CacheEntry(FileStamp stamp, SharedLoad load) {
			this.stamp = stamp;
			this.load = load;
		}

		private LoadRequest acquire(Path file) {
			this.subscribers.incrementAndGet();
			AtomicBoolean released = new AtomicBoolean();
			return new LoadRequest(this.load.future(), () -> {
				if (!released.compareAndSet(false, true)) {
					return;
				}
				// Future 属于共享缓存项，只有最后一个离开时才能取消任务
				if (this.subscribers.decrementAndGet() == 0 && !this.load.future().isDone()) {
					removeEntry(file, this);
					this.load.cancel();
				}
			});
		}

		private void cancelIfUnused() {
			if (this.subscribers.get() == 0 && !this.load.future().isDone()) {
				this.load.cancel();
			}
		}
	}

	private record SharedLoad(CompletableFuture<SchematicPreviewModel> future, Runnable cancellation) {
		private void cancel() {
			this.cancellation.run();
		}
	}

	private record CacheReference(Path file, CacheEntry entry) {
	}
}
