package br.com.quadradorunner;

import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ObstacleDetectorTest {
    @Test void allCampaignRoutesHaveSafeObstaclesThroughoutTheirEntireLength() {
        for (int seed = 0; seed < 100; seed++) {
            List<Level> levels = List.of(Level.standard(new Random(seed)),
                Level.dashRoute(new Random(seed)), Level.glideRoute(new Random(seed)));
            assertTrue(levels.get(0).pits.isEmpty());
            assertFalse(levels.get(1).pits.isEmpty(), "Second route must have pits");
            assertFalse(levels.get(2).pits.isEmpty(), "Third route must have pits");
            for (Level level : levels) {
                double previousEnd = GameModel.START_X;
                for (Shape obstacle : level.obstacles) {
                    Rectangle2D bounds = obstacle.getBounds2D();
                    assertTrue(bounds.getMinX() - previousEnd >= ObstacleDetector.OBSTACLE_CLEARANCE);
                    assertTrue(bounds.getMaxX() < level.finish - ObstacleDetector.FINISH_CLEARANCE);
                    assertTrue(ObstacleDetector.isFree(level, (int) bounds.getX(), (int) bounds.getWidth()));
                    previousEnd = bounds.getMaxX();
                }
                // Check every screen-sized region, including the previously empty late course.
                for (int start = 650; start + 1200 < level.finish - 200; start += 400) {
                    int left = start;
                    assertTrue(level.obstacles.stream().anyMatch(shape ->
                        shape.getBounds2D().getMinX() >= left && shape.getBounds2D().getMaxX() < left + 1200),
                        level.id + " empty section at " + start + ", seed=" + seed);
                }
                completeWithSingleJumps(level, seed);
            }
        }
    }

    @Test void replacesOldObstaclesAndSkipsFullyOccupiedGround() {
        Level occupied = new Level("occupied", "Occupied", 2000,
            List.of(Level.obstacle(700, 40, 40)), List.of(new Rectangle2D.Double(0, 0, 2000, 50)),
            List.of(), List.of());
        assertTrue(ObstacleDetector.fill(occupied, new Random(1)).obstacles.isEmpty());
        Level empty = new Level("empty", List.of(), List.of(), List.of());
        Level filled = ObstacleDetector.fill(empty, new Random(1));
        assertFalse(filled.obstacles.isEmpty());
        assertEquals(filled.obstacles.stream().map(shape -> shape.getBounds2D()).toList(),
            ObstacleDetector.fill(empty, new Random(1)).obstacles.stream().map(shape -> shape.getBounds2D()).toList());
    }

    private void completeWithSingleJumps(Level level, int seed) {
        GameModel model = new GameModel(level);
        model.jump();
        for (int frame = 0; frame < 5000 && model.state == GameModel.State.RUNNING; frame++) {
            boolean obstacleAhead = level.obstacles.stream().anyMatch(shape -> {
                double distance = shape.getBounds2D().getMinX() - model.x - GameModel.SIZE;
                return distance > 0 && distance < 65;
            });
            boolean pitAhead = level.pits.stream().anyMatch(pit -> {
                double distance = pit.x() - model.x - GameModel.SIZE;
                return distance > 0 && distance < 35;
            });
            if (model.grounded && (obstacleAhead || pitAhead)) model.jump();
            model.update(FixedStepLoop.STEP);
        }
        assertEquals(GameModel.State.WON, model.state, level.id + ", seed=" + seed + ", x=" + model.x);
        assertEquals(GameModel.MAX_LIVES, model.lives, "Course must allow avoiding all damage");
    }
}
