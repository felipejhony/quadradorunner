package br.com.quadradorunner;

import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;

/** Course geometry with animation clocks owned by each attempt in GameModel. */
final class CourseObstacle extends Rectangle2D.Double {
    enum Kind { STACK_UNDER, STACK_MIDDLE, FALLING, SPLITTER }
    final Kind kind;
    static final double TRIGGER_DISTANCE = 180;

    CourseObstacle(int x, int width, Kind kind) {
        super(x, Level.GROUND - 300, width, 300);
        this.kind = kind;
    }

    Shape harmfulShape(double elapsed, double playerX) {
        double ground = Level.GROUND;
        return switch (kind) {
            case STACK_UNDER -> stack(ground - 400, ground - 52);
            case STACK_MIDDLE -> {
                Area area = stack(ground - 400, ground - 195);
                area.add(new Area(new Rectangle2D.Double(x, ground - 55, width, 55)));
                yield area;
            }
            case FALLING -> elapsed < 0 || elapsed > 1.6
                ? new Rectangle2D.Double(x, -240, width, 220)
                : new Rectangle2D.Double(x, Math.min(ground - 220, -240 + Math.max(0, elapsed - 0.45) * 1000), width, 220);
            case SPLITTER -> {
                if (elapsed < 0) yield new Rectangle2D.Double(x, ground - 140, width, 140);
                double opening = Math.min(1, elapsed / 0.25);
                double closing = Math.max(0, Math.min(1, (playerX + GameModel.SIZE - x) / (width + GameModel.SIZE)));
                double gap = (60 - 20 * closing) * opening;
                yield new Rectangle2D.Double(x, ground - 70 - gap, width, 70);
            }
        };
    }

    Shape harmlessShape(double elapsed) {
        if (kind != Kind.SPLITTER || elapsed < 0) return new Rectangle2D.Double();
        // The lower half retracts into the track and never participates in collision.
        double opening = Math.min(1, elapsed / 0.25);
        return new Rectangle2D.Double(x, Level.GROUND - 70 + 52 * opening, width, 70);
    }

    private Area stack(double top, double bottom) {
        Area area = new Area();
        for (double y = top; y < bottom; y += 48) {
            area.add(new Area(new Rectangle2D.Double(x, y, width, Math.min(44, bottom - y))));
        }
        return area;
    }
}
