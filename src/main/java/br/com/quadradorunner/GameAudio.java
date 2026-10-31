package br.com.quadradorunner;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Serializes device access off the EDT. A failed device leaves the game playable. */
final class GameAudio implements AutoCloseable {
    enum Effect { JUMP, COLLECT, LOSE, WIN, DASH, GLIDE, PURCHASE, DENIED }

    interface Output extends AutoCloseable {
        void play(Effect effect);
        void music(boolean playing);
        void silence();
        @Override void close();
    }

    private static final Logger LOG = Logger.getLogger(GameAudio.class.getName());
    private final ExecutorService worker;
    private final Supplier<Output> factory;
    private Output output;
    private boolean failed;
    private boolean closed;
    private boolean muted;

    GameAudio() {
        this(SynthAudioOutput::new);
    }

    GameAudio(Supplier<Output> factory) {
        this(factory, Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "game-audio");
            thread.setDaemon(true);
            return thread;
        }));
    }

    GameAudio(Supplier<Output> factory, ExecutorService worker) {
        this.factory = factory;
        this.worker = worker;
    }

    // Commands and close are submitted by the EDT; output and failed belong to the worker.
    void play(Effect effect) {
        if (!muted) submit(device -> device.play(effect));
    }

    void configure(boolean muted, boolean paused) {
        this.muted = muted;
        submit(device -> {
            if (muted || paused) device.silence();
            device.music(!muted && !paused);
        });
    }

    private void submit(Consumer<Output> command) {
        if (closed) return;
        worker.execute(() -> {
            if (failed) return;
            try {
                if (output == null) output = factory.get();
                command.accept(output);
            } catch (RuntimeException exception) {
                failed = true;
                LOG.log(Level.WARNING, "Audio unavailable; continuing without sound", exception);
                releaseOutput();
            }
        });
    }

    private void releaseOutput() {
        if (output != null) {
            try { output.close(); }
            catch (RuntimeException exception) { LOG.log(Level.FINE, "Audio cleanup failed", exception); }
            output = null;
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        worker.execute(this::releaseOutput);
        worker.shutdown();
    }
}
