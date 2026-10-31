package br.com.quadradorunner;

import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.util.Objects;
import java.util.HashSet;
import static br.com.quadradorunner.Level.*;

/** Game rules independent of Swing. Owned by the event dispatch thread in the UI. */
final class GameModel {
    static final int SIZE = 34;
    static final double START_X = 100;
    static final double SPEED = 260;
    private static final double GRAVITY = 1700;
    private static final double JUMP_SPEED = -710;
    static final double POWER_DURATION = 10;
    enum State { READY, RUNNING, PAUSED, LOST, WON }
    final Level level;
    final boolean[] collected;
    private GameProgress progress;
    private int ringsCollectedThisAttempt;
    record LevelSummary(String levelId, int newRings, int totalBalance) { }
    double powerRemaining;
    int jumpsUsed;
    State state = State.READY;
    double x = START_X;
    double y = GROUND - SIZE;
    double velocityY;
    boolean grounded = true;

    GameModel(Level level) {
        this(level, new GameProgress());
    }

    GameModel(Level level, GameProgress progress) {
        this.level = Objects.requireNonNull(level, "level");
        this.progress = Objects.requireNonNull(progress, "progress");
        collected = new boolean[level.powerUps.size()];
    }

    GameProgress progress() { return progress; }

    LevelSummary levelSummary() {
        if (state != State.WON) throw new IllegalStateException("The level has not been completed");
        return new LevelSummary(level.id, ringsCollectedThisAttempt, progress.balance());
    }

    boolean isRingCollected(Ring ring) {
        return progress.collectedRings().contains(new GameProgress.RingId(level.id, ring.id()));
    }

    void pause() {
        if (state == State.RUNNING) state = State.PAUSED;
    }

    boolean jump() {
        if (state == State.READY) state = State.RUNNING;
        if (state == State.RUNNING && (grounded || (powerRemaining > 0 && jumpsUsed < 2))) {
            velocityY = JUMP_SPEED;
            grounded = false;
            jumpsUsed++;
            return true;
        }
        return false;
    }

    void restart() {
        x = START_X;
        y = GROUND - SIZE;
        velocityY = 0;
        grounded = true;
        jumpsUsed = 0;
        powerRemaining = 0;
        ringsCollectedThisAttempt = 0;
        java.util.Arrays.fill(collected, false);
        state = State.READY;
    }

    void togglePause() {
        if (state == State.RUNNING) state = State.PAUSED;
        else if (state == State.PAUSED) state = State.RUNNING;
    }

    void update(double dt) {
        if (!Double.isFinite(dt) || dt <= 0 || dt > FixedStepLoop.STEP + 1e-12) {
            throw new IllegalArgumentException("Use a positive fixed physics step");
        }
        if (state != State.RUNNING) return;
        powerRemaining = Math.max(0, powerRemaining - dt);
        x += SPEED * dt;
        velocityY += GRAVITY * dt;
        y += velocityY * dt;
        if (y >= GROUND - SIZE) {
            y = GROUND - SIZE;
            velocityY = 0;
            grounded = true;
            jumpsUsed = 0;
        }
        Rectangle2D.Double player = new Rectangle2D.Double(x, y, SIZE, SIZE);
        for (Shape obstacle : level.obstacles) {
            if (obstacle.intersects(player)) {
                state = State.LOST;
                break;
            }
        }
        if (state == State.RUNNING) {
            collectRings(player);
            for (int i = 0; i < level.powerUps.size(); i++) {
                if (!collected[i] && player.intersects(level.powerUps.get(i))) {
                    collected[i] = true;
                    powerRemaining = POWER_DURATION;
                }
            }
        }
        if (state == State.RUNNING && x >= FINISH) {
            state = State.WON;
            if (!progress.completedLevelIds().contains(level.id)) {
                var completed = new HashSet<>(progress.completedLevelIds());
                completed.add(level.id);
                progress = new GameProgress(progress.balance(), progress.collectedRings(),
                    progress.unlockedAbilities(), completed);
            }
        }
    }

    private void collectRings(Rectangle2D player) {
        for (Ring ring : level.rings) {
            if (progress.balance() == Integer.MAX_VALUE) return;
            if (!isRingCollected(ring) && ring.intersects(player)) {
                var rings = new HashSet<>(progress.collectedRings());
                rings.add(new GameProgress.RingId(level.id, ring.id()));
                progress = new GameProgress(progress.balance() + 1, rings,
                    progress.unlockedAbilities(), progress.completedLevelIds());
                ringsCollectedThisAttempt++;
            }
        }
    }

}
