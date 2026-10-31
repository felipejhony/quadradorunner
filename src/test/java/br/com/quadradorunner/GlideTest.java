package br.com.quadradorunner;

import java.awt.Shape;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class GlideTest {
    private Level empty() { return new Level(List.of(), List.of()); }

    private GameModel unlocked(Level level) {
        return new GameModel(level, new GameProgress(0, Set.of(),
            Set.of(GameProgress.Ability.GLIDE, GameProgress.Ability.DASH), Set.of()));
    }

    private void falling(GameModel model) {
        model.state = GameModel.State.RUNNING;
        model.grounded = false;
        model.y = 100;
        model.velocityY = 500;
    }

    private void steps(GameModel model, int count) {
        for (int i = 0; i < count; i++) model.update(FixedStepLoop.STEP);
    }

    @Test void requiresGlidePurchaseAndRunningAirborneDescent() {
        for (Set<GameProgress.Ability> abilities : List.of(Set.<GameProgress.Ability>of(),
                Set.of(GameProgress.Ability.DASH))) {
            GameModel locked = new GameModel(empty(), new GameProgress(0, Set.of(), abilities, Set.of()));
            falling(locked);
            locked.setGlideHeld(true);
            steps(locked, 1);
            assertFalse(locked.isGliding());
            assertTrue(locked.velocityY > 500);
        }
        for (GameModel.State state : GameModel.State.values()) {
            GameModel model = unlocked(empty());
            falling(model);
            model.state = state;
            model.setGlideHeld(true);
            assertEquals(state == GameModel.State.RUNNING, model.isGliding());
        }
        GameModel ground = unlocked(empty());
        ground.state = GameModel.State.RUNNING;
        ground.setGlideHeld(true);
        assertFalse(ground.isGliding());
    }

    @Test void holdingBeforeApexDoesNotChangeAscentButSlowsSubsequentDescent() {
        GameModel glider = unlocked(empty());
        GameModel normal = unlocked(empty());
        glider.jump();
        normal.jump();
        glider.setGlideHeld(true);
        for (int i = 0; i < 50; i++) {
            assertFalse(glider.isGliding());
            steps(glider, 1);
            steps(normal, 1);
            assertEquals(normal.y, glider.y);
            assertEquals(normal.velocityY, glider.velocityY);
        }
        steps(glider, 61);
        steps(normal, 61);
        assertTrue(glider.isGliding());
        assertTrue(glider.y < normal.y);
        assertTrue(glider.velocityY > 0);
        assertTrue(glider.velocityY <= GameModel.GLIDE_MAX_FALL_SPEED);
    }

    @Test void reducedGravityReachesFallSpeedCapWithoutHoveringOrGainingHeight() {
        GameModel model = unlocked(empty());
        falling(model);
        model.velocityY = 0;
        model.setGlideHeld(true);
        double previousY = model.y;
        for (int i = 0; i < 120; i++) {
            steps(model, 1);
            assertTrue(model.y > previousY);
            assertTrue(model.velocityY > 0 && model.velocityY <= 120);
            previousY = model.y;
        }
        assertEquals(120, model.velocityY);
        assertEquals(196.5, model.y, 1e-7);
        assertFalse(model.grounded);
    }

    @Test void releaseRestoresNormalGravityImmediatelyAndDoesNotAlterProgress() {
        GameModel model = unlocked(empty());
        falling(model);
        GameProgress original = model.progress();
        model.setGlideHeld(true);
        steps(model, 1);
        assertEquals(120, model.velocityY);
        model.setGlideHeld(false);
        assertFalse(model.isGliding());
        steps(model, 1);
        assertEquals(120 + 1700 * FixedStepLoop.STEP, model.velocityY, 1e-9);
        assertSame(original, model.progress());
    }

    @Test void pauseCancelsHeldCommandAndFreezesUntilExplicitResume() {
        GameModel model = unlocked(empty());
        falling(model);
        model.setGlideHeld(true);
        steps(model, 1);
        model.togglePause();
        double y = model.y;
        double velocity = model.velocityY;
        steps(model, 120);
        assertEquals(y, model.y);
        assertEquals(velocity, model.velocityY);
        assertFalse(model.isGliding());
        model.togglePause();
        steps(model, 1);
        assertFalse(model.isGliding());
        assertTrue(model.velocityY > 120);
        model.setGlideHeld(true);
        steps(model, 1);
        assertTrue(model.isGliding());
        assertEquals(120, model.velocityY);
    }

    @Test void landingEndsGlideAndRestartDoesNotKeepHeldCommand() {
        GameModel model = unlocked(empty());
        falling(model);
        model.y = Level.GROUND - GameModel.SIZE - 0.5;
        model.jumpsUsed = 2;
        model.setGlideHeld(true);
        steps(model, 1);
        assertTrue(model.grounded);
        assertFalse(model.isGliding());
        assertEquals(0, model.velocityY);
        assertEquals(0, model.jumpsUsed);
        GameProgress original = model.progress();
        model.restart();
        model.jump();
        steps(model, 65);
        assertFalse(model.isGliding());
        assertTrue(model.velocityY > 120);
        assertSame(original, model.progress());
    }

    @Test void temporaryDoubleJumpInterruptsGlideWithoutGrantingExtraJumps() {
        GameModel model = unlocked(empty());
        model.jump();
        steps(model, 55);
        model.setGlideHeld(true);
        assertTrue(model.isGliding());
        assertEquals(1, model.jumpsUsed);
        assertFalse(model.jump());
        model.powerRemaining = 10;
        assertTrue(model.jump());
        assertFalse(model.isGliding());
        assertEquals(2, model.jumpsUsed);
        assertFalse(model.jump());
        steps(model, 55);
        assertTrue(model.isGliding());
        assertEquals(2, model.jumpsUsed);
    }

    @Test void glideCombinesWithDashAndCollectsRingOnce() {
        Ring ring = new Ring("one", 146, 110);
        GameModel model = unlocked(new Level("a", List.of(), List.of(), List.of(ring)));
        falling(model);
        model.setGlideHeld(true);
        assertTrue(model.dash());
        steps(model, 24);
        assertEquals(256, model.x, 1e-7);
        assertEquals(124, model.y, 1e-7);
        assertEquals(120, model.velocityY);
        assertTrue(model.isGliding());
        assertFalse(model.isDashing());
        assertTrue(model.isRingCollected(ring));
        assertEquals(1, model.progress().balance());
        steps(model, 1);
        assertEquals(1, model.progress().balance());
    }

    @Test void allObstacleShapesStillCauseDefeatAndVictoryCancelsGlide() {
        for (Shape obstacle : List.of(Level.obstacle(146, 2, 80), Level.triangle(146, 2, 80),
                Level.circle(146, 80), Level.diamond(146, 2, 80))) {
            GameModel model = unlocked(new Level(List.of(obstacle), List.of()));
            falling(model);
            model.lives = 1;
            model.y = 374;
            model.setGlideHeld(true);
            steps(model, 24);
            assertEquals(GameModel.State.LOST, model.state, obstacle.getClass().getName());
            assertFalse(model.isGliding());
        }
        GameModel winner = unlocked(empty());
        falling(winner);
        winner.x = Level.FINISH - 1;
        winner.setGlideHeld(true);
        steps(winner, 1);
        assertEquals(GameModel.State.WON, winner.state);
        assertFalse(winner.isGliding());
    }

    @Test void purchasedGlideSurvivesReloadButHeldCommandDoesNot(@TempDir Path directory) throws Exception {
        GameModel buyer = new GameModel(empty(), new GameProgress(6, Set.of(), Set.of(), Set.of()));
        buyer.state = GameModel.State.RUNNING;
        buyer.x = Level.FINISH - 1;
        steps(buyer, 1);
        assertEquals(AbilityShop.Status.PURCHASED, buyer.buy(AbilityShop.Item.GLIDE));
        Path file = directory.resolve("progress.properties");
        new GameProgressStore(file).save(buyer.progress());
        GameModel restored = new GameModel(empty(), new GameProgressStore(file).load());
        falling(restored);
        assertFalse(restored.isGliding());
        restored.setGlideHeld(true);
        steps(restored, 1);
        assertTrue(restored.isGliding());
        assertEquals(120, restored.velocityY);
        assertEquals(0, restored.progress().balance());
    }

    @Test void simulationIsStableAcrossDifferentTimerIntervals() {
        GameModel a = unlocked(empty());
        GameModel b = unlocked(empty());
        falling(a);
        falling(b);
        a.setGlideHeld(true);
        b.setGlideHeld(true);
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
    }
}
