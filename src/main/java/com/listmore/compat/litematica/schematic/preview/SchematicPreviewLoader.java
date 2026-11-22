package com.listmore.compat.litematica.schematic.preview;

// import com.listmore.ListMore;
import com.listmore.compat.litematica.schematic.preview.model.SchematicPreviewModel;
import com.listmore.compat.litematica.schematic.preview.model.SchematicPreviewScanner;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.SchematicMetadata;
import fi.dy.masa.litematica.schematic.SchematicSchema;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class SchematicPreviewLoader {
    private static final int MAX_CACHE_ENTRIES = 10;
    private static final int SCAN_WORKERS = Math.max(1,
            Math.min(6, Runtime.getRuntime().availableProcessors() - 2));
    private static final ExecutorService ATTRIBUTE_EXECUTOR = Executors.newSingleThreadExecutor(
            threadFactory("ListMore preview attributes"));
    private static final ExecutorService FILE_EXECUTOR = Executors.newFixedThreadPool(2,
            threadFactory("ListMore preview file"));
    private static final ExecutorService SCAN_EXECUTOR = Executors.newFixedThreadPool(
            SCAN_WORKERS, threadFactory("ListMore preview scan"));
    private static final LinkedHashMap<Path, CacheEntry> CACHE = new LinkedHashMap<>();

    private SchematicPreviewLoader() {
    }

    static CompletableFuture<FileStamp> checkFile(Path file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
                return new FileStamp(attributes.lastModifiedTime(), attributes.size());
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }, ATTRIBUTE_EXECUTOR);
    }

    static LoadRequest load(Path file, FileStamp stamp) {
        synchronized (CACHE) {
            CacheEntry cached = CACHE.get(file);
            if (cached != null && cached.stamp.equals(stamp) && !cached.model.isCompletedExceptionally()) {
                cached.subscribers++;
                // ListMore.LOGGER.debug("Schematic preview cache hit for {} (model ready: {})",
                //         file.getFileName(), cached.model.isDone());
                return new LoadRequest(file, cached);
            }
            CacheEntry entry = new CacheEntry(stamp);
            entry.subscribers = 1;
            CACHE.put(file, entry);
            if (CACHE.size() > MAX_CACHE_ENTRIES) {
                Iterator<Path> oldest = CACHE.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
            entry.worker = FILE_EXECUTOR.submit(() -> readAndScan(file, entry));
            return new LoadRequest(file, entry);
        }
    }

    private static void readAndScan(Path file, CacheEntry entry) {
        // long started = System.nanoTime();
        try {
            LitematicaSchematic schematic = LitematicaSchematic.createFromFile(
                    file.getParent(), file.getFileName().toString());
            if (schematic == null) {
                throw new IllegalStateException("Litematica returned no schematic");
            }
            if (entry.cancelled.get()) {
                throw new CancellationException("Schematic preview load cancelled");
            }
            SchematicInfo info = SchematicInfo.from(schematic.getMetadata());
            // long parsed = System.nanoTime();
            entry.info.complete(info);
            // ListMore.LOGGER.debug("Schematic preview parsed {} in {} ms",
            //         file.getFileName(), (parsed - started) / 1_000_000.0);
            // long scanStarted = System.nanoTime();
            SchematicPreviewModel model = SchematicPreviewScanner.scan(
                    schematic, SCAN_EXECUTOR, SCAN_WORKERS, entry.cancelled::get);
            // long scanned = System.nanoTime();
            entry.model.complete(model);
            // ListMore.LOGGER.debug("Schematic preview scanned {} in {} ms ({} sections, {} blocks)",
            //         file.getFileName(), (scanned - scanStarted) / 1_000_000.0,
            //         model.sections().size(), model.blockCount());
        } catch (Throwable throwable) {
            entry.info.completeExceptionally(throwable);
            entry.model.completeExceptionally(throwable);
            synchronized (CACHE) {
                CACHE.remove(file, entry);
            }
        }
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

    static final class LoadRequest {
        private final Path file;
        private final CacheEntry entry;
        private boolean released;

        private LoadRequest(Path file, CacheEntry entry) {
            this.file = file;
            this.entry = entry;
        }

        CompletableFuture<SchematicInfo> infoFuture() {
            return this.entry.info;
        }

        CompletableFuture<SchematicPreviewModel> modelFuture() {
            return this.entry.model;
        }

        void release() {
            synchronized (CACHE) {
                if (this.released) {
                    return;
                }
                this.released = true;
                if (--this.entry.subscribers == 0 && !this.entry.model.isDone()) {
                    CACHE.remove(this.file, this.entry);
                    this.entry.cancelled.set(true);
                    this.entry.worker.cancel(true);
                    this.entry.info.cancel(false);
                    this.entry.model.cancel(false);
                }
            }
        }

    }

    public record SchematicInfo(String name, String author, long timeCreated, long timeModified,
                                boolean modified, int regionCount, int totalVolume, int totalBlocks,
                                int sizeX, int sizeY, int sizeZ, SchematicSchema schema) {
        private static SchematicInfo from(SchematicMetadata metadata) {
            var size = metadata.getEnclosingSize();
            return new SchematicInfo(metadata.getName(), metadata.getAuthor(), metadata.getTimeCreated(),
                    metadata.getTimeModified(), metadata.hasBeenModified(), metadata.getRegionCount(),
                    metadata.getTotalVolume(), metadata.getTotalBlocks(), size.getX(), size.getY(), size.getZ(),
                    metadata.getSchematicSchema());
        }
    }

    record FileStamp(FileTime modifiedTime, long size) {
    }

    private static final class CacheEntry {
        private final FileStamp stamp;
        private final CompletableFuture<SchematicInfo> info = new CompletableFuture<>();
        private final CompletableFuture<SchematicPreviewModel> model = new CompletableFuture<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private Future<?> worker;
        private int subscribers;

        private CacheEntry(FileStamp stamp) {
            this.stamp = stamp;
        }
    }
}
