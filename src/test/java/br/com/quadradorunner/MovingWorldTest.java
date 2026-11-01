package br.com.quadradorunner;

import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MovingWorldTest {
    @Test void generatedWorldKeepsRingsOutsideObstaclePaths() {
        Set<Integer> counts = new java.util.HashSet<>();
        for (int seed = 0; seed < 100; seed++) {
            Level level = Level.standard(new Random(seed * 7919L));
            counts.add(level.obstacles.size());
            GameModel model = new GameModel(level);
            model.state = GameModel.State.RUNNING;
            for (int frame = 0; frame < 240; frame++) {
                model.state = GameModel.State.RUNNING;
                model.update(FixedStepLoop.STEP);
                for (Ring ring : level.rings) {
                    for (var obstacle : level.obstacles) {
                        assertFalse(model.obstacleShape(obstacle).intersects(model.movingRing(ring).shape().getBounds2D()));
                    }
                }
            }
        }
        assertTrue(counts.size() > 1);
    }

    @Test void randomizedLayoutsCanBeCompletedWithSingleJumps() {
        for (int seed = 0; seed < 100; seed++) {
            GameModel model = new GameModel(Level.standard(new Random(seed * 7919L)));
            CoursePilot.complete(model);
            assertEquals(GameModel.State.WON, model.state, "seed=" + seed + ", x=" + model.x);
        }
    }

    @Test void purchasedDoubleJumpIsPermanentAndLimitedToTwoJumps() {
        GameProgress progress = new GameProgress(5, Set.of(), Set.of(), Set.of());
        var purchase = AbilityShop.purchase(progress, AbilityShop.Item.DOUBLE_JUMP);
        assertEquals(AbilityShop.Status.PURCHASED, purchase.status());
        assertEquals(0, purchase.progress().balance());
        GameModel model = new GameModel(new Level(List.of(), List.of()), purchase.progress());
        assertTrue(model.jump());
        assertTrue(model.jump());
        assertFalse(model.jump());
        model.restart();
        assertTrue(model.jump());
        assertTrue(model.jump());
    }

    @Test void movementPausesAndResetsWithAttempt() {
        Level level = Level.standard(new Random(1));
        GameModel model = new GameModel(level);
        var obstacle = level.obstacles.get(0);
        var initial = model.obstacleShape(obstacle).getBounds2D();
        model.jump();
        model.update(FixedStepLoop.STEP);
        assertNotEquals(initial.getY(), model.obstacleShape(obstacle).getBounds2D().getY());
        model.pause();
        var paused = model.movingRing(level.rings.get(0));
        model.update(FixedStepLoop.STEP);
        assertEquals(paused, model.movingRing(level.rings.get(0)));
        model.restart();
        assertEquals(initial, model.obstacleShape(obstacle).getBounds2D());
    }
}
