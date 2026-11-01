package br.com.quadradorunner;

import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.util.Objects;
import java.util.HashSet;
import static br.com.quadradorunner.Level.*;

/** Game rules independent of Swing. Owned by the event dispatch thread in the UI. */
final class GameModel {
    static final int SIZE = 34;
    static final int MAX_LIVES = 3;
    static final double HIT_INVULNERABILITY = 1.0;
    int lives = MAX_LIVES;
    private double invulnerabilityRemaining;
    private final java.util.Set<Shape> touchedObstacles = new HashSet<>();
    static final double START_X = 100;
    static final double SPEED = 260;
    private static final double GRAVITY = 1700;
    private static final double JUMP_SPEED = -710;
    static final double OBSTACLE_LIFT = 56;
    static final double OBSTACLE_OSCILLATION_SPEED = 0.7;
    static final double POWER_DURATION = 10;
    static final double DASH_SPEED = 780;
    static final double DASH_DURATION = 0.2;
    static final double DASH_COOLDOWN = 1.2;
    static final double DOUBLE_JUMP_COOLDOWN = 2;
    static final double DURABILITY_DURATION = 5;
    static final double DURABILITY_COOLDOWN = 10;
    static final double ROCKET_COOLDOWN = 4;
    private static final double ROCKET_SPEED = 1200;
    private static final double ROCKET_RANGE = 700;
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
    private double doubleJumpCooldownRemaining;
    private double durabilityRemaining;
    private double durabilityCooldownRemaining;
    private double rocketCooldownRemaining;
    private Shape rocketTarget;
    private double rocketX;
    private double rocketY;
    private final java.util.Set<Shape> destroyedObstacles = new HashSet<>();
    record Projectile(double x, double y) { }
    private boolean glideHeld;
    private GameProgress.Ability blockedGate;
    record LevelSummary(String levelId, int newRings, int totalBalance) { }
    private double worldTime;
    private final java.util.Map<CourseObstacle, Double> obstacleTriggers = new java.util.HashMap<>();
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
    double doubleJumpCooldownRemaining() { return doubleJumpCooldownRemaining; }
    boolean isDurable() { return durabilityRemaining > 0; }
    double durabilityTimeRemaining() { return durabilityRemaining; }
    double durabilityCooldownRemaining() { return durabilityCooldownRemaining; }
    double rocketCooldownRemaining() { return rocketCooldownRemaining; }
    Projectile projectile() { return rocketTarget == null ? null : new Projectile(rocketX, rocketY); }
    boolean isDestroyed(Shape obstacle) { return destroyedObstacles.contains(obstacle); }

    boolean durability() {
        if (state != State.RUNNING || !progress.unlockedAbilities().contains(GameProgress.Ability.DURABILITY)
                || durabilityCooldownRemaining > 0) return false;
        durabilityRemaining = DURABILITY_DURATION;
        durabilityCooldownRemaining = DURABILITY_COOLDOWN;
        return true;
    }

    boolean fireRocket() {
        if (state != State.RUNNING || !progress.unlockedAbilities().contains(GameProgress.Ability.ROCKET)
                || rocketCooldownRemaining > 0 || rocketTarget != null) return false;
        rocketTarget = level.obstacles.stream().filter(o -> !isDestroyed(o))
            .filter(o -> o.getBounds2D().getMinX() >= x + SIZE
                && o.getBounds2D().getMinX() <= x + SIZE + ROCKET_RANGE)
            .min(java.util.Comparator.comparingDouble(o -> o.getBounds2D().getMinX())).orElse(null);
        if (rocketTarget == null) return false;
        rocketX = x + SIZE;
        rocketY = y + SIZE / 2.0;
        rocketCooldownRemaining = ROCKET_COOLDOWN;
        return true;
    }

    private void updateRocket(double dt) {
        if (rocketTarget == null) return;
        Rectangle2D target = obstacleShape(rocketTarget).getBounds2D();
        double dx = target.getCenterX() - rocketX;
        double dy = target.getCenterY() - rocketY;
        double distance = Math.hypot(dx, dy);
        double travel = ROCKET_SPEED * dt;
        if (distance <= travel + 6) {
            destroyedObstacles.add(rocketTarget);
            rocketTarget = null;
        } else {
            rocketX += dx / distance * travel;
            rocketY += dy / distance * travel;
        }
    }

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
        dashCooldownRemaining = progress.unlockedAbilities().contains(GameProgress.Ability.DASH_UPGRADE)
            ? DASH_COOLDOWN / 2 : DASH_COOLDOWN;
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
        boolean permanentDoubleJump = progress.unlockedAbilities().contains(GameProgress.Ability.DOUBLE_JUMP);
        boolean secondJump = !grounded && jumpsUsed < 2
            && (powerRemaining > 0 || (permanentDoubleJump && doubleJumpCooldownRemaining == 0));
        if (state == State.RUNNING && (grounded || secondJump)) {
            if (!grounded && permanentDoubleJump) {
                doubleJumpCooldownRemaining = progress.unlockedAbilities().contains(GameProgress.Ability.DOUBLE_JUMP_UPGRADE)
                    ? DOUBLE_JUMP_COOLDOWN / 2 : DOUBLE_JUMP_COOLDOWN;
            }
            velocityY = JUMP_SPEED;
            grounded = false;
            jumpsUsed++;
            return true;
        }
        return false;
    }

    void restart() {
        worldTime = 0;
        obstacleTriggers.clear();
        lives = MAX_LIVES;
        invulnerabilityRemaining = 0;
        touchedObstacles.clear();
        x = START_X;
        y = GROUND - SIZE;
        velocityY = 0;
        grounded = true;
        jumpsUsed = 0;
        powerRemaining = 0;
        ringsCollectedThisAttempt = 0;
        dashRemaining = 0;
        dashCooldownRemaining = 0;
        doubleJumpCooldownRemaining = 0;
        durabilityRemaining = 0;
        durabilityCooldownRemaining = 0;
        rocketCooldownRemaining = 0;
        rocketTarget = null;
        destroyedObstacles.clear();
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
            durabilityRemaining = 0;
            rocketTarget = null;
            glideHeld = false;
        }
    }

    private double countDown(double value, double elapsed) {
        double remaining = value - elapsed;
        return remaining <= TIME_EPSILON ? 0 : remaining;
    }

    private void updateMovement(double dt, double speed) {
        worldTime += dt;
        for (Shape shape : level.obstacles) {
            if (shape instanceof CourseObstacle obstacle
                    && x + SIZE >= obstacle.x - CourseObstacle.TRIGGER_DISTANCE) {
                obstacleTriggers.putIfAbsent(obstacle, worldTime);
            }
        }
        invulnerabilityRemaining = countDown(invulnerabilityRemaining, dt);
        durabilityRemaining = countDown(durabilityRemaining, dt);
        durabilityCooldownRemaining = countDown(durabilityCooldownRemaining, dt);
        doubleJumpCooldownRemaining = countDown(doubleJumpCooldownRemaining, dt);
        rocketCooldownRemaining = countDown(rocketCooldownRemaining, dt);
        updateRocket(dt);
        powerRemaining = Math.max(0, powerRemaining - dt);
        x += speed * dt;
        boolean gliding = isGliding();
        velocityY += (gliding ? GLIDE_GRAVITY : GRAVITY) * dt;
        if (gliding) velocityY = Math.min(velocityY, GLIDE_MAX_FALL_SPEED);
        double previousY = y;
        y += velocityY * dt;
        grounded = false;
        if (velocityY >= 0 && previousY <= GROUND - SIZE && y >= GROUND - SIZE
                && level.hasGround(x, SIZE)) {
            y = GROUND - SIZE;
            velocityY = 0;
            grounded = true;
            jumpsUsed = 0;
        }
        if (y > GROUND + SIZE * 2) {
            int remainingLives = lives;
            int attemptRings = ringsCollectedThisAttempt;
            restart();
            lives = remainingLives;
            ringsCollectedThisAttempt = attemptRings;
            return;
        }
        Rectangle2D.Double player = new Rectangle2D.Double(x, y, SIZE, SIZE);
        for (Shape obstacle : level.obstacles) {
            if (isDestroyed(obstacle) || isDurable()) continue;
            Rectangle2D bounds = obstacle.getBounds2D();
            if (bounds.getMaxX() <= x || bounds.getMinX() >= x + SIZE) continue;
            if (invulnerabilityRemaining == 0 && obstacleShape(obstacle).intersects(player)
                    && touchedObstacles.add(obstacle)) {
                lives--;
                invulnerabilityRemaining = HIT_INVULNERABILITY;
                if (lives == 0) state = State.LOST;
                break;
            }
        }
        if (state == State.RUNNING) {
            for (AbilityGate gate : level.gates) {
                boolean active = switch (gate.ability()) {
                    case DASH -> isDashing();
                    case GLIDE -> isGliding();
                    case DOUBLE_JUMP -> jumpsUsed == 2;
                    case DURABILITY -> isDurable();
                    case ROCKET, DASH_UPGRADE, DOUBLE_JUMP_UPGRADE -> false;
                };
                if (gate.intersects(player) && !active && !isDurable()) {
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

    Shape obstacleShape(Shape obstacle) {
        if (obstacle instanceof CourseObstacle course) {
            return course.harmfulShape(obstacleElapsed(course), x);
        }
        int index = level.obstacles.indexOf(obstacle);
        double offset = index % 3 == 0
            ? -OBSTACLE_LIFT * (1 - Math.cos(worldTime * OBSTACLE_OSCILLATION_SPEED)) / 2 : 0;
        return java.awt.geom.AffineTransform.getTranslateInstance(0, offset).createTransformedShape(obstacle);
    }

    double obstacleElapsed(CourseObstacle obstacle) {
        Double triggered = obstacleTriggers.get(obstacle);
        return triggered == null ? -1 : worldTime - triggered;
    }


    Ring movingRing(Ring ring) {
        double amplitude = ring.y() >= GROUND - 40 ? 4 : 12;
        double offset = -amplitude * (1 - Math.cos(worldTime * 2 + ring.x() / 200));
        return new Ring(ring.id(), ring.x(), Math.max(0, ring.y() + offset));
    }

    private void collectRings(Rectangle2D player) {
        for (Ring ring : level.rings) {
            if (progress.balance() == Integer.MAX_VALUE) return;
            if (!isRingCollected(ring) && movingRing(ring).intersects(player)) {
                var rings = new HashSet<>(progress.collectedRings());
                rings.add(new GameProgress.RingId(level.id, ring.id()));
                progress = new GameProgress(progress.balance() + 1, rings,
                    progress.unlockedAbilities(), progress.completedLevelIds());
                ringsCollectedThisAttempt++;
            }
        }
    }

}
