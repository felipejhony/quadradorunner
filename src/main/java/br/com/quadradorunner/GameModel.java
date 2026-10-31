package br.com.quadradorunner;

import java.awt.Shape;
import java.awt.geom.Rectangle2D;
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
    double powerRemaining;
    int jumpsUsed;
    State state = State.READY;
    double x = START_X;
    double y = GROUND - SIZE;
    double velocityY;
    boolean grounded = true;

    GameModel(Level level) {
        this.level = level;
        collected = new boolean[level.powerUps.size()];
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
            for (int i = 0; i < level.powerUps.size(); i++) {
                if (!collected[i] && player.intersects(level.powerUps.get(i))) {
                    collected[i] = true;
                    powerRemaining = POWER_DURATION;
                }
            }
        }
        if (state == State.RUNNING && x >= FINISH) state = State.WON;
    }

}
