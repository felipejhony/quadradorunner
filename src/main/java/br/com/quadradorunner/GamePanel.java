package br.com.quadradorunner;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.*;
import javax.swing.*;
import java.util.function.Supplier;
import java.util.function.Consumer;
import static br.com.quadradorunner.Level.*;
import static br.com.quadradorunner.GameModel.SIZE;

/** Swing adapter: all callbacks and drawing run on the EDT. */
public final class GamePanel extends JPanel {
    static final int WIDTH = 960;
    static final int HEIGHT = 540;
    private static final int CAMERA_ANCHOR = 210;
    private static final int FINISH_MARGIN = 230;
    private static final double TIMER_DISPLAY_EPSILON = 1e-9;
    private static final Color INK = new Color(225, 237, 247);
    private static final Color SKY_TOP = new Color(14, 23, 43);
    private static final Color SKY_BOTTOM = new Color(29, 54, 76);
    private static final Color STAR = new Color(76, 105, 127);
    private static final Color BUILDING = new Color(35, 66, 86);
    private static final Color GROUND_COLOR = new Color(17, 32, 47);
    private static final Color ACCENT = new Color(83, 227, 192);
    private static final Color GROUND_MARK = new Color(42, 61, 77);
    private static final Color OBSTACLE_FILL = new Color(245, 115, 106);
    private static final Color OBSTACLE_EDGE = new Color(255, 177, 144);
    private static final Color CRYSTAL_GLOW = new Color(89, 185, 255, 55);
    private static final Color CRYSTAL = new Color(104, 202, 255);
    private static final Color RING_GOLD = new Color(255, 206, 76);
    private static final Color RING_HIGHLIGHT = new Color(255, 246, 190);
    private static final Color DASH_COLOR = new Color(181, 157, 255);
    private static final Color DASH_TRAIL = new Color(181, 157, 255, 90);
    private static final Color GLIDE_COLOR = new Color(134, 234, 255);
    private static final Stroke RING_STROKE = new BasicStroke(4);
    private static final Color FLAG_DARK = new Color(30, 45, 62);
    private static final Color PLAYER_GLOW = new Color(104, 202, 255, 100);
    private static final Color SHADOW = new Color(0, 0, 0, 55);
    private static final Color PLAYER_FACE = new Color(15, 47, 51);
    private static final Color PROGRESS_TRACK = new Color(57, 79, 98);
    private static final Color OVERLAY_SHADE = new Color(6, 13, 25, 195);
    private static final Font CRYSTAL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    private static final Font TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 18);
    private static final Font CONTROLS_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    private static final Font POWER_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    private static final Font OVERLAY_LABEL = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    private static final Font OVERLAY_TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 42);
    private static final Font OVERLAY_SUBTITLE = new Font(Font.SANS_SERIF, Font.PLAIN, 17);
    private static final Font OVERLAY_HINT = new Font(Font.SANS_SERIF, Font.BOLD, 18);
    private GameModel model;
    private final GameSession session;
    private final Supplier<GameAudio> audioFactory;
    private final Consumer<GameProgress> progressChanged;
    private final JButton resetButton = new JButton("Resetar todo o progresso");
    private boolean resetting;
    private GameAudio audio;
    private boolean muted;
    private boolean windowFocused = true;
    private String shopMessage = "";
    private final FixedStepLoop loop = new FixedStepLoop();
    private final GameInput input = new GameInput();
    private final Timer timer;
    private long lastTick;
    private double camera;

    public GamePanel() {
        this(new GameModel(Level.standard()));
    }

    GamePanel(GameModel model) {
        this(model, GameAudio::new);
    }

    GamePanel(GameModel model, Supplier<GameAudio> audioFactory) {
        this(model, audioFactory, progress -> { });
    }

    GamePanel(GameModel model, Supplier<GameAudio> audioFactory, Consumer<GameProgress> progressChanged) {
        this(new GameSession(model), audioFactory, progressChanged);
    }

    GamePanel(GameSession session, Supplier<GameAudio> audioFactory, Consumer<GameProgress> progressChanged) {
        this(session, audioFactory, progressChanged, GamePanel::resetLocally);
    }

    GamePanel(GameSession session, Supplier<GameAudio> audioFactory, Consumer<GameProgress> progressChanged,
              Consumer<GamePanel> resetRequested) {
        this.session = session;
        this.model = session.current();
        this.audioFactory = audioFactory;
        this.progressChanged = progressChanged;
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(new Color(13, 22, 39));
        setLayout(null);
        resetButton.setBounds(670, 478, 260, 26);
        resetButton.setFocusable(false);
        resetButton.addActionListener(event -> resetRequested.accept(this));
        add(resetButton);
        bindKey("SPACE", GameInput.Key.SPACE, this::jump);
        bindKey("UP", GameInput.Key.UP, this::jump);
        bindKey("D", GameInput.Key.DASH, () -> model.dash());
        bindKey("G", GameInput.Key.GLIDE, () -> model.setGlideHeld(true), () -> model.setGlideHeld(false));
        bindKey("R", GameInput.Key.RESTART, session::replay);
        bindKey("ENTER", GameInput.Key.ADVANCE, () -> {
            if (session.advance()) model = session.current();
        });
        bindKey("P", GameInput.Key.PAUSE, () -> model.togglePause());
        bindKey("ESCAPE", GameInput.Key.ESCAPE, () -> {
            if (session.isShopOpen()) session.closeShop();
            else model.togglePause();
        });
        bindKey("B", GameInput.Key.SHOP, () -> {
            if (session.isShopOpen()) session.closeShop();
            else if (session.openShop()) shopMessage = "";
        });
        bindKey("1", GameInput.Key.BUY_DASH, () -> buy(AbilityShop.Item.DASH));
        bindKey("2", GameInput.Key.BUY_GLIDE, () -> buy(AbilityShop.Item.GLIDE));
        bindKey("M", GameInput.Key.MUTE, () -> muted = !muted);
        timer = new Timer(16, event -> tick());
        timer.setCoalesce(true);
        refreshResetButton();
    }

    boolean confirmReset() {
        return JOptionPane.showOptionDialog(this,
            "Resetar todo o jogo?\nSaldo, argolas, habilidades e fases concluídas serão zerados.\n"
                + "Os cristais serão restaurados e somente a primeira fase ficará liberada.",
            "Confirmar reset completo", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
            null, new String[]{"Cancelar", "Resetar jogo"}, "Cancelar") == 1;
    }

    private void resetLocally() {
        if (confirmReset()) {
            beginReset();
            finishReset(true);
        }
    }

    boolean isResetting() { return resetting; }

    void beginReset() {
        resetting = true;
        input.clear();
        model.pause();
        timer.stop();
        refreshResetButton();
        repaint();
    }

    void finishReset(boolean success) {
        if (!resetting) return;
        if (success) {
            session.reset();
            model = session.current();
            shopMessage = "";
        }
        resetting = false;
        input.clear();
        synchronizeTimer();
        repaint();
    }

    private void refreshResetButton() {
        resetButton.setVisible(resetting || model.state != GameModel.State.RUNNING);
        resetButton.setEnabled(!resetting);
        resetButton.setText(resetting ? "Resetando progresso..." : "Resetar todo o progresso");
    }

    private void jump() {
        if (model.jump()) play(GameAudio.Effect.JUMP);
    }

    private void buy(AbilityShop.Item item) {
        AbilityShop.Status status = session.buy(item);
        shopMessage = switch (status) {
            case PURCHASED -> item.label + " desbloqueado permanentemente!";
            case ALREADY_OWNED -> "Você já possui " + item.label + ". Nenhuma argola foi gasta.";
            case INSUFFICIENT_FUNDS -> "Saldo insuficiente para comprar " + item.label + ".";
            case UNAVAILABLE -> "";
        };
        if (status == AbilityShop.Status.PURCHASED) progressChanged.accept(model.progress());
    }

    private void play(GameAudio.Effect effect) {
        if (audio != null) audio.play(effect);
    }

    private void configureAudio() {
        if (audio != null) audio.configure(muted, !windowFocused || model.state == GameModel.State.PAUSED);
    }

    private void bindKey(String stroke, GameInput.Key key, Runnable command) {
        bindKey(stroke, key, command, () -> { });
    }

    private void bindKey(String stroke, GameInput.Key key, Runnable command, Runnable released) {
        bind("pressed " + stroke, () -> {
            if (resetting) return;
            if (input.press(key)) {
                command.run();
                synchronizeTimer();
                repaint();
            }
        });
        bind("released " + stroke, () -> {
            input.release(key);
            released.run();
        });
    }

    private void bind(String stroke, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(stroke), stroke);
        getActionMap().put(stroke, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    void pauseForFocusLoss() {
        windowFocused = false;
        input.clear();
        model.pause();
        synchronizeTimer();
        repaint();
    }

    void audioFocusGained() {
        windowFocused = true;
        configureAudio();
    }

    void closeAudio() {
        if (audio != null) {
            audio.close();
            audio = null;
        }
    }

    private void synchronizeTimer() {
        refreshResetButton();
        configureAudio();
        boolean shouldRun = !resetting && isDisplayable() && model.state == GameModel.State.RUNNING;
        if (shouldRun && timer.isRunning()) return;
        loop.reset();
        lastTick = System.nanoTime();
        if (shouldRun) timer.start();
        else timer.stop();
    }

    @Override public void addNotify() {
        super.addNotify();
        audio = audioFactory.get();
        synchronizeTimer();
    }

    @Override public void removeNotify() {
        timer.stop();
        closeAudio();
        input.clear();
        model.pause();
        loop.reset();
        super.removeNotify();
    }

    private void tick() {
        long now = System.nanoTime();
        loop.advance((now - lastTick) / 1_000_000_000.0, this::updateGame);
        lastTick = now;
        if (model.state != GameModel.State.RUNNING) synchronizeTimer();
        repaint();
    }

    void updateGame(double dt) {
        if (resetting) return;
        GameModel.State previous = model.state;
        int collectedBefore = collectedCount();
        GameProgress previousProgress = model.progress();
        model.update(dt);
        refreshResetButton();
        if (collectedCount() > collectedBefore
                || model.progress().collectedRings().size() > previousProgress.collectedRings().size()) {
            play(GameAudio.Effect.COLLECT);
        }
        if (model.progress() != previousProgress) progressChanged.accept(model.progress());
        if (previous == GameModel.State.RUNNING) {
            if (model.state == GameModel.State.LOST) play(GameAudio.Effect.LOSE);
            if (model.state == GameModel.State.WON) play(GameAudio.Effect.WIN);
        }
    }

    private int collectedCount() {
        int count = 0;
        for (boolean collected : model.collected) if (collected) count++;
        return count;
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        camera = Math.max(0, Math.min(model.x - CAMERA_ANCHOR, FINISH - WIDTH + FINISH_MARGIN));
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            drawBackground(g);
            drawWorld(g);
            drawHud(g);
            switch (model.state) {
                case READY -> overlay(g, "QUADRADO RUNNER", readyMessage(), "ESPAÇO ou ↑ para começar e pular");
                case PAUSED -> overlay(g, "PAUSA", "Respire. A pista espera por você.", "P ou ESC para continuar");
                case LOST -> overlay(g, "QUASE LÁ!", "Você bateu em um obstáculo. Tente pular um pouco antes.", "R para tentar novamente");
                case WON -> {
                    if (session.isShopOpen()) drawShop(g);
                    else drawLevelSummary(g);
                }
                default -> { }
            }
        } finally { g.dispose(); }
    }

    private void drawBackground(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, SKY_TOP, 0, GROUND, SKY_BOTTOM));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(STAR);
        for (int i = 0; i < 35; i++) {
            int sx = Math.floorMod(i * 137 - (int) (camera * 0.12), WIDTH + 30);
            int sy = 35 + (i * 73 % 240);
            g.fillOval(sx, sy, 2, 2);
        }
        g.setColor(BUILDING);
        for (int i = -1; i < 9; i++) {
            int bx = i * 180 - (int) (camera * 0.25) % 180;
            int h = 70 + Math.floorMod(i * 37, 110);
            g.fillRoundRect(bx, GROUND - h, 130, h + 20, 12, 12);
        }
    }

    private void drawWorld(Graphics2D g) {
        g.setColor(GROUND_COLOR);
        g.fillRect(0, GROUND, WIDTH, HEIGHT - GROUND);
        g.setColor(ACCENT);
        g.fillRect(0, GROUND, WIDTH, 3);
        g.setColor(GROUND_MARK);
        for (int i = 0; i < WIDTH / 48 + 2; i++) {
            int tx = i * 48 - (int) camera % 48;
            g.drawLine(tx, GROUND + 20, tx + 22, GROUND + 20);
        }
        AffineTransform original = g.getTransform();
        g.translate(-camera, 0);
        for (Shape obstacle : model.level.obstacles) {
            Rectangle2D bounds = obstacle.getBounds2D();
            if (bounds.getMaxX() < camera || bounds.getMinX() > camera + WIDTH) continue;
            g.setColor(OBSTACLE_FILL);
            g.fill(obstacle);
            g.setColor(OBSTACLE_EDGE);
            g.draw(obstacle);
        }
        for (int i = 0; i < model.level.powerUps.size(); i++) {
            if (model.collected[i]) continue;
            Rectangle2D.Double item = model.level.powerUps.get(i);
            int cx = (int) item.getCenterX();
            int cy = (int) item.getCenterY();
            g.setColor(CRYSTAL_GLOW);
            g.fillOval(cx - 23, cy - 23, 46, 46);
            g.setColor(CRYSTAL);
            g.fillPolygon(new int[]{cx, cx + 14, cx, cx - 14},
                new int[]{cy - 14, cy, cy + 14, cy}, 4);
            g.setColor(Color.WHITE);
            g.setFont(CRYSTAL_FONT);
            g.drawString("2", cx - 4, cy + 5);
        }
        drawRings(g);
        int fx = FINISH;
        g.setColor(INK);
        g.fillRect(fx, GROUND - 140, 4, 140);
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 6; col++) {
                g.setColor((row + col) % 2 == 0 ? INK : FLAG_DARK);
                g.fillRect(fx + 4 + col * 10, GROUND - 140 + row * 10, 10, 10);
            }
        }
        int px = (int) model.x;
        if (model.isGliding()) {
            g.setColor(GLIDE_COLOR);
            g.fillArc(px - 12, (int) model.y - 26, SIZE + 24, 30, 0, 180);
            g.drawLine(px - 12, (int) model.y - 11, px + SIZE / 2, (int) model.y);
            g.drawLine(px + SIZE + 12, (int) model.y - 11, px + SIZE / 2, (int) model.y);
        }
        if (model.isDashing()) {
            g.setColor(DASH_TRAIL);
            for (int i = 1; i <= 3; i++) {
                g.fillRoundRect(px - i * 15, (int) model.y + i * 3, SIZE, SIZE - i * 6, 7, 7);
            }
        }
        if (model.powerRemaining > 0) {
            g.setColor(PLAYER_GLOW);
            g.fillRoundRect(px - 5, (int) model.y - 5, SIZE + 10, SIZE + 10, 12, 12);
        }
        g.setColor(SHADOW);
        g.fillOval(px - 4, GROUND - 7, SIZE + 8, 12);
        g.setColor(model.isDashing() ? DASH_COLOR : model.isGliding() ? GLIDE_COLOR : ACCENT);
        g.fillRoundRect(px, (int) model.y, SIZE, SIZE, 7, 7);
        g.setColor(PLAYER_FACE);
        g.fillRect(px + 22, (int) model.y + 9, 5, 6);
        g.fillRect(px + 23, (int) model.y + 23, 8, 3);
        g.setTransform(original);
    }

    private void drawRings(Graphics2D g) {
        Stroke original = g.getStroke();
        g.setStroke(RING_STROKE);
        for (Ring ring : model.level.rings) {
            if (model.isRingCollected(ring) || ring.x() + Ring.DIAMETER < camera
                    || ring.x() > camera + WIDTH) continue;
            // Stroke stays inside the collectible outer disk.
            Ellipse2D outline = new Ellipse2D.Double(ring.x() + 2, ring.y() + 2,
                Ring.DIAMETER - 4, Ring.DIAMETER - 4);
            g.setColor(RING_GOLD);
            g.draw(outline);
            g.setColor(RING_HIGHLIGHT);
            g.draw(new Arc2D.Double(ring.x() + 2, ring.y() + 2,
                Ring.DIAMETER - 4, Ring.DIAMETER - 4, 60, 80, Arc2D.OPEN));
        }
        g.setStroke(original);
    }

    private void drawHud(Graphics2D g) {
        g.setColor(INK);
        g.setFont(TITLE_FONT);
        g.drawString("QUADRADO / RUNNER", 28, 36);
        g.setFont(CONTROLS_FONT);
        g.drawString("ESPAÇO / ↑  pular     D  dash     G  planar     P / ESC  pausar     R  reiniciar", 28, 511);
        g.drawString(muted ? "M  som: desligado" : "M  som: ligado", 785, 511);
        g.setColor(RING_GOLD);
        g.drawString("ARGOLAS COLETADAS  " + model.collectedRingCount() + "/" + model.level.rings.size(), 420, 32);
        g.drawString("SALDO  " + model.progress().balance(), 420, 55);
        g.setColor(INK);
        double progress = Math.max(0, Math.min(1, (model.x - GameModel.START_X) / (FINISH - GameModel.START_X)));
        g.drawString("CHEGADA  " + (int) (progress * 100) + "%", 720, 32);
        g.setColor(PROGRESS_TRACK);
        g.fillRoundRect(720, 43, 210, 7, 7, 7);
        g.setColor(ACCENT);
        g.fillRoundRect(720, 43, (int) (210 * progress), 7, 7, 7);
        g.setColor(CRYSTAL);
        g.setFont(POWER_FONT);
        g.drawString(model.powerRemaining > 0
            ? "SALTO DUPLO  " + (int) Math.ceil(model.powerRemaining) + "s  •  " + (2 - model.jumpsUsed) + " salto(s) disponível(is)"
            : "◆ Cristal azul = salto duplo por 10s", 28, 65);
        g.setColor(DASH_COLOR);
        String dashStatus;
        if (!model.progress().unlockedAbilities().contains(GameProgress.Ability.DASH)) {
            dashStatus = "DASH bloqueado • disponível na loja por " + AbilityShop.Item.DASH.price + " argolas";
        } else if (model.isDashing()) {
            dashStatus = "DASH ativo";
        } else if (model.dashCooldownRemaining() > 0) {
            dashStatus = "DASH recarregando: "
                + Math.ceil(model.dashCooldownRemaining() * 10 - TIMER_DISPLAY_EPSILON) / 10 + "s";
        } else {
            dashStatus = "D  DASH pronto";
        }
        g.drawString(dashStatus, 28, 90);
        g.setColor(GLIDE_COLOR);
        String glideStatus = !model.progress().unlockedAbilities().contains(GameProgress.Ability.GLIDE)
            ? "PLANAR bloqueado • loja: " + AbilityShop.Item.GLIDE.price + " argolas"
            : model.isGliding() ? "G  PLANAR ativo" : "Segure G durante a queda para planar";
        g.drawString(glideStatus, 520, 90);
    }

    private String readyMessage() {
        int total = model.level.rings.size();
        int collected = model.collectedRingCount();
        if (total > 0 && collected == total) {
            return "As " + total + " argolas desta fase já foram coletadas neste save.";
        }
        if (collected > 0) {
            return "Restam " + (total - collected) + " argola(s) nesta fase. As coletadas permanecem salvas.";
        }
        return "Pegue os cristais azuis: salto duplo por 10 segundos!";
    }

    private void overlay(Graphics2D g, String title, String subtitle, String hint) {
        g.setColor(OVERLAY_SHADE);
        g.fillRect(0, 65, WIDTH, 410);
        g.setColor(ACCENT);
        centered(g, levelLabel(), 182, OVERLAY_LABEL);
        g.setColor(INK);
        centered(g, title, 242, OVERLAY_TITLE);
        centered(g, subtitle, 283, OVERLAY_SUBTITLE);
        g.setColor(ACCENT);
        centered(g, hint, 339, OVERLAY_HINT);
    }

    private String levelLabel() {
        return "FASE " + session.levelNumber() + " / " + session.levelCount();
    }

    private void drawLevelSummary(Graphics2D g) {
        GameModel.LevelSummary summary = model.levelSummary();
        g.setColor(OVERLAY_SHADE);
        g.fillRect(0, 65, WIDTH, 410);
        g.setColor(ACCENT);
        centered(g, levelLabel(), 148, OVERLAY_LABEL);
        g.setColor(INK);
        centered(g, "FASE CONCLUÍDA!", 210, OVERLAY_TITLE);
        centered(g, "Argolas novas nesta tentativa: " + summary.newRings(), 267, OVERLAY_SUBTITLE);
        g.setColor(RING_GOLD);
        centered(g, "Saldo total: " + summary.totalBalance() + " argola(s)", 308, OVERLAY_HINT);
        g.setColor(ACCENT);
        centered(g, session.canAdvance() ? "R rejogar     B loja     ENTER avançar" : "R para rejogar     B para loja",
            365, OVERLAY_HINT);
        g.setColor(INK);
        centered(g, session.canAdvance() ? "Seu progresso acompanha você na próxima fase."
            : "Todas as fases disponíveis foram concluídas.", 410, OVERLAY_SUBTITLE);
    }

    private void drawShop(Graphics2D g) {
        g.setColor(OVERLAY_SHADE);
        g.fillRect(0, 65, WIDTH, 410);
        g.setColor(ACCENT);
        centered(g, levelLabel(), 112, OVERLAY_LABEL);
        g.setColor(INK);
        centered(g, "LOJA DE HABILIDADES", 164, OVERLAY_TITLE);
        g.setColor(RING_GOLD);
        centered(g, "Saldo: " + model.progress().balance() + " argola(s)", 203, OVERLAY_HINT);
        g.setColor(INK);
        centered(g, "Após comprar: D para dash; segure G durante a queda para planar.", 233, CONTROLS_FONT);
        int row = 0;
        for (AbilityShop.Item item : AbilityShop.Item.values()) {
            int y = 280 + row * 50;
            g.setFont(OVERLAY_HINT);
            g.setColor(INK);
            g.drawString((row + 1) + "  " + item.label, 180, y);
            g.setColor(RING_GOLD);
            g.drawString(item.price + " argolas", 440, y);
            boolean owned = model.progress().unlockedAbilities().contains(item.ability);
            boolean affordable = model.progress().balance() >= item.price;
            g.setColor(owned || affordable ? ACCENT : OBSTACLE_FILL);
            g.drawString(owned ? "Adquirido" : affordable ? "Disponível"
                : "Faltam " + (item.price - model.progress().balance()), 650, y);
            row++;
        }
        g.setColor(INK);
        centered(g, shopMessage, 380, OVERLAY_SUBTITLE);
        g.setColor(ACCENT);
        centered(g, "1 / 2 comprar     B / ESC voltar ao resumo", 420, OVERLAY_HINT);
    }

    private void centered(Graphics2D g, String text, int baseline, Font font) {
        g.setFont(font);
        g.drawString(text, (WIDTH - g.getFontMetrics().stringWidth(text)) / 2, baseline);
    }
}
