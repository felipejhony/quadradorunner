package br.com.quadradorunner;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Stroke;

/** Indoor tiled baths and aqueducts, drawn without advancing simulation time. */
final class PoolScenery {
    static final Color TILE = new Color(169, 201, 197);
    static final Color WATER = new Color(42, 153, 174);
    private static final Color STONE = new Color(108, 149, 149);

    private PoolScenery() { }

    static void draw(Graphics2D g, double camera) {
        g.setPaint(new GradientPaint(0, 0, new Color(55, 88, 94), 0, Level.GROUND, TILE));
        g.fillRect(0, 0, GamePanel.WIDTH, GamePanel.HEIGHT);
        g.setColor(new Color(154, 185, 180, 90));
        int offset = (int) (camera * 0.15) % 48;
        for (int x = -48; x < GamePanel.WIDTH + 48; x += 48) g.drawLine(x - offset, 100, x - offset, Level.GROUND);
        for (int y = 100; y < Level.GROUND; y += 32) g.drawLine(0, y, GamePanel.WIDTH, y);
        g.setColor(new Color(37, 65, 73));
        g.fillRect(0, 0, GamePanel.WIDTH, 100);
        g.setColor(new Color(83, 119, 123));
        for (int i = -1; i < 7; i++) {
            int x = i * 210 - (int) (camera * 0.12) % 210;
            g.fillRect(x, 80, 18, 50);
            g.fillRect(x, 93, 210, 10);
        }
        drawAqueduct(g, camera * 0.22, 144, 200, 0.7);
        drawAqueduct(g, camera * 0.35, 212, 290, 1);
        for (int i = -1; i < 5; i++) {
            int x = i * 310 - (int) (camera * 0.5) % 310;
            drawPool(g, x, 350 + Math.floorMod(i, 2) * 18);
        }
    }

    private static void drawAqueduct(Graphics2D g, double camera, int top, int spacing, double scale) {
        Stroke original = g.getStroke();
        int width = (int) (spacing * 0.76);
        g.setStroke(new BasicStroke((float) (16 * scale)));
        for (int i = -1; i < GamePanel.WIDTH / spacing + 2; i++) {
            int x = i * spacing - (int) camera % spacing;
            g.setColor(STONE);
            g.drawArc(x + 22, top, width, 150, 0, 180);
            g.fillRect(x + 14, top + 75, 18, Level.GROUND - top - 75);
            g.fillRect(x + width + 14, top + 75, 18, Level.GROUND - top - 75);
            g.setColor(new Color(193, 215, 202));
            g.fillRect(x, top - 22, spacing, 20);
            g.setColor(WATER);
            g.fillRect(x, top - 17, spacing, 7);
            g.setColor(new Color(156, 231, 227));
            for (int ripple = 0; ripple < spacing; ripple += 42) g.drawLine(x + ripple, top - 14, x + ripple + 20, top - 14);
        }
        g.setStroke(original);
    }

    private static void drawPool(Graphics2D g, int x, int y) {
        int[] xs = {x + 30, x + 225, x + 270, x};
        int[] ys = {y, y, y + 58, y + 58};
        g.setColor(new Color(211, 222, 207));
        g.fillPolygon(xs, ys, 4);
        int[] innerX = {x + 36, x + 219, x + 255, x + 15};
        int[] innerY = {y + 7, y + 7, y + 49, y + 49};
        g.setColor(new Color(31, 117, 143));
        g.fillPolygon(innerX, innerY, 4);
        g.setColor(new Color(117, 211, 210));
        for (int row = 0; row < 4; row++) {
            int left = x + 40 - row * 4;
            g.drawLine(left, y + 14 + row * 9, left + 150 + row * 12, y + 14 + row * 9);
        }
        g.setColor(new Color(220, 237, 232));
        g.drawArc(x + 186, y - 6, 12, 24, 0, 180);
        g.drawArc(x + 204, y - 6, 12, 24, 0, 180);
        g.drawLine(x + 198, y + 6, x + 198, y + 25);
        g.drawLine(x + 216, y + 6, x + 216, y + 25);
        g.drawLine(x + 198, y + 17, x + 216, y + 17);
    }
}
