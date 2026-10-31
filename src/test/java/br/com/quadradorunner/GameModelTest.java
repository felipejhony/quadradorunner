package br.com.quadradorunner;

import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static br.com.quadradorunner.GameModel.State.*;

class GameModelTest {
    private GameModel empty() { return new GameModel(new Level(List.of(), List.of())); }
    private void steps(GameModel game, int count) {
        for (int i = 0; i < count; i++) game.update(FixedStepLoop.STEP);
    }

    @Test void startsJumpsAndLandsWithoutAirJump() {
        GameModel game = empty();
        steps(game, 10);
        assertEquals(GameModel.START_X, game.x);
        game.jump();
        steps(game, 10);
        assertEquals(RUNNING, game.state);
        assertTrue(game.y < Level.GROUND - GameModel.SIZE);
        double velocity = game.velocityY;
        game.jump();
        assertEquals(velocity, game.velocityY);
        steps(game, 110);
        assertTrue(game.grounded);
        assertEquals(0, game.jumpsUsed);
        assertEquals(360, game.x, 1e-8);
    }

    @Test void pauseFreezesMovementAndPowerAndRequiresExplicitResume() {
        GameModel game = empty();
        game.jump();
        game.powerRemaining = 5;
        game.pause();
        double x = game.x;
        steps(game, 120);
        game.jump();
        assertEquals(PAUSED, game.state);
        assertEquals(x, game.x);
        assertEquals(5, game.powerRemaining);
        game.togglePause();
        steps(game, 120);
        assertEquals(x + 260, game.x, 1e-8);
        assertEquals(4, game.powerRemaining, 1e-8);
    }

    @Test void crystalsCollectOnceRenewDurationAndRestartRestoresEverything() {
        GameModel game = new GameModel(new Level(List.of(), List.of(
            new Rectangle2D.Double(100, 0, 40, Level.GROUND),
            new Rectangle2D.Double(200, 0, 40, Level.GROUND))));
        game.jump();
        steps(game, 1);
        assertTrue(game.collected[0]);
        assertEquals(10, game.powerRemaining);
        steps(game, 2);
        assertTrue(game.powerRemaining < 10);
        while (!game.collected[1]) steps(game, 1);
        assertEquals(10, game.powerRemaining);
        game.restart();
        assertEquals(READY, game.state);
        assertEquals(GameModel.START_X, game.x);
        assertEquals(Level.GROUND - GameModel.SIZE, game.y);
        assertEquals(0, game.velocityY);
        assertTrue(game.grounded);
        assertEquals(0, game.powerRemaining);
        assertEquals(0, game.jumpsUsed);
        assertFalse(game.collected[0]);
        assertFalse(game.collected[1]);
    }

    @Test void doubleJumpIsLimitedRenewedByLandingAndExpires() {
        GameModel game = empty();
        game.powerRemaining = 10;
        game.jump();
        steps(game, 20);
        game.jump();
        assertEquals(2, game.jumpsUsed);
        steps(game, 1);
        double velocity = game.velocityY;
        game.jump();
        assertEquals(velocity, game.velocityY);
        steps(game, 130);
        assertTrue(game.grounded);
        game.jump();
        assertEquals(1, game.jumpsUsed);
        game.powerRemaining = FixedStepLoop.STEP / 2;
        steps(game, 1);
        velocity = game.velocityY;
        game.jump();
        assertEquals(0, game.powerRemaining);
        assertEquals(velocity, game.velocityY);
    }

    @Test void allObstacleShapesCauseLossAndPreventCollectionOrVictory() {
        for (Shape shape : List.of(Level.obstacle(120, 80, 80), Level.triangle(120, 80, 80),
                Level.circle(120, 80), Level.diamond(120, 80, 80))) {
            GameModel game = new GameModel(new Level(List.of(shape),
                List.of(new Rectangle2D.Double(100, 0, 100, Level.GROUND))));
            game.lives = 1;
            game.state = RUNNING;
            steps(game, 1);
            assertEquals(LOST, game.state, shape.getClass().getName());
            assertFalse(game.collected[0]);
            double x = game.x;
            steps(game, 10);
            assertEquals(x, game.x);
        }
        GameModel game = new GameModel(new Level(List.of(Level.obstacle(Level.FINISH, 40, 80)), List.of()));
        game.state = RUNNING;
        game.x = Level.FINISH - 1;
        game.lives = 1;
        steps(game, 1);
        assertEquals(LOST, game.state);
    }

    @Test void shapeGeometryDoesNotTreatBoundingBoxCornersAsSolid() {
        for (Shape shape : List.of(Level.triangle(100, 80, 80), Level.circle(100, 80),
                Level.diamond(100, 80, 80))) {
            assertFalse(shape.intersects(100, Level.GROUND - 80, 2, 2));
            assertTrue(shape.intersects(138, Level.GROUND - 42, 4, 4));
        }
        assertFalse(Level.obstacle(100, 40, 40).intersects(66, Level.GROUND - 40, 34, 34));
    }

    @Test void victoryStopsSimulationAndPauseDoesNotChangeTerminalStates() {
        GameModel game = empty();
        game.state = RUNNING;
        game.x = Level.FINISH - 1;
        steps(game, 1);
        assertEquals(WON, game.state);
        double x = game.x;
        game.togglePause();
        game.jump();
        steps(game, 20);
        assertEquals(WON, game.state);
        assertEquals(x, game.x);
    }

    @Test void standardLevelCanBeCompletedWithoutCrystals() {
        Level standard = Level.standard();
        GameModel game = new GameModel(new Level(standard.obstacles, List.of()));
        game.jump();
        for (int step = 0; step < 5000 && game.state == RUNNING; step++) {
            if (game.grounded) {
                boolean approaching = standard.obstacles.stream().anyMatch(obstacle -> {
                    double distance = obstacle.getBounds2D().getMinX() - (game.x + GameModel.SIZE);
                    return distance > 0 && distance < 65;
                });
                if (approaching) game.jump();
            }
            steps(game, 1);
        }
        assertEquals(WON, game.state, "Failed at x=" + game.x);
        assertEquals(0, game.powerRemaining);
    }
}
