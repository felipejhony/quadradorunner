package br.com.quadradorunner;

import java.awt.Shape;
import java.awt.geom.*;
import java.util.List;

/** Static world geometry; coordinates are in world pixels. */
final class Level {
    static final int GROUND = 428;
    static final int FINISH = 8400;
    final List<Shape> obstacles;
    final List<Rectangle2D.Double> powerUps;

    Level(List<Shape> obstacles, List<Rectangle2D.Double> powerUps) {
        this.obstacles = List.copyOf(obstacles);
        this.powerUps = List.copyOf(powerUps);
    }

    static Level standard() {
        List<Shape> obstacles = List.of(
            obstacle(650, 40, 44), obstacle(1050, 48, 60),
            triangle(1460, 70, 65), circle(1910, 70),
            obstacle(2330, 90, 48), obstacle(2820, 55, 66),
            diamond(3260, 80, 90), obstacle(3710, 50, 80),
            triangle(4200, 90, 85), circle(4660, 76),
            diamond(5100, 90, 100), obstacle(5570, 90, 65),
            triangle(6040, 100, 90), circle(6500, 85),
            diamond(6960, 90, 100), obstacle(7430, 100, 60),
            triangle(7900, 100, 85));
        List<Rectangle2D.Double> powerUps = List.of(
            new Rectangle2D.Double(1200, GROUND - 60, 28, 28),
            new Rectangle2D.Double(3900, GROUND - 60, 28, 28),
            new Rectangle2D.Double(6750, GROUND - 60, 28, 28));
        return new Level(obstacles, powerUps);
    }

    static Rectangle2D.Double obstacle(int x, int width, int height) {
        return new Rectangle2D.Double(x, GROUND - height, width, height);
    }

    static Shape triangle(int x, int width, int height) {
        Path2D path = new Path2D.Double();
        path.moveTo(x, GROUND);
        path.lineTo(x + width / 2.0, GROUND - height);
        path.lineTo(x + width, GROUND);
        path.closePath();
        return path;
    }

    static Shape circle(int x, int diameter) {
        return new Ellipse2D.Double(x, GROUND - diameter, diameter, diameter);
    }

    static Shape diamond(int x, int width, int height) {
        Path2D path = new Path2D.Double();
        path.moveTo(x + width / 2.0, GROUND - height);
        path.lineTo(x + width, GROUND - height / 2.0);
        path.lineTo(x + width / 2.0, GROUND);
        path.lineTo(x, GROUND - height / 2.0);
        path.closePath();
        return path;
    }

}
