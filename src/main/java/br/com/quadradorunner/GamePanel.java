package br.com.quadradorunner;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.*;
import javax.swing.*;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.Arrays;
import java.util.stream.Collectors;
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
    private static final Font CRYSTAL_FONT = GameUi.bold(13);
    private static final Font TITLE_FONT = GameUi.bold(18);
    private static final Font CONTROLS_FONT = GameUi.regular(13);
    private static final Font POWER_FONT = GameUi.bold(14);
    private static final Font OVERLAY_LABEL = GameUi.bold(13);
    private static final Font OVERLAY_TITLE = GameUi.bold(38);
    private static final Font OVERLAY_SUBTITLE = GameUi.regular(16);
    private static final Font OVERLAY_HINT = GameUi.bold(17);
    private static final Font METRIC_FONT = GameUi.bold(22);
    private GameModel model;
    private final GameSession session;
    private final Supplier<GameAudio> audioFactory;
    private final Consumer<GameProgress> progressChanged;
    private final JButton resetButton = new GameButton("Zerar progresso", true);
    private final JButton levelsButton = new GameButton("F  /  Escolher fase", false);
    private final JPanel levelChoices = new JPanel(new GridLayout(0, 1, 0, 10));
    private boolean choosingLevel;
    private boolean resetting;
    private GameAudio audio;
    private boolean muted;
    private boolean windowFocused = true;
    private String shopMessage = "";
    private String collectionMessage = "";
    private double collectionMessageRemaining;
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
        resetButton.setBounds(714, 448, 226, 38);
        resetButton.setFocusable(false);
        resetButton.setToolTipText("Apaga saldo, argolas, habilidades e conclusões após confirmar. R reinicia só a tentativa.");
        resetButton.getAccessibleContext().setAccessibleDescription("Reset completo e permanente; exige confirmação.");
        resetButton.addActionListener(event -> resetRequested.accept(this));
        add(resetButton);
        levelsButton.setBounds(20, 448, 200, 38);
        levelsButton.setFocusable(false);
        levelsButton.addActionListener(event -> toggleLevelMenu());
        add(levelsButton);
        levelChoices.setOpaque(false);
        levelChoices.setBounds(160, 210, 640, 185);
        levelChoices.setVisible(false);
        add(levelChoices);
        bindKey("F", GameInput.Key.LEVELS, this::toggleLevelMenu);
        bindKey("SPACE", GameInput.Key.SPACE, this::jump);
        bindKey("UP", GameInput.Key.UP, this::jump);
        bindKey("D", GameInput.Key.DASH, () -> {
            if (model.dash()) play(GameAudio.Effect.DASH);
        });
        bindKey("G", GameInput.Key.GLIDE, () -> {
            model.setGlideHeld(true);
            if (model.isGliding()) play(GameAudio.Effect.GLIDE);
        }, () -> model.setGlideHeld(false));
        bindKey("R", GameInput.Key.RESTART, () -> { session.replay(); clearCollectionMessage(); });
        bindKey("ENTER", GameInput.Key.ADVANCE, () -> {
            if (session.advance()) { model = session.current(); clearCollectionMessage(); }
        });
        bindKey("P", GameInput.Key.PAUSE, () -> model.togglePause());
        bindKey("ESCAPE", GameInput.Key.ESCAPE, () -> {
            if (choosingLevel) closeLevelMenu();
            else if (session.isShopOpen()) session.closeShop();
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
        return ResetConfirmation.show(this);
    }

    private void toggleLevelMenu() {
        if (resetting) return;
        if (choosingLevel) closeLevelMenu();
        else {
            model.pause();
            choosingLevel = true;
            levelChoices.removeAll();
            for (int i = 0; i < session.levelCount(); i++) {
                int target = i;
                Level level = session.levelAt(i);
                boolean completed = model.progress().completedLevelIds().contains(level.id);
                boolean unlocked = session.isLevelUnlocked(i);
                JButton choice = new GameButton((i + 1) + " — " + level.title + " — "
                    + (completed ? "Concluída · rejogar" : unlocked ? "Liberada · jogar" : "Bloqueada"), false);
                choice.setToolTipText(unlocked ? "Iniciar uma nova tentativa preservando seu progresso."
                    : "Conclua as fases anteriores e compre a habilidade exigida na loja (B após vencer).");
                choice.setFocusable(false);
                choice.setEnabled(unlocked);
                choice.addActionListener(event -> {
                    if (!resetting && session.selectLevel(target)) {
                        model = session.current();
                        clearCollectionMessage();
                        shopMessage = "";
                        closeLevelMenu();
                        synchronizeTimer();
                        repaint();
                    }
                });
                levelChoices.add(choice);
            }
            levelChoices.setVisible(true);
            levelChoices.revalidate();
        }
        synchronizeTimer();
        repaint();
    }

    private void closeLevelMenu() {
        choosingLevel = false;
        levelChoices.setVisible(false);
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
        closeLevelMenu();
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
            clearCollectionMessage();
        }
        resetting = false;
        input.clear();
        synchronizeTimer();
        repaint();
    }

    private void refreshResetButton() {
        levelsButton.setVisible(model.state != GameModel.State.RUNNING);
        levelsButton.setEnabled(!resetting);
        resetButton.setVisible(resetting || model.state != GameModel.State.RUNNING);
        resetButton.setEnabled(!resetting);
        resetButton.setText(resetting ? "Salvando reset..." : "Zerar progresso");
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
        if (status == AbilityShop.Status.PURCHASED) {
            progressChanged.accept(model.progress());
            play(GameAudio.Effect.PURCHASE);
        } else if (status != AbilityShop.Status.UNAVAILABLE) play(GameAudio.Effect.DENIED);
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
            if (choosingLevel && key != GameInput.Key.LEVELS && key != GameInput.Key.ESCAPE
                && key != GameInput.Key.MUTE) return;
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
        boolean glidingBefore = model.isGliding();
        int collectedBefore = collectedCount();
        GameProgress previousProgress = model.progress();
        model.update(dt);
        if (previous == GameModel.State.RUNNING) {
            collectionMessageRemaining = Math.max(0, collectionMessageRemaining - dt);
        }
        if (!glidingBefore && model.isGliding()) play(GameAudio.Effect.GLIDE);
        refreshResetButton();
        if (collectedCount() > collectedBefore
                || model.progress().collectedRings().size() > previousProgress.collectedRings().size()) {
            play(GameAudio.Effect.COLLECT);
            int newRings = model.progress().collectedRings().size() - previousProgress.collectedRings().size();
            collectionMessage = newRings > 0 ? "+" + newRings + " ARGOLA" : "SALTO DUPLO / 10s";
            collectionMessageRemaining = 1;
        }
        if (model.progress() != previousProgress) progressChanged.accept(model.progress());
        if (previous == GameModel.State.RUNNING) {
            if (model.state == GameModel.State.LOST) play(GameAudio.Effect.LOSE);
            if (model.state == GameModel.State.WON) play(GameAudio.Effect.WIN);
        }
    }

    private void clearCollectionMessage() {
        collectionMessage = "";
        collectionMessageRemaining = 0;
    }

    private int collectedCount() {
        int count = 0;
        for (boolean collected : model.collected) if (collected) count++;
        return count;
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        camera = Math.max(0, Math.min(model.x - CAMERA_ANCHOR, model.level.finish - WIDTH + FINISH_MARGIN));
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            drawBackground(g);
            drawWorld(g);
            drawHud(g);
            if (collectionMessageRemaining > 0 && model.state == GameModel.State.RUNNING) {
                g.setFont(POWER_FONT);
                g.setColor(RING_GOLD);
                g.drawString(collectionMessage, (int) (model.x - camera),
                    Math.max(155, (int) model.y - 16));
            }
            if (choosingLevel) {
                g.setColor(OVERLAY_SHADE);
                g.fillRoundRect(100, 138, 760, 294, 20, 20);
                g.setColor(INK);
                centered(g, "ESCOLHER FASE", 174, OVERLAY_TITLE);
                centered(g, "Saldo, compras e argolas coletadas serão preservados.", 193, CONTROLS_FONT);
                centered(g, "F ou ESC para voltar · escolher reinicia a tentativa", 419, CONTROLS_FONT);
                return;
            }
            switch (model.state) {
                case READY -> {
                    overlay(g, "QUADRADO RUNNER", readyMessage(), "ESPAÇO ou CIMA para começar e pular");
                    if (!model.level.gates.isEmpty()) {
                        g.setColor(INK);
                        centered(g, routeInstruction(), 390, CONTROLS_FONT);
                    }
                }
                case PAUSED -> overlay(g, "PAUSA", "Respire. A pista espera por você.", "P ou ESC para continuar");
                case LOST -> overlay(g, "QUASE LÁ!", lossMessage(), "R para tentar novamente");
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
        drawAbilityGates(g);
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
        int fx = model.level.finish;
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

    private void drawAbilityGates(Graphics2D g) {
        for (AbilityGate gate : model.level.gates) {
            if (gate.x() + gate.width() < camera || gate.x() > camera + WIDTH + 100) continue;
            boolean dash = gate.ability() == GameProgress.Ability.DASH;
            g.setColor(dash ? DASH_TRAIL : CRYSTAL_GLOW);
            g.fillRect(gate.x(), 0, gate.width(), GROUND);
            g.setColor(dash ? DASH_COLOR : GLIDE_COLOR);
            g.drawLine(gate.x(), 0, gate.x(), GROUND);
            g.drawLine(gate.x() + gate.width(), 0, gate.x() + gate.width(), GROUND);
            g.setFont(POWER_FONT);
            g.drawString(dash ? "BARREIRA DASH" : "CORRENTE AÉREA", gate.x() - 65, 155);
            g.setFont(CONTROLS_FONT);
            g.drawString(dash ? "D durante a travessia" : "G durante a descida", gate.x() - 65, 175);
        }
    }

    private void drawHud(Graphics2D g) {
        hudCard(g, 20, 12, 350, 66);
        hudCard(g, 382, 12, 166, 66);
        hudCard(g, 560, 12, 170, 66);
        hudCard(g, 742, 12, 198, 66);
        g.setColor(INK);
        g.setFont(TITLE_FONT);
        g.drawString("QUADRADO / RUNNER", 36, 39);
        g.setFont(CONTROLS_FONT);
        g.setColor(GameUi.MUTED);
        g.drawString(levelLabel(), 36, 61);
        g.setColor(RING_GOLD);
        g.drawString("SALDO", 398, 32);
        g.setFont(METRIC_FONT);
        g.drawString(Integer.toString(model.progress().balance()), 398, 61);
        g.setFont(CONTROLS_FONT);
        g.drawString("ARGOLAS DA FASE", 576, 32);
        g.setFont(METRIC_FONT);
        g.drawString(model.collectedRingCount() + " / " + model.level.rings.size(), 576, 61);
        g.setFont(CONTROLS_FONT);
        g.setColor(GameUi.MUTED);
        double progress = Math.max(0, Math.min(1, (model.x - GameModel.START_X) / (model.level.finish - GameModel.START_X)));
        g.drawString("CHEGADA  " + (int) (progress * 100) + "%", 758, 36);
        g.setColor(PROGRESS_TRACK);
        g.fillRoundRect(758, 51, 166, 7, 7, 7);
        g.setColor(ACCENT);
        g.fillRoundRect(758, 51, (int) (166 * progress), 7, 7, 7);
        hudCard(g, 20, 86, 300, 38);
        hudCard(g, 330, 86, 300, 38);
        hudCard(g, 640, 86, 300, 38);
        g.setColor(CRYSTAL);
        g.setFont(CONTROLS_FONT);
        g.drawString(model.powerRemaining > 0
            ? "2X  /  " + (int) Math.ceil(model.powerRemaining) + "s  /  " + (2 - model.jumpsUsed) + " salto(s) restante(s)"
            : "2X  /  Cristal azul: salto duplo por 10s", 34, 110);
        g.setColor(DASH_COLOR);
        String dashStatus;
        if (!model.progress().unlockedAbilities().contains(GameProgress.Ability.DASH)) {
            dashStatus = "D  /  DASH bloqueado · loja: " + AbilityShop.Item.DASH.price + " argolas";
        } else if (model.isDashing()) {
            dashStatus = "D  /  DASH ativo";
        } else if (model.dashCooldownRemaining() > 0) {
            dashStatus = "D  /  DASH recarga: "
                + Math.ceil(model.dashCooldownRemaining() * 10 - TIMER_DISPLAY_EPSILON) / 10 + "s";
        } else {
            dashStatus = "D  /  DASH pronto";
        }
        g.drawString(dashStatus, 344, 110);
        g.setColor(GLIDE_COLOR);
        String glideStatus = !model.progress().unlockedAbilities().contains(GameProgress.Ability.GLIDE)
            ? "G  /  PLANAR bloqueado · loja: " + AbilityShop.Item.GLIDE.price + " argolas"
            : model.isGliding() ? "G  /  PLANAR ativo" : "G  /  Segure na queda para planar";
        g.drawString(glideStatus, 654, 110);

        g.setColor(GameUi.SURFACE);
        g.fillRect(0, 438, WIDTH, HEIGHT - 438);
        g.setColor(GameUi.EDGE);
        g.drawLine(20, 438, 940, 438);
        g.setFont(CONTROLS_FONT);
        g.setColor(GameUi.MUTED);
        if (model.state == GameModel.State.RUNNING) {
            g.drawString("F  /  Escolher fase (pausa a corrida)", 28, 474);
        } else {
            centered(g, "R repete a tentativa · progresso preservado", 473, CONTROLS_FONT);
        }
        g.setColor(INK);
        g.drawString("ESPAÇO / CIMA  pular     D  dash     G  planar     P / ESC  pausar     R  reiniciar", 28, 518);
        g.setColor(muted ? OBSTACLE_FILL : ACCENT);
        g.drawString(muted ? "M  /  SOM OFF" : "M  /  SOM ON", 810, 518);
    }

    private void hudCard(Graphics2D g, int x, int y, int width, int height) {
        g.setColor(GameUi.SURFACE);
        g.fillRoundRect(x, y, width, height, 12, 12);
        g.setColor(GameUi.EDGE);
        g.drawRoundRect(x, y, width, height, 12, 12);
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
        if (!model.level.gates.isEmpty()) return "Prepare-se para o desafio de " + model.level.title + ".";
        return "Pegue os cristais azuis: salto duplo por 10 segundos!";
    }

    private String routeInstruction() {
        return model.level.requiredAbilities.contains(GameProgress.Ability.DASH)
            ? "Ative D pouco antes da barreira roxa e atravesse durante o impulso."
            : "Pule antes da corrente azul e entre nela já descendo, mantendo G pressionada.";
    }

    private String lossMessage() {
        if (model.blockedGate() == GameProgress.Ability.DASH) {
            return "Atravesse a barreira roxa enquanto o dash estiver ativo.";
        }
        if (model.blockedGate() == GameProgress.Ability.GLIDE) {
            return "Entre na corrente já descendo e segure G para planar.";
        }
        return "Você bateu em um obstáculo. Tente pular um pouco antes.";
    }

    private void overlay(Graphics2D g, String title, String subtitle, String hint) {
        g.setColor(OVERLAY_SHADE);
        g.fillRoundRect(60, 138, 840, 294, 20, 20);
        g.setColor(ACCENT);
        centered(g, levelLabel(), 182, OVERLAY_LABEL);
        g.setColor(INK);
        centered(g, title, 242, OVERLAY_TITLE);
        centered(g, subtitle, 283, OVERLAY_SUBTITLE);
        g.setColor(ACCENT);
        centered(g, hint, 339, OVERLAY_HINT);
    }

    private String levelLabel() {
        return "FASE " + session.levelNumber() + " / " + session.levelCount() + " • " + model.level.title;
    }

    private void drawLevelSummary(Graphics2D g) {
        GameModel.LevelSummary summary = model.levelSummary();
        g.setColor(OVERLAY_SHADE);
        g.fillRoundRect(60, 138, 840, 294, 20, 20);
        g.setColor(ACCENT);
        centered(g, levelLabel(), 165, OVERLAY_LABEL);
        g.setColor(INK);
        centered(g, "FASE CONCLUÍDA!", 210, OVERLAY_TITLE);
        centered(g, "Argolas novas nesta tentativa: " + summary.newRings(), 267, OVERLAY_SUBTITLE);
        g.setColor(RING_GOLD);
        centered(g, "Saldo total: " + summary.totalBalance() + " argola(s)", 308, OVERLAY_HINT);
        g.setColor(ACCENT);
        centered(g, session.canAdvance() ? "R rejogar     B loja     ENTER avançar" : "R para rejogar     B para loja",
            365, OVERLAY_HINT);
        g.setColor(INK);
        centered(g, nextLevelMessage(), 410, OVERLAY_SUBTITLE);
    }

    private String nextLevelMessage() {
        if (!session.hasNextLevel()) return "Todas as fases disponíveis foram concluídas.";
        var missing = session.missingNextAbilities();
        if (!missing.isEmpty()) {
            String required = Arrays.stream(AbilityShop.Item.values())
                .filter(item -> missing.contains(item.ability))
                .map(item -> item.label + " (" + item.price + " argolas)")
                .collect(Collectors.joining(" e "));
            return "Próxima fase exige " + required + ". Compre na loja com B.";
        }
        return "Seu progresso acompanha você na próxima fase.";
    }

    private void drawShop(Graphics2D g) {
        g.setColor(OVERLAY_SHADE);
        g.fillRoundRect(60, 138, 840, 294, 20, 20);
        g.setColor(ACCENT);
        centered(g, "MELHORIAS PERMANENTES", 162, OVERLAY_LABEL);
        g.setColor(INK);
        centered(g, "LOJA DE HABILIDADES", 202, OVERLAY_TITLE);
        g.setColor(RING_GOLD);
        centered(g, "Saldo: " + model.progress().balance() + " argola(s)", 232, OVERLAY_HINT);
        g.setColor(INK);
        centered(g, "Após comprar: D para dash; segure G durante a queda para planar.", 254, CONTROLS_FONT);
        int row = 0;
        for (AbilityShop.Item item : AbilityShop.Item.values()) {
            int y = 292 + row * 44;
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
        int width = g.getFontMetrics().stringWidth(text);
        if (width > WIDTH - 150) g.setFont(font.deriveFont(font.getSize2D() * (WIDTH - 150) / width));
        g.drawString(text, (WIDTH - g.getFontMetrics().stringWidth(text)) / 2, baseline);
    }
}
