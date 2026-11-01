package br.com.quadradorunner;

import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.PathIterator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ObstacleVariationTest {
    @Test void raisedObstacleAllowsWalkingUnderItsRealCollisionShape() {
        Shape obstacle = Level.obstacle(1260, 65, 45);
        GameModel model = new GameModel(new Level(List.of(obstacle), List.of()));
        model.state = GameModel.State.RUNNING;
        for (int i = 0; i < 600; i++) {
            model.update(FixedStepLoop.STEP);
            if (model.x + GameModel.SIZE >= 1260 && model.x < 1325) {
                assertTrue(model.obstacleShape(obstacle).getBounds2D().getMaxY() < model.y - 5);
            }
        }
        assertTrue(model.x > 1325);
        assertTrue(model.grounded);
        assertEquals(GameModel.MAX_LIVES, model.lives);
    }

    @Test void sameObstacleStillCollidesNearGroundAndStationaryOnesStayStill() {
        Shape low = Level.obstacle(200, 65, 45);
        Shape fixed = Level.obstacle(600, 60, 60);
        GameModel model = new GameModel(new Level(List.of(low, fixed), List.of()));
        model.state = GameModel.State.RUNNING;
        for (int i = 0; i < 60; i++) model.update(FixedStepLoop.STEP);
        assertEquals(2, model.lives);
        assertEquals(fixed.getBounds2D(), model.obstacleShape(fixed).getBounds2D());
        model.restart();
        assertEquals(low.getBounds2D(), model.obstacleShape(low).getBounds2D());
    }

    @Test void generatorProducesDifferentAnglesOrientationsAndEllipseProportionsWithinReservedBounds() {
        Random random = new Random(42);
        Set<String> triangles = new HashSet<>();
        Set<String> quadrilaterals = new HashSet<>();
        Set<Double> ellipseRatios = new HashSet<>();
        boolean upright = false;
        boolean inverted = false;
        for (int i = 0; i < 500; i++) {
            int width = 35 + random.nextInt(36);
            int height = 35 + random.nextInt(31);
            Shape shape = ObstacleShapes.random(650, width, height, random);
            assertEquals(650, shape.getBounds2D().getMinX(), 1e-8);
            assertEquals(650 + width, shape.getBounds2D().getMaxX(), 1e-8);
            assertEquals(Level.GROUND - height, shape.getBounds2D().getMinY(), 1e-8);
            assertEquals(Level.GROUND, shape.getBounds2D().getMaxY(), 1e-8);
            if (shape instanceof Ellipse2D) {
                ellipseRatios.add((double) width / height);
                continue;
            }
            StringBuilder points = new StringBuilder();
            int vertices = 0;
            double[] point = new double[6];
            double firstY = 0;
            for (PathIterator path = shape.getPathIterator(null); !path.isDone(); path.next()) {
                int type = path.currentSegment(point);
                if (type == PathIterator.SEG_CLOSE) continue;
                if (vertices == 0) firstY = point[1];
                points.append((point[0] - 650) / width).append(',')
                    .append((point[1] - Level.GROUND + height) / height).append(';');
                vertices++;
            }
            if (vertices == 3) {
                triangles.add(points.toString());
                upright |= firstY == Level.GROUND;
                inverted |= firstY == Level.GROUND - height;
            } else {
                assertEquals(4, vertices);
                quadrilaterals.add(points.toString());
            }
        }
        assertTrue(triangles.size() > 30);
        assertTrue(quadrilaterals.size() > 30);
        assertTrue(ellipseRatios.size() > 30);
        assertTrue(upright && inverted);
    }
}
