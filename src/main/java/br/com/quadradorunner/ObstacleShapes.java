package br.com.quadradorunner;

import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.Random;

/** Variants stay within the detector's reserved horizontal and vertical bounds. */
final class ObstacleShapes {
    private ObstacleShapes() { }

    static Shape random(int x, int width, int height, Random random) {
        double top = Level.GROUND - height;
        double apex = 0.15 + random.nextDouble() * 0.7;
        return switch (random.nextInt(4)) {
            case 0 -> random.nextBoolean()
                ? polygon(x, top, width, height, 0, 1, apex, 0, 1, 1)
                : polygon(x, top, width, height, 0, 0, 1, 0, apex, 1);
            case 1 -> new Ellipse2D.Double(x, top, width, height);
            case 2 -> polygon(x, top, width, height,
                apex, 0, 1, 0.25 + random.nextDouble() * 0.5,
                0.15 + random.nextDouble() * 0.7, 1, 0, 0.25 + random.nextDouble() * 0.5);
            default -> {
                double inset = 0.1 + random.nextDouble() * 0.3;
                yield random.nextBoolean()
                    ? polygon(x, top, width, height, inset, 0, 1, 0, 1 - inset, 1, 0, 1)
                    : polygon(x, top, width, height, inset, 0, 1 - inset, 0, 1, 1, 0, 1);
            }
        };
    }

    private static Shape polygon(int x, double y, int width, int height, double... vertices) {
        Path2D path = new Path2D.Double();
        path.moveTo(x + vertices[0] * width, y + vertices[1] * height);
        for (int i = 2; i < vertices.length; i += 2) {
            path.lineTo(x + vertices[i] * width, y + vertices[i + 1] * height);
        }
        path.closePath();
        return path;
    }
}
