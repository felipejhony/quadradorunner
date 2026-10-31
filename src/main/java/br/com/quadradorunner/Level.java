package br.com.quadradorunner;

import java.awt.Shape;
import java.awt.geom.*;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Static world geometry; coordinates are in world pixels. */
final class Level {
    static final int GROUND = 428;
    static final int FINISH = 8400;
    static final String STANDARD_ID = "level-01";
    final String id;
    final List<Shape> obstacles;
    final List<Rectangle2D.Double> powerUps;
    final List<Ring> rings;

    Level(List<Shape> obstacles, List<Rectangle2D.Double> powerUps) {
        this(STANDARD_ID, obstacles, powerUps, List.of());
    }

    Level(String id, List<Shape> obstacles, List<Rectangle2D.Double> powerUps, List<Ring> rings) {
        this.id = Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("Level ID cannot be blank");
        this.obstacles = List.copyOf(obstacles);
        this.powerUps = List.copyOf(powerUps);
        this.rings = List.copyOf(rings);
        Set<String> ringIds = new HashSet<>();
        for (Ring ring : this.rings) {
            if (!ringIds.add(ring.id())) throw new IllegalArgumentException("Duplicate ring ID: " + ring.id());
        }
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
        // Explicit IDs must survive reordering and future changes to world positions.
        List<Ring> rings = List.of(
            new Ring("ring-01", 350, GROUND - 36), new Ring("ring-02", 850, GROUND - 130),
            new Ring("ring-03", 1260, GROUND - 120), new Ring("ring-04", 1660, GROUND - 36),
            new Ring("ring-05", 2120, GROUND - 130), new Ring("ring-06", 2600, GROUND - 36),
            new Ring("ring-07", 3070, GROUND - 100), new Ring("ring-08", 3500, GROUND - 36),
            new Ring("ring-09", 4450, GROUND - 120), new Ring("ring-10", 4850, GROUND - 36),
            new Ring("ring-11", 5800, GROUND - 120), new Ring("ring-12", 8150, GROUND - 36));
        return new Level(STANDARD_ID, obstacles, powerUps, rings);
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
