package br.com.quadradorunner;

import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.Objects;

/** Stable local ID and world position. The whole outer disk is collectible. */
record Ring(String id, double x, double y) {
    static final int DIAMETER = 24;

    Ring {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("Ring ID cannot be blank");
        if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || y < 0) {
            throw new IllegalArgumentException("Ring coordinates must be finite and nonnegative");
        }
    }

    Ellipse2D.Double shape() {
        return new Ellipse2D.Double(x, y, DIAMETER, DIAMETER);
    }

    boolean intersects(Rectangle2D player) {
        return shape().intersects(player);
    }
}
