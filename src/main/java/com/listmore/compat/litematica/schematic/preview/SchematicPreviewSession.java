package com.listmore.compat.litematica.schematic.preview;

import com.listmore.ListMore;
import com.listmore.compat.litematica.schematic.preview.SchematicPreviewLoader.FileStamp;
import com.listmore.compat.litematica.schematic.preview.SchematicPreviewLoader.LoadRequest;
import com.listmore.compat.litematica.schematic.preview.SchematicPreviewLoader.SchematicInfo;
import com.listmore.compat.litematica.schematic.preview.model.SchematicPreviewModel;
import com.listmore.compat.litematica.schematic.preview.render.SchematicPreviewRenderManager;

import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

// The GUI thread owns all session state and consumes only the currently selected futures.
public final class SchematicPreviewSession {
    private static final long FILE_CHECK_INTERVAL_NANOS = 1_000_000_000L;
    private final SchematicPreviewTransform transform = new SchematicPreviewTransform();
    private final SchematicPreviewRenderManager renderer = new SchematicPreviewRenderManager();
    private Path file;
    private FileStamp fileStamp;
    private CompletableFuture<FileStamp> fileCheck;
    private long nextFileCheckNanos;
    private long loadStartedNanos;
    private boolean firstFrameReported;
    private LoadRequest loadRequest;
    private SchematicInfo info;
    private SchematicPreviewModel model;
    private Throwable loadFailure;
    private boolean closed;

    public SchematicPreviewTransform transform() {
        return this.transform;
    }

    public SchematicPreviewRenderManager renderer() {
        return this.renderer;
    }

    public SchematicInfo info() {
        return this.info;
    }

    public SchematicPreviewModel model() {
        return this.model;
    }

    public boolean isLoading() {
        return this.loadRequest != null || (this.info == null && this.fileCheck != null);
    }

    public boolean hasFailure() {
        return this.loadFailure != null;
    }

    public void setFile(Path file) {
        if (this.closed) {
            return;
        }
        Path normalizedFile = file.toAbsolutePath().normalize();
        if (!normalizedFile.equals(this.file)) {
            this.clear();
            this.file = normalizedFile;
            this.transform.reset();
            this.startFileCheck(System.nanoTime());
        }
    }

    public void clear() {
        if (this.file == null) {
            return;
        }
        if (this.fileCheck != null) {
            this.fileCheck.cancel(false);
            this.fileCheck = null;
        }
        this.file = null;
        this.fileStamp = null;
        this.clearPreview();
    }

    private void clearPreview() {
        this.releaseLoad();
        this.info = null;
        this.model = null;
        this.loadFailure = null;
        this.firstFrameReported = false;
        this.renderer.clearModel();
    }

    public void update() {
        if (this.file == null || this.closed) {
            return;
        }
        if (this.fileCheck != null && this.fileCheck.isDone()) {
            this.consumeFileCheck();
        }
        long now = System.nanoTime();
        if (this.fileCheck == null && now - this.nextFileCheckNanos >= 0) {
            this.startFileCheck(now);
        }
        if (this.loadRequest == null) {
            return;
        }
        try {
            if (this.info == null) {
                if (!this.loadRequest.infoFuture().isDone()) {
                    return;
                }
                this.info = this.loadRequest.infoFuture().join();
                ListMore.LOGGER.debug("Schematic preview info ready for {} after {} ms",
                        this.file.getFileName(), this.elapsedMillis());
            }
            if (this.loadRequest.modelFuture().isDone()) {
                this.model = this.loadRequest.modelFuture().join();
                this.releaseLoad();
                this.renderer.modelChanged();
                ListMore.LOGGER.debug("Schematic preview model ready for {} after {} ms",
                        this.file.getFileName(), this.elapsedMillis());
            }
        } catch (CompletionException | CancellationException exception) {
            this.loadFailure = exception instanceof CompletionException ? exception.getCause() : exception;
            this.releaseLoad();
            ListMore.LOGGER.debug("Schematic preview load failed for {}", this.file.getFileName(), this.loadFailure);
        }
    }

    private void startFileCheck(long now) {
        this.nextFileCheckNanos = now + FILE_CHECK_INTERVAL_NANOS;
        this.fileCheck = SchematicPreviewLoader.checkFile(this.file);
        if (this.fileStamp == null) {
            this.loadStartedNanos = now;
        }
    }

    private void consumeFileCheck() {
        CompletableFuture<FileStamp> check = this.fileCheck;
        this.fileCheck = null;
        FileStamp stamp;
        try {
            stamp = check.join();
        } catch (CompletionException exception) {
            if (this.fileStamp != null) {
                this.clearPreview();
                this.fileStamp = null;
            }
            this.loadFailure = exception.getCause();
            return;
        }
        if (stamp.equals(this.fileStamp)) {
            return;
        }
        if (this.fileStamp != null) {
            this.clearPreview();
            this.transform.reset();
            this.loadStartedNanos = this.nextFileCheckNanos - FILE_CHECK_INTERVAL_NANOS;
        }
        this.fileStamp = stamp;
        this.loadFailure = null;
        this.loadRequest = SchematicPreviewLoader.load(this.file, stamp);
    }

    private void releaseLoad() {
        if (this.loadRequest != null) {
            this.loadRequest.release();
            this.loadRequest = null;
        }
    }

    public void previewRendered() {
        if (this.firstFrameReported) {
            return;
        }
        this.firstFrameReported = true;
        ListMore.LOGGER.debug("Schematic preview first frame submitted for {} after {} ms",
                this.file.getFileName(), this.elapsedMillis());
    }

    private double elapsedMillis() {
        return (System.nanoTime() - this.loadStartedNanos) / 1_000_000.0;
    }

    public void close() {
        if (this.closed) {
            return;
        }
        this.clear();
        this.closed = true;
        this.renderer.close();
    }
}
