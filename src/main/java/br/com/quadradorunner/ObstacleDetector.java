package br.com.quadradorunner;

import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Scans the entire course and fills free ground before play, keeping jumps predictable. */
final class ObstacleDetector {
    static final int START_CLEARANCE = 650;
    static final int FINISH_CLEARANCE = 200;
    static final int OBSTACLE_CLEARANCE = 260;
    static final int PIT_CLEARANCE = 180;
    static final int ITEM_CLEARANCE = 20;
    private static final int SCAN_STEP = 10;

    private ObstacleDetector() { }

    static Level fill(Level level, Random random) {
        List<Shape> obstacles = new ArrayList<>();
        int x = START_CLEARANCE;
        while (x < level.finish - FINISH_CLEARANCE) {
            int width = 35 + random.nextInt(36);
            int slot = obstacles.size();
            boolean splitter = slot % 5 == 4;
            boolean advanced = (level.id.equals("level-03") || level.id.equals("level-04")) && slot > 0;
            if (splitter) width = 140;
            // Keep raised obstacles low enough to remain jumpable throughout their cycle.
            int height = 35 + random.nextInt(obstacles.size() % 3 == 0 ? 11 : 31);
            while (x + width < level.finish - FINISH_CLEARANCE && !isFree(level, x, width)) {
                x += SCAN_STEP;
            }
            if (x + width >= level.finish - FINISH_CLEARANCE) break;
            Shape obstacle = ObstacleShapes.random(x, width, height, random);
            if (splitter) {
                obstacle = new CourseObstacle(x, width, CourseObstacle.Kind.SPLITTER);
            } else if (advanced) {
                CourseObstacle.Kind kind = switch (slot % 3) {
                    case 0 -> CourseObstacle.Kind.FALLING;
                    case 1 -> CourseObstacle.Kind.STACK_UNDER;
                    default -> CourseObstacle.Kind.STACK_MIDDLE;
                };
                obstacle = new CourseObstacle(x, width, kind);
            }
            obstacles.add(obstacle);
            x += width + OBSTACLE_CLEARANCE + random.nextInt(41);
        }
        return new Level(level.id, level.title, level.finish, obstacles, level.powerUps,
            level.rings, level.gates, level.pits);
    }

    static boolean isFree(Level level, int x, int width) {
        for (Level.Pit pit : level.pits) {
            if (overlaps(x, width, pit.x(), pit.width(), PIT_CLEARANCE)) return false;
        }
        for (Ring ring : level.rings) {
            if (overlaps(x, width, ring.x(), Ring.DIAMETER, ITEM_CLEARANCE)) return false;
        }
        for (Rectangle2D item : level.powerUps) {
            if (overlaps(x, width, item.getX(), item.getWidth(), ITEM_CLEARANCE)) return false;
        }
        for (AbilityGate gate : level.gates) {
            if (overlaps(x, width, gate.x(), gate.width(), PIT_CLEARANCE)) return false;
        }
        return true;
    }

    private static boolean overlaps(int x, int width, double otherX, double otherWidth, int margin) {
        return x < otherX + otherWidth + margin && x + width + margin > otherX;
    }
}
