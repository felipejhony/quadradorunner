package br.com.quadradorunner;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;

/** Short visual animation; coordinates are relative to the viewport. */
final class DamageFeedback {
    static final double DURATION = 1;
    private double elapsed = DURATION;
    private double x;
    private double y;

    void start(double x, double y) {
        this.x = x;
        this.y = y;
        elapsed = 0;
    }

    void update(double dt) { elapsed = Math.min(DURATION, elapsed + dt); }
    boolean active() { return elapsed < DURATION; }
    boolean playerVisible() { return !active() || (int) (elapsed / 0.09) % 2 != 0; }

    void draw(Graphics2D graphics) {
        if (!active()) return;
        for (int heart = 0; heart < 3; heart++) {
            for (int side : new int[]{-1, 1}) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    double vx = (heart - 1) * 65 + side * 35;
                    g.translate(x + vx * elapsed,
                        y - (100 + heart * 35) * elapsed + 35 * elapsed * elapsed);
                    g.rotate(side * elapsed * 2);
                    g.scale(0.7 + heart * 0.15, 0.7 + heart * 0.15);
                    Path2D half = new Path2D.Double();
                    half.moveTo(0, -5);
                    half.curveTo(side * 8, -17, side * 22, -5, side * 12, 5);
                    half.lineTo(0, 16);
                    half.lineTo(side * 3, 6);
                    half.lineTo(-side * 2, 1);
                    half.closePath();
                    g.setColor(new Color(245, 75, 110, (int) (255 * (1 - elapsed / DURATION))));
                    g.fill(half);
                } finally { g.dispose(); }
            }
        }
    }
}
