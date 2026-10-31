package br.com.quadradorunner;

import java.awt.geom.Rectangle2D;
import java.util.Objects;

/** Full-height skill challenge: passage requires the named movement to be active. */
record AbilityGate(String id, int x, int width, GameProgress.Ability ability) {
    AbilityGate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ability, "ability");
        if (id.isBlank() || x < 0 || width <= 0) throw new IllegalArgumentException("Invalid ability gate");
    }

    boolean intersects(Rectangle2D player) {
        return new Rectangle2D.Double(x, 0, width, Level.GROUND).intersects(player);
    }
}
