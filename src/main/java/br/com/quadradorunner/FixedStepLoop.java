package br.com.quadradorunner;

import java.util.function.DoubleConsumer;

/** Fixed simulation time; excess wall time is discarded after 30 catch-up steps. */
final class FixedStepLoop {
    static final double STEP = 1.0 / 120;
    static final int MAX_STEPS = 30;
    private double accumulator;

    void advance(double elapsed, DoubleConsumer update) {
        if (!Double.isFinite(elapsed) || elapsed < 0) {
            throw new IllegalArgumentException("Elapsed time must be finite and nonnegative");
        }
        accumulator += Math.min(elapsed, STEP * MAX_STEPS);
        int steps = 0;
        while (accumulator + 1e-12 >= STEP && steps < MAX_STEPS) {
            update.accept(STEP);
            accumulator = Math.max(0, accumulator - STEP);
            steps++;
        }
    }

    void reset() { accumulator = 0; }
}
