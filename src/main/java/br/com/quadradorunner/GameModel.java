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
    static final double DASH_SPEED = 780;
    static final double DASH_DURATION = 0.2;
    static final double DASH_COOLDOWN = 1.2;
    static final double GLIDE_GRAVITY = 300;
    static final double GLIDE_MAX_FALL_SPEED = 120;
    private static final double MAX_DASH_DISPLACEMENT = 2;
    private static final double TIME_EPSILON = 1e-9;
    enum State { READY, RUNNING, PAUSED, LOST, WON }
    final Level level;
    final boolean[] collected;
    private GameProgress progress;
    private int ringsCollectedThisAttempt;
    private double dashRemaining;
    private double dashCooldownRemaining;
    private boolean glideHeld;
    private GameProgress.Ability blockedGate;
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
    GameProgress.Ability blockedGate() { return blockedGate; }

    boolean isDashing() { return dashRemaining > 0; }
    double dashTimeRemaining() { return dashRemaining; }
    double dashCooldownRemaining() { return dashCooldownRemaining; }

    void setGlideHeld(boolean held) {
        glideHeld = held && state == State.RUNNING
            && progress.unlockedAbilities().contains(GameProgress.Ability.GLIDE);
    }

    boolean isGliding() {
        return glideHeld && state == State.RUNNING && !grounded && velocityY >= 0;
    }

    boolean dash() {
        if (state != State.RUNNING || !progress.unlockedAbilities().contains(GameProgress.Ability.DASH)
                || isDashing() || dashCooldownRemaining > 0) return false;
        dashRemaining = DASH_DURATION;
        dashCooldownRemaining = DASH_COOLDOWN;
        return true;
    }

    AbilityShop.Status buy(AbilityShop.Item item) {
        Objects.requireNonNull(item, "item");
        if (state != State.WON) return AbilityShop.Status.UNAVAILABLE;
        AbilityShop.Purchase purchase = AbilityShop.purchase(progress, item);
        progress = purchase.progress();
        return purchase.status();
    }

    LevelSummary levelSummary() {
        if (state != State.WON) throw new IllegalStateException("The level has not been completed");
        return new LevelSummary(level.id, ringsCollectedThisAttempt, progress.balance());
    }

    boolean isRingCollected(Ring ring) {
        return progress.collectedRings().contains(new GameProgress.RingId(level.id, ring.id()));
    }

    int collectedRingCount() {
        int count = 0;
        for (Ring ring : level.rings) if (isRingCollected(ring)) count++;
        return count;
    }

    void pause() {
        glideHeld = false;
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
        dashRemaining = 0;
        dashCooldownRemaining = 0;
        glideHeld = false;
        java.util.Arrays.fill(collected, false);
        blockedGate = null;
        state = State.READY;
    }

    void togglePause() {
        if (state == State.RUNNING) pause();
        else if (state == State.PAUSED) state = State.RUNNING;
    }

    void update(double dt) {
        if (!Double.isFinite(dt) || dt <= 0 || dt > FixedStepLoop.STEP + 1e-12) {
            throw new IllegalArgumentException("Use a positive fixed physics step");
        }
        if (state != State.RUNNING) return;
        double remaining = dt;
        while (remaining > 0 && state == State.RUNNING) {
            double speed = isDashing() ? DASH_SPEED : SPEED;
            double step = isDashing()
                ? Math.min(remaining, Math.min(dashRemaining, MAX_DASH_DISPLACEMENT / speed)) : remaining;
            updateMovement(step, speed);
            dashRemaining = countDown(dashRemaining, step);
            dashCooldownRemaining = countDown(dashCooldownRemaining, step);
            remaining -= step;
        }
        if (state == State.LOST || state == State.WON) {
            dashRemaining = 0;
            glideHeld = false;
        }
    }

    private double countDown(double value, double elapsed) {
        double remaining = value - elapsed;
        return remaining <= TIME_EPSILON ? 0 : remaining;
    }

    private void updateMovement(double dt, double speed) {
        powerRemaining = Math.max(0, powerRemaining - dt);
        x += speed * dt;
        boolean gliding = isGliding();
        velocityY += (gliding ? GLIDE_GRAVITY : GRAVITY) * dt;
        if (gliding) velocityY = Math.min(velocityY, GLIDE_MAX_FALL_SPEED);
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
            for (AbilityGate gate : level.gates) {
                boolean active = switch (gate.ability()) {
                    case DASH -> isDashing();
                    case GLIDE -> isGliding();
                };
                if (gate.intersects(player) && !active) {
                    blockedGate = gate.ability();
                    state = State.LOST;
                    break;
                }
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
        if (state == State.RUNNING && x >= level.finish) {
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
