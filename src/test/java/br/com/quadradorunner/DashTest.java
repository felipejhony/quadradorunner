package br.com.quadradorunner;

import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DashTest {
    private GameModel unlocked(Level level) {
        return new GameModel(level, new GameProgress(0, Set.of(), Set.of(GameProgress.Ability.DASH), Set.of()));
    }

    private Level empty() { return new Level(List.of(), List.of()); }
    private void steps(GameModel model, int count) {
        for (int i = 0; i < count; i++) model.update(FixedStepLoop.STEP);
    }

    @Test void requiresDashPurchaseAndRunningState() {
        for (Set<GameProgress.Ability> abilities : List.of(Set.<GameProgress.Ability>of(),
                Set.of(GameProgress.Ability.GLIDE))) {
            GameModel locked = new GameModel(empty(), new GameProgress(0, Set.of(), abilities, Set.of()));
            locked.state = GameModel.State.RUNNING;
            assertFalse(locked.dash());
            steps(locked, 1);
            assertEquals(GameModel.START_X + GameModel.SPEED * FixedStepLoop.STEP, locked.x, 1e-9);
            assertEquals(0, locked.dashCooldownRemaining());
        }
        for (GameModel.State state : GameModel.State.values()) {
            GameModel model = unlocked(empty());
            model.state = state;
            assertEquals(state == GameModel.State.RUNNING, model.dash());
        }
    }

    @Test void hasExactDurationDistanceAndReturnToNormalSpeed() {
        GameModel model = unlocked(empty());
        model.state = GameModel.State.RUNNING;
        GameProgress before = model.progress();
        assertTrue(model.dash());
        assertFalse(model.dash());
        steps(model, 23);
        assertTrue(model.isDashing());
        steps(model, 1);
        assertFalse(model.isDashing());
        assertEquals(0, model.dashTimeRemaining());
        assertEquals(GameModel.START_X + 156, model.x, 1e-7);
        steps(model, 1);
        assertEquals(GameModel.START_X + 156 + GameModel.SPEED / 120, model.x, 1e-7);
        assertSame(before, model.progress()); // Activating dash does not spend currency or change the save.
    }

    @Test void cooldownStartsAtActivationAndPreventsImmediateReuse() {
        GameModel model = unlocked(empty());
        model.state = GameModel.State.RUNNING;
        assertTrue(model.dash());
        steps(model, 24);
        assertFalse(model.dash());
        assertEquals(1, model.dashCooldownRemaining(), 1e-9);
        steps(model, 119);
        assertFalse(model.dash());
        steps(model, 1);
        assertEquals(0, model.dashCooldownRemaining());
        assertTrue(model.dash());
    }

    @Test void dashEndingInsidePhysicsStepSplitsRemainingTimeAtNormalSpeed() {
        GameModel model = unlocked(empty());
        model.state = GameModel.State.RUNNING;
        model.dash();
        for (int i = 0; i < 28; i++) model.update(0.007);
        assertEquals(0.004, model.dashTimeRemaining(), 1e-9);
        model.update(FixedStepLoop.STEP);
        assertFalse(model.isDashing());
        double normalTime = 28 * 0.007 + FixedStepLoop.STEP - 0.2;
        assertEquals(GameModel.START_X + 156 + 260 * normalTime, model.x, 1e-7);
    }

    @Test void pauseFreezesActiveDurationCooldownMovementAndTemporaryPower() {
        GameModel model = unlocked(empty());
        model.state = GameModel.State.RUNNING;
        model.powerRemaining = 5;
        model.dash();
        steps(model, 6);
        model.pause();
        double x = model.x;
        double active = model.dashTimeRemaining();
        double cooldown = model.dashCooldownRemaining();
        double power = model.powerRemaining;
        steps(model, 240);
        assertFalse(model.dash());
        assertEquals(x, model.x);
        assertEquals(active, model.dashTimeRemaining());
        assertEquals(cooldown, model.dashCooldownRemaining());
        assertEquals(power, model.powerRemaining);
        model.togglePause();
        steps(model, 18);
        assertFalse(model.isDashing());
        assertEquals(GameModel.START_X + 156, model.x, 1e-7);
    }

    @Test void airborneDashKeepsGravityAndDoesNotGrantOrConsumeJumps() {
        GameModel model = unlocked(empty());
        assertTrue(model.jump());
        double initialVelocity = model.velocityY;
        assertTrue(model.dash());
        steps(model, 12);
        assertEquals(initialVelocity + 1700 * 0.1, model.velocityY, 1e-7);
        assertTrue(model.y < Level.GROUND - GameModel.SIZE);
        assertEquals(1, model.jumpsUsed);
        assertFalse(model.jump());
        model.powerRemaining = 10;
        assertTrue(model.jump());
        assertEquals(2, model.jumpsUsed);
        assertFalse(model.jump());
    }

    @Test void collidesWithEveryShapeIncludingVeryThinObstaclesWithoutInvulnerability() {
        for (Shape obstacle : List.of(Level.obstacle(146, 1, 80), Level.triangle(146, 2, 80),
                Level.circle(146, 2), Level.diamond(146, 2, 80))) {
            GameModel model = unlocked(new Level(List.of(obstacle), List.of()));
            model.state = GameModel.State.RUNNING;
            model.lives = 1;
            model.dash();
            steps(model, 24);
            assertEquals(GameModel.State.LOST, model.state, obstacle.getClass().getName());
            assertTrue(model.x < 148);
            assertFalse(model.isDashing());
            assertFalse(model.dash());
        }
    }

    @Test void dashCollectsRingsAndCrystalsOnceAlongItsPath() {
        Ring ring = new Ring("one", 146, Level.GROUND - 30);
        Level level = new Level("a", List.of(),
            List.of(new Rectangle2D.Double(200, Level.GROUND - 30, 28, 28)), List.of(ring));
        GameModel model = unlocked(level);
        model.state = GameModel.State.RUNNING;
        model.dash();
        steps(model, 24);
        assertTrue(model.isRingCollected(ring));
        assertTrue(model.collected[0]);
        assertEquals(1, model.progress().balance());
        assertTrue(model.powerRemaining > 9.8);
        steps(model, 24);
        assertEquals(1, model.progress().balance());
    }

    @Test void victoryEndsDashAndRestartResetsTransientTimersOnly() {
        GameModel model = unlocked(empty());
        model.state = GameModel.State.RUNNING;
        model.x = Level.FINISH - 1;
        model.dash();
        steps(model, 1);
        assertEquals(GameModel.State.WON, model.state);
        assertFalse(model.isDashing());
        assertFalse(model.dash());
        GameProgress won = model.progress();
        model.restart();
        assertSame(won, model.progress());
        assertEquals(0, model.dashTimeRemaining());
        assertEquals(0, model.dashCooldownRemaining());
        assertEquals(GameModel.State.READY, model.state);
        model.jump();
        assertTrue(model.dash());
    }

    @Test void purchasedDashSurvivesDiskReloadButActiveTimersDoNot(@TempDir Path directory) throws Exception {
        GameModel buyer = new GameModel(empty(), new GameProgress(4, Set.of(), Set.of(), Set.of()));
        buyer.state = GameModel.State.RUNNING;
        buyer.x = Level.FINISH - 1;
        steps(buyer, 1);
        assertEquals(AbilityShop.Status.PURCHASED, buyer.buy(AbilityShop.Item.DASH));
        buyer.restart();
        buyer.jump();
        buyer.dash();
        steps(buyer, 6);
        Path file = directory.resolve("progress.properties");
        new GameProgressStore(file).save(buyer.progress());
        GameModel restored = new GameModel(empty(), new GameProgressStore(file).load());
        assertEquals(0, restored.dashTimeRemaining());
        assertEquals(0, restored.dashCooldownRemaining());
        assertEquals(0, restored.progress().balance());
        restored.jump();
        assertTrue(restored.dash());
    }

    @Test void dashSimulationIsStableAcrossDifferentTimerIntervals() {
        GameModel a = unlocked(empty());
        GameModel b = unlocked(empty());
        a.jump();
        b.jump();
        a.dash();
        b.dash();
        FixedStepLoop first = new FixedStepLoop();
        FixedStepLoop second = new FixedStepLoop();
        for (int i = 0; i < 100; i++) first.advance(0.01, a::update);
        for (int i = 0; i < 20; i++) {
            second.advance(0.013, b::update);
            second.advance(0.037, b::update);
        }
        assertEquals(a.x, b.x);
        assertEquals(a.y, b.y);
        assertEquals(a.velocityY, b.velocityY);
        assertEquals(a.dashCooldownRemaining(), b.dashCooldownRemaining());
    }
}
