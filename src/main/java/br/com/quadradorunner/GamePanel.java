package br.com.quadradorunner;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.AffineTransform;
import java.util.List;
import javax.swing.*;

/** All game state and painting run on Swing's event dispatch thread. */
public final class GamePanel extends JPanel {
    static final int WIDTH = 960;
    static final int HEIGHT = 540;
    static final int GROUND = 428;
    static final int SIZE = 34;
    static final int FINISH = 8400;
    private static final double SPEED = 260;
    private static final double GRAVITY = 1700;
    private static final double JUMP_SPEED = -710;
    private static final double POWER_DURATION = 10;
    private static final Color INK = new Color(225, 237, 247);

    enum State { READY, RUNNING, PAUSED, LOST, WON }

    private final List<Shape> obstacles = List.of(
        obstacle(650, 40, 44), obstacle(1050, 48, 60),
        triangle(1460, 70, 65), circle(1910, 70),
        obstacle(2330, 90, 48), obstacle(2820, 55, 66),
        diamond(3260, 80, 90), obstacle(3710, 50, 80),
        triangle(4200, 90, 85), circle(4660, 76),
        diamond(5100, 90, 100), obstacle(5570, 90, 65),
        triangle(6040, 100, 90), circle(6500, 85),
        diamond(6960, 90, 100), obstacle(7430, 100, 60),
        triangle(7900, 100, 85));
    private final List<Rectangle2D.Double> powerUps = List.of(
        new Rectangle2D.Double(1200, GROUND - 60, 28, 28),
        new Rectangle2D.Double(3900, GROUND - 60, 28, 28),
        new Rectangle2D.Double(6750, GROUND - 60, 28, 28));
    private final boolean[] collected = new boolean[powerUps.size()];
    private double powerRemaining;
    private int jumpsUsed;
    private final Timer timer;
    private State state = State.READY;
    private double x = 100;
    private double y = GROUND - SIZE;
    private double velocityY;
    private double camera;
    private boolean grounded = true;
    private boolean jumpHeld;
    private long lastTick;

    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(new Color(13, 22, 39));
        bind("pressed SPACE", "jump", this::pressJump);
        bind("released SPACE", "release", () -> jumpHeld = false);
        bind("pressed UP", "jumpUp", this::pressJump);
        bind("released UP", "releaseUp", () -> jumpHeld = false);
        bind("pressed R", "restart", this::restart);
        bind("pressed P", "pause", this::togglePause);
        bind("pressed ESCAPE", "pauseEscape", this::togglePause);
        timer = new Timer(16, event -> tick());
        timer.setCoalesce(true);
    }

    @Override public void addNotify() {
        super.addNotify();
        lastTick = System.nanoTime();
        timer.start();
    }

    @Override public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    private static Rectangle2D.Double obstacle(int x, int width, int height) {
        return new Rectangle2D.Double(x, GROUND - height, width, height);
    }

    private static Shape triangle(int x, int width, int height) {
        Path2D path = new Path2D.Double();
        path.moveTo(x, GROUND);
        path.lineTo(x + width / 2.0, GROUND - height);
        path.lineTo(x + width, GROUND);
        path.closePath();
        return path;
    }

    private static Shape circle(int x, int diameter) {
        return new Ellipse2D.Double(x, GROUND - diameter, diameter, diameter);
    }

    private static Shape diamond(int x, int width, int height) {
        Path2D path = new Path2D.Double();
        path.moveTo(x + width / 2.0, GROUND - height);
        path.lineTo(x + width, GROUND - height / 2.0);
        path.lineTo(x + width / 2.0, GROUND);
        path.lineTo(x, GROUND - height / 2.0);
        path.closePath();
        return path;
    }

    private void bind(String key, String name, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    private void pressJump() {
        if (jumpHeld) return;
        jumpHeld = true;
        if (state == State.READY) state = State.RUNNING;
        if (state == State.RUNNING && (grounded || (powerRemaining > 0 && jumpsUsed < 2))) {
            velocityY = JUMP_SPEED;
            grounded = false;
            jumpsUsed++;
        }
    }

    private void restart() {
        x = 100;
        y = GROUND - SIZE;
        velocityY = 0;
        camera = 0;
        grounded = true;
        jumpHeld = false;
        jumpsUsed = 0;
        powerRemaining = 0;
        java.util.Arrays.fill(collected, false);
        state = State.READY;
        repaint();
    }

    private void togglePause() {
        if (state == State.RUNNING) state = State.PAUSED;
        else if (state == State.PAUSED) state = State.RUNNING;
        repaint();
    }

    private void tick() {
        long now = System.nanoTime();
        double dt = Math.min((now - lastTick) / 1_000_000_000.0, 0.05);
        lastTick = now;
        if (state == State.RUNNING) {
            // Small physics steps keep collisions reliable even after a slow frame.
            int steps = Math.max(1, (int) Math.ceil(dt / (1.0 / 120)));
            for (int i = 0; i < steps && state == State.RUNNING; i++) update(dt / steps);
        }
        repaint();
    }

    private void update(double dt) {
        powerRemaining = Math.max(0, powerRemaining - dt);
        x += SPEED * dt;
        velocityY += GRAVITY * dt;
        y += velocityY * dt;
        if (y >= GROUND - SIZE) {
            y = GROUND - SIZE;
            velocityY = 0;
            grounded = true;
            jumpsUsed = 0;
        }
        Rectangle2D.Double player = new Rectangle2D.Double(x, y, SIZE, SIZE);
        for (Shape obstacle : obstacles) {
            if (obstacle.intersects(player)) {
                state = State.LOST;
                break;
            }
        }
        if (state == State.RUNNING) {
            for (int i = 0; i < powerUps.size(); i++) {
                if (!collected[i] && player.intersects(powerUps.get(i))) {
                    collected[i] = true;
                    powerRemaining = POWER_DURATION;
                }
            }
        }
        if (state == State.RUNNING && x >= FINISH) state = State.WON;
        camera = Math.max(0, Math.min(x - 210, FINISH - WIDTH + 230));
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            drawBackground(g);
            drawWorld(g);
            drawHud(g);
            switch (state) {
                case READY -> overlay(g, "QUADRADO RUNNER", "Pegue os cristais azuis: salto duplo por 10 segundos!", "ESPAÇO ou ↑ para começar e pular");
                case PAUSED -> overlay(g, "PAUSA", "Respire. A pista espera por você.", "P ou ESC para continuar");
                case LOST -> overlay(g, "QUASE LÁ!", "Você bateu em um obstáculo. Tente pular um pouco antes.", "R para tentar novamente");
                case WON -> overlay(g, "SUCESSO!", "Você chegou ao fim do mapa. Boa corrida!", "R para jogar novamente");
                default -> { }
            }
        } finally { g.dispose(); }
    }

    private void drawBackground(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(14, 23, 43), 0, GROUND, new Color(29, 54, 76)));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(new Color(76, 105, 127));
        for (int i = 0; i < 35; i++) {
            int sx = Math.floorMod(i * 137 - (int) (camera * 0.12), WIDTH + 30);
            int sy = 35 + (i * 73 % 240);
            g.fillOval(sx, sy, 2, 2);
        }
        g.setColor(new Color(35, 66, 86));
        for (int i = -1; i < 9; i++) {
            int bx = i * 180 - (int) (camera * 0.25) % 180;
            int h = 70 + Math.floorMod(i * 37, 110);
            g.fillRoundRect(bx, GROUND - h, 130, h + 20, 12, 12);
        }
    }

    private void drawWorld(Graphics2D g) {
        g.setColor(new Color(17, 32, 47));
        g.fillRect(0, GROUND, WIDTH, HEIGHT - GROUND);
        g.setColor(new Color(83, 227, 192));
        g.fillRect(0, GROUND, WIDTH, 3);
        g.setColor(new Color(42, 61, 77));
        for (int i = 0; i < WIDTH / 48 + 2; i++) {
            int tx = i * 48 - (int) camera % 48;
            g.drawLine(tx, GROUND + 20, tx + 22, GROUND + 20);
        }
        AffineTransform scroll = AffineTransform.getTranslateInstance(-camera, 0);
        for (Shape obstacle : obstacles) {
            Shape visible = scroll.createTransformedShape(obstacle);
            g.setColor(new Color(245, 115, 106));
            g.fill(visible);
            g.setColor(new Color(255, 177, 144));
            g.draw(visible);
        }
        for (int i = 0; i < powerUps.size(); i++) {
            if (collected[i]) continue;
            Rectangle2D.Double item = powerUps.get(i);
            int cx = (int) (item.getCenterX() - camera);
            int cy = (int) item.getCenterY();
            g.setColor(new Color(89, 185, 255, 55));
            g.fillOval(cx - 23, cy - 23, 46, 46);
            g.setColor(new Color(104, 202, 255));
            g.fillPolygon(new int[]{cx, cx + 14, cx, cx - 14},
                new int[]{cy - 14, cy, cy + 14, cy}, 4);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.drawString("2", cx - 4, cy + 5);
        }
        int fx = (int) (FINISH - camera);
        g.setColor(INK);
        g.fillRect(fx, GROUND - 140, 4, 140);
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 6; col++) {
                g.setColor((row + col) % 2 == 0 ? INK : new Color(30, 45, 62));
                g.fillRect(fx + 4 + col * 10, GROUND - 140 + row * 10, 10, 10);
            }
        }
        int px = (int) (x - camera);
        if (powerRemaining > 0) {
            g.setColor(new Color(104, 202, 255, 100));
            g.fillRoundRect(px - 5, (int) y - 5, SIZE + 10, SIZE + 10, 12, 12);
        }
        g.setColor(new Color(0, 0, 0, 55));
        g.fillOval(px - 4, GROUND - 7, SIZE + 8, 12);
        g.setColor(new Color(83, 227, 192));
        g.fillRoundRect(px, (int) y, SIZE, SIZE, 7, 7);
        g.setColor(new Color(15, 47, 51));
        g.fillRect(px + 22, (int) y + 9, 5, 6);
        g.fillRect(px + 23, (int) y + 23, 8, 3);
    }

    private void drawHud(Graphics2D g) {
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.drawString("QUADRADO / RUNNER", 28, 36);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.drawString("ESPAÇO / ↑  pular     P / ESC  pausar     R  reiniciar", 28, 511);
        double progress = Math.max(0, Math.min(1, (x - 100) / (FINISH - 100.0)));
        g.drawString("CHEGADA  " + (int) (progress * 100) + "%", 720, 32);
        g.setColor(new Color(57, 79, 98));
        g.fillRoundRect(720, 43, 210, 7, 7, 7);
        g.setColor(new Color(83, 227, 192));
        g.fillRoundRect(720, 43, (int) (210 * progress), 7, 7, 7);
        g.setColor(new Color(104, 202, 255));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString(powerRemaining > 0
            ? "SALTO DUPLO  " + (int) Math.ceil(powerRemaining) + "s  •  " + (2 - jumpsUsed) + " salto(s) disponível(is)"
            : "◆ Cristal azul = salto duplo por 10s", 28, 65);
    }

    private void overlay(Graphics2D g, String title, String subtitle, String hint) {
        g.setColor(new Color(6, 13, 25, 195));
        g.fillRect(0, 65, WIDTH, 410);
        g.setColor(new Color(83, 227, 192));
        centered(g, "01 / A PISTA", 182, 13, Font.BOLD);
        g.setColor(INK);
        centered(g, title, 242, 42, Font.BOLD);
        centered(g, subtitle, 283, 17, Font.PLAIN);
        g.setColor(new Color(83, 227, 192));
        centered(g, hint, 339, 18, Font.BOLD);
    }

    private void centered(Graphics2D g, String text, int baseline, int size, int style) {
        g.setFont(new Font(Font.SANS_SERIF, style, size));
        g.drawString(text, (WIDTH - g.getFontMetrics().stringWidth(text)) / 2, baseline);
    }
}
