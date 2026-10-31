package br.com.quadradorunner;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Saves immutable snapshots in order, outside the EDT. Completion runs on the worker. */
final class ProgressAutosave implements AutoCloseable {
    private final GameProgressStore store;
    private final ExecutorService worker;
    private boolean closed;

    ProgressAutosave(GameProgressStore store) {
        this(store, Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "progress-save");
            thread.setDaemon(true);
            return thread;
        }));
    }

    ProgressAutosave(GameProgressStore store, ExecutorService worker) {
        this.store = Objects.requireNonNull(store, "store");
        this.worker = Objects.requireNonNull(worker, "worker");
    }

    // Called by the EDT, like close(). A null failure means the save completed successfully.
    void save(GameProgress progress, Consumer<IOException> completed) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(completed, "completed");
        if (closed) throw new IllegalStateException("Autosave is closed");
        worker.execute(() -> {
            IOException failure = null;
            try { store.save(progress); }
            catch (IOException exception) { failure = exception; }
            completed.accept(failure);
        });
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        worker.shutdown();
    }
}
