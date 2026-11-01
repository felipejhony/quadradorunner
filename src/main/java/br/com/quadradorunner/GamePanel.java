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
    private final JPanel shopChoices = new JPanel(new GridLayout(0, 1, 0, 10));
    private final JScrollPane shopScroll = new JScrollPane(shopChoices);
    private int selectedShopItem;
    private boolean choosingLevel;
    private int selectedLevel;
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
        shopChoices.setOpaque(false);
        shopChoices.setPreferredSize(new Dimension(610, AbilityShop.Item.values().length * 42 - 10));
        shopScroll.setBounds(160, 266, 640, 116);
        shopScroll.setBorder(null);
        shopScroll.getViewport().setBackground(GameUi.SURFACE);
        shopScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        shopScroll.getVerticalScrollBar().setUnitIncrement(42);
        shopScroll.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                thumbColor = GameUi.MINT;
                trackColor = GameUi.SURFACE;
            }
        });
        shopScroll.setVisible(false);
        shopChoices.setVisible(false);
        add(shopScroll);
        bindKey("F", GameInput.Key.LEVELS, this::toggleLevelMenu);
        bindKey("SPACE", GameInput.Key.SPACE, () -> {
            if (choosingLevel) { ((JButton) levelChoices.getComponent(selectedLevel)).doClick(); return; }
            if (session.isShopOpen()) { buy(AbilityShop.Item.values()[selectedShopItem]); return; }
            jump();
            model.setGlideHeld(true);
            if (model.isGliding()) play(GameAudio.Effect.GLIDE);
        }, () -> model.setGlideHeld(false));
        bindKey("UP", GameInput.Key.UP, () -> { if (choosingLevel || session.isShopOpen()) moveLevelSelection(-1); else jump(); });
        bindKey("DOWN", GameInput.Key.DOWN, () -> moveLevelSelection(1));
        bindKey("LEFT", GameInput.Key.LEFT, () -> moveLevelSelection(-1));
        bindKey("RIGHT", GameInput.Key.RIGHT, () -> moveLevelSelection(1));
        bindKey("D", GameInput.Key.DASH, () -> {
            if (model.dash()) play(GameAudio.Effect.DASH);
        });
        bindKey("S", GameInput.Key.DURABILITY, () -> {
            if (model.durability()) play(GameAudio.Effect.SHIELD);
        });
        bindKey("W", GameInput.Key.ROCKET, () -> {
            if (model.fireRocket()) play(GameAudio.Effect.ROCKET);
        });
        bindKey("R", GameInput.Key.RESTART, () -> { session.replay(); clearCollectionMessage(); });
        bindKey("ENTER", GameInput.Key.ADVANCE, () -> {
            if (choosingLevel) { ((JButton) levelChoices.getComponent(selectedLevel)).doClick(); return; }
            if (session.isShopOpen()) { buy(AbilityShop.Item.values()[selectedShopItem]); return; }
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
            else if (session.openShop()) { shopMessage = ""; selectedShopItem = 0; }
        });
        bindKey("1", GameInput.Key.BUY_DASH, () -> buy(AbilityShop.Item.DASH));
        bindKey("2", GameInput.Key.BUY_GLIDE, () -> buy(AbilityShop.Item.GLIDE));
        bindKey("3", GameInput.Key.BUY_DOUBLE_JUMP, () -> buy(AbilityShop.Item.DOUBLE_JUMP));
        bindKey("4", GameInput.Key.BUY_DURABILITY, () -> buy(AbilityShop.Item.DURABILITY));
        bindKey("5", GameInput.Key.BUY_ROCKET, () -> buy(AbilityShop.Item.ROCKET));
        bindKey("6", GameInput.Key.BUY_DASH_UPGRADE, () -> buy(AbilityShop.Item.DASH_UPGRADE));
        bindKey("7", GameInput.Key.BUY_DOUBLE_JUMP_UPGRADE, () -> buy(AbilityShop.Item.DOUBLE_JUMP_UPGRADE));
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
            shopChoices.setVisible(false);
            shopScroll.setVisible(false);
            levelChoices.removeAll();
            for (int i = 0; i < session.levelCount(); i++) {
                int target = i;
                Level level = session.levelAt(i);
                boolean completed = model.progress().completedLevelIds().contains(level.id);
                boolean unlocked = session.isLevelUnlocked(i);
                JButton choice = new GameButton((i + 1) + " — " + level.title + " — "
                    + (completed ? "Concluída · rejogar" : unlocked ? "Liberada · jogar" : "Bloqueada"), false);

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
            selectedLevel = session.levelNumber() - 1;
            highlightLevelSelection();
            levelChoices.setVisible(true);
            levelChoices.revalidate();
            levelChoices.doLayout();
            requestFocusInWindow();
        }
        synchronizeTimer();
        repaint();
    }

    private void moveLevelSelection(int direction) {
        if (session.isShopOpen() && !choosingLevel) {
            selectedShopItem = Math.floorMod(selectedShopItem + direction, AbilityShop.Item.values().length);
            refreshShopChoices();
            return;
        }
        if (!choosingLevel) return;
        for (int i = 0; i < session.levelCount(); i++) {
            selectedLevel = Math.floorMod(selectedLevel + direction, session.levelCount());
            if (session.isLevelUnlocked(selectedLevel)) break;
        }
        highlightLevelSelection();
    }

    private void highlightLevelSelection() {
        for (int i = 0; i < levelChoices.getComponentCount(); i++) {
            ((GameButton) levelChoices.getComponent(i)).setKeyboardSelected(i == selectedLevel);
        }
    }

    private void closeLevelMenu() {
        choosingLevel = false;
        levelChoices.setVisible(false);
        refreshShopChoices();
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

    private void refreshShopChoices() {
        shopChoices.setVisible(session.isShopOpen() && !choosingLevel && !resetting);
        shopScroll.setVisible(shopChoices.isVisible());
        if (!shopChoices.isVisible()) return;
        shopChoices.removeAll();
        for (AbilityShop.Item item : AbilityShop.Item.values()) {
            int price = AbilityShop.price(model.progress(), item);
            boolean owned = model.progress().unlockedAbilities().contains(item.ability);
            boolean affordable = model.progress().balance() >= price;
            boolean baseOwned = item.ability.prerequisite() == null
                || model.progress().unlockedAbilities().contains(item.ability.prerequisite());
            String status = owned ? "Adquirido" : !baseOwned ? "Requer poder base"
                : affordable ? "Comprar" : "Faltam " + (price - model.progress().balance());
            GameButton choice = new GameButton((item.ordinal() + 1) + " — " + item.label
                + " — " + price + " argolas — " + status, false);
            choice.setFocusable(false);
            choice.setEnabled(!owned && affordable && baseOwned);
            choice.setKeyboardSelected(item.ordinal() == selectedShopItem);
            choice.addActionListener(event -> buy(item));
            shopChoices.add(choice);
        }
        shopChoices.revalidate();
        shopScroll.doLayout();
        shopScroll.getViewport().doLayout();
        shopChoices.doLayout();
        shopChoices.scrollRectToVisible(shopChoices.getComponent(selectedShopItem).getBounds());
        shopChoices.repaint();
    }

    private void refreshResetButton() {
        refreshShopChoices();
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
        selectedShopItem = item.ordinal();
        AbilityShop.Status status = session.buy(item);
        shopMessage = switch (status) {
            case PURCHASED -> item.label + " desbloqueado permanentemente!";
            case ALREADY_OWNED -> "Você já possui " + item.label + ". Nenhuma argola foi gasta.";
            case INSUFFICIENT_FUNDS -> "Saldo insuficiente para comprar " + item.label + ".";
            case REQUIRES_BASE -> "Adquira o poder base antes da melhoria.";
            case UNAVAILABLE -> "";
        };
        if (status == AbilityShop.Status.PURCHASED) {
            progressChanged.accept(model.progress());
            play(GameAudio.Effect.PURCHASE);
        } else if (status != AbilityShop.Status.UNAVAILABLE) play(GameAudio.Effect.DENIED);
        refreshShopChoices();
    }

    private void play(GameAudio.Effect effect) {
        if (audio != null) audio.play(effect);
    }

    private void configureAudio() {
        if (audio != null) audio.configure(muted, !windowFocused || model.state == GameModel.State.PAUSED,
            model.level.id.equals("level-04") ? GameAudio.Music.POOLS
                : model.level.id.equals("level-03") ? GameAudio.Music.SPACE
                : model.level.id.equals("level-02") ? GameAudio.Music.SUNRISE : GameAudio.Music.NIGHT);
    }

    private void bindKey(String stroke, GameInput.Key key, Runnable command) {
        bindKey(stroke, key, command, () -> { });
    }

    private void bindKey(String stroke, GameInput.Key key, Runnable command, Runnable released) {
        bind("pressed " + stroke, () -> {
            if (resetting) return;
            if (choosingLevel && key != GameInput.Key.LEVELS && key != GameInput.Key.ESCAPE
                && key != GameInput.Key.MUTE && key != GameInput.Key.UP && key != GameInput.Key.DOWN
                && key != GameInput.Key.LEFT && key != GameInput.Key.RIGHT && key != GameInput.Key.ADVANCE
                && key != GameInput.Key.SPACE) return;
            if (input.press(key)) {
                command.run();
                refreshShopChoices();
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
        int previousLives = model.lives;
        session.update(dt);
        if (model != session.current()) {
            model = session.current();
            clearCollectionMessage();
            progressChanged.accept(model.progress());
            play(GameAudio.Effect.LOSE);
            refreshResetButton();
            return;
        }
        if (model.lives < previousLives) play(GameAudio.Effect.LOSE);
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
        if (model.level.id.equals("level-04")) {
            PoolScenery.draw(g, camera);
            return;
        }
        boolean sunrise = model.level.id.equals("level-02");
        g.setPaint(new GradientPaint(0, 0, sunrise ? new Color(129, 65, 96) : SKY_TOP,
            0, GROUND, sunrise ? new Color(255, 177, 92) : SKY_BOTTOM));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(STAR);
        for (int i = 0; i < (sunrise ? 0 : 35); i++) {
            int sx = Math.floorMod(i * 137 - (int) (camera * 0.12), WIDTH + 30);
            int sy = 35 + (i * 73 % 240);
            g.fillOval(sx, sy, 2, 2);
        }
        if (sunrise) {
            g.setColor(new Color(255, 217, 137));
            g.fillOval(680, 260, 150, 150);
        }
        if (model.level.id.equals("level-03")) drawUfos(g);
        g.setColor(sunrise ? new Color(123, 78, 87) : BUILDING);
        for (int i = -1; i < 9; i++) {
            int bx = i * 180 - (int) (camera * 0.25) % 180;
            int h = 70 + Math.floorMod(i * 37, 110);
            g.fillRoundRect(bx, GROUND - h, 130, h + 20, 12, 12);
        }
    }

    private void drawUfos(Graphics2D g) {
        for (int i = 0; i < 4; i++) {
            int x = Math.floorMod(150 + i * 265 - (int) (camera * 0.18), WIDTH + 140) - 70;
            int y = 150 + i % 3 * 32;
            g.setColor(new Color(113, 235, 214, 28));
            g.fillOval(x - 12, y + 4, 112, 36);
            g.setColor(new Color(113, 235, 214));
            g.fillOval(x + 25, y - 14, 38, 30);
            g.setColor(new Color(180, 195, 220));
            g.fillOval(x, y, 88, 24);
            g.setColor(new Color(72, 87, 122));
            g.fillOval(x + 7, y + 9, 74, 12);
            g.setColor(new Color(255, 223, 130));
            for (int light = 0; light < 4; light++) g.fillOval(x + 17 + light * 16, y + 10, 5, 4);
        }
    }

    private void drawWorld(Graphics2D g) {
        boolean pools = model.level.id.equals("level-04");
        g.setColor(pools ? PoolScenery.TILE : GROUND_COLOR);
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
        for (Level.Pit pit : model.level.pits) {
            g.setColor(pools ? PoolScenery.WATER : new Color(12, 18, 30));
            g.fillRect(pit.x(), GROUND, pit.width(), HEIGHT - GROUND);
            g.setColor(Color.WHITE);
            g.drawLine(pit.x(), GROUND, pit.x(), HEIGHT);
            g.drawLine(pit.end(), GROUND, pit.end(), HEIGHT);
        }
        drawAbilityGates(g);
        for (Shape baseObstacle : model.level.obstacles) {
            if (model.isDestroyed(baseObstacle)) continue;
            if (baseObstacle instanceof CourseObstacle course) {
                double elapsed = model.obstacleElapsed(course);
                g.setColor(ACCENT);
                g.fill(course.harmlessShape(elapsed));
                if (course.kind == CourseObstacle.Kind.FALLING && elapsed >= 0 && elapsed < 0.65) {
                    g.setColor(new Color(255, 100, 90, 75));
                    g.fill(new Rectangle2D.Double(course.x, 120, course.width, GROUND - 120));
                }
            }
            Shape obstacle = model.obstacleShape(baseObstacle);
            Rectangle2D bounds = obstacle.getBounds2D();
            if (bounds.getMaxX() < camera || bounds.getMinX() > camera + WIDTH) continue;
            g.setColor(model.level.id.equals("level-02") ? new Color(49, 54, 160) : OBSTACLE_FILL);
            g.fill(obstacle);
            g.setColor(Color.WHITE);
            java.awt.Stroke previousStroke = g.getStroke();
            g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(obstacle);
            g.setStroke(previousStroke);
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
        GameModel.Projectile projectile = model.projectile();
        if (projectile != null) {
            g.setColor(new Color(255, 172, 72));
            g.fillOval((int) projectile.x() - 16, (int) projectile.y() - 4, 18, 8);
            g.setColor(INK);
            g.fillOval((int) projectile.x() - 5, (int) projectile.y() - 5, 14, 10);
        }
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
        if (model.isDurable()) {
            g.setColor(new Color(120, 210, 255, 75));
            g.fillOval(px - 12, (int) model.y - 12, SIZE + 24, SIZE + 24);
            g.setColor(new Color(155, 230, 255));
            g.setStroke(new BasicStroke(3));
            g.drawOval(px - 9, (int) model.y - 9, SIZE + 18, SIZE + 18);
            g.setStroke(new BasicStroke(1));
        }
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
        for (Ring baseRing : model.level.rings) {
            Ring ring = model.movingRing(baseRing);
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
        }
    }

    private void drawAbilityIcons(Graphics2D g) {
        int x = 20;
        for (GameProgress.Ability ability : GameProgress.Ability.values()) {
            if (ability.prerequisite() != null) continue;
            if (!model.progress().unlockedAbilities().contains(ability)) continue;
            hudCard(g, x, 86, 34, 32);
            g.setColor(ability == GameProgress.Ability.DASH ? DASH_COLOR
                : ability == GameProgress.Ability.GLIDE ? GLIDE_COLOR : CRYSTAL);
            g.setStroke(new BasicStroke(2));
            switch (ability) {
                case DASH -> { // Lightning bolt.
                    Path2D bolt = new Path2D.Double();
                    bolt.moveTo(x + 20, 91); bolt.lineTo(x + 10, 103);
                    bolt.lineTo(x + 17, 103); bolt.lineTo(x + 13, 113);
                    bolt.lineTo(x + 25, 99); bolt.lineTo(x + 18, 99);
                    bolt.closePath(); g.fill(bolt);
                }
                case GLIDE -> { // Open wings.
                    g.drawLine(x + 6, 97, x + 17, 108);
                    g.drawLine(x + 17, 108, x + 28, 97);
                    g.drawLine(x + 6, 97, x + 17, 101);
                    g.drawLine(x + 17, 101, x + 28, 97);
                }
                case DOUBLE_JUMP -> {
                    for (int y : new int[]{100, 108}) {
                        g.drawLine(x + 9, y, x + 17, y - 6);
                        g.drawLine(x + 17, y - 6, x + 25, y);
                    }
                }
                case DURABILITY -> {
                    g.drawArc(x + 8, 92, 18, 21, 180, 180);
                    g.drawLine(x + 8, 102, x + 8, 92);
                    g.drawLine(x + 26, 102, x + 26, 92);
                    g.drawLine(x + 8, 92, x + 26, 92);
                }
                case ROCKET -> {
                    g.fillOval(x + 8, 98, 20, 9);
                    g.drawLine(x + 5, 102, x + 8, 102);
                }
                default -> { }
            }
            double cooldown = switch (ability) {
                case DASH -> model.dashCooldownRemaining();
                case DOUBLE_JUMP -> model.doubleJumpCooldownRemaining();
                case DURABILITY -> model.durabilityCooldownRemaining();
                case ROCKET -> model.rocketCooldownRemaining();
                default -> 0;
            };
            if (cooldown > 0) {
                g.setColor(INK);
                g.setFont(GameUi.bold(11));
                g.drawString(Integer.toString((int) Math.ceil(cooldown)), x + 12, 132);
            }
            x += 42;
        }
        g.setStroke(new BasicStroke(1));
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
        drawAbilityIcons(g);
        hudCard(g, 788, 86, 152, 36);
        for (int i = 0; i < GameModel.MAX_LIVES; i++) {
            int hx = 806 + i * 42;
            Path2D heart = new Path2D.Double();
            heart.moveTo(hx, 99);
            heart.curveTo(hx - 10, 87, hx - 19, 102, hx, 115);
            heart.curveTo(hx + 19, 102, hx + 10, 87, hx, 99);
            g.setColor(i < model.lives ? new Color(235, 65, 92) : GameUi.MUTED);
            if (i < model.lives) g.fill(heart);
            else g.draw(heart);
        }

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
        g.drawString("ESPAÇO/CIMA pular | ESPAÇO planar | D dash | S escudo | W foguete | P pausa | R reiniciar", 28, 518);
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
        return model.level.title;
    }

    private String routeInstruction() {
        return model.level.title;
    }

    private String lossMessage() {
        return "Tentativa encerrada.";
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
        return "Seu progresso acompanha você na próxima fase.";
    }

    private void drawShop(Graphics2D g) {
        g.setColor(OVERLAY_SHADE);
        g.fillRoundRect(60, 138, 840, 294, 20, 20);
        g.setColor(ACCENT);
        centered(g, "PODERES: +3 POR COMPRA · MELHORIAS: PREÇO FIXO", 162, OVERLAY_LABEL);
        g.setColor(INK);
        centered(g, "LOJA DE HABILIDADES", 202, OVERLAY_TITLE);
        g.setColor(RING_GOLD);
        centered(g, "Saldo: " + model.progress().balance() + " argola(s)", 232, OVERLAY_HINT);
        g.setColor(INK);
        centered(g, "D: Dash · S: Durabilidade · W: Foguete · ESPAÇO: pular / planar", 254, CONTROLS_FONT);
        g.setColor(INK);
        centered(g, shopMessage, 394, OVERLAY_SUBTITLE);
        g.setColor(ACCENT);
        centered(g, "Setas / rolagem: escolher   ENTER / ESPAÇO: comprar   B / ESC: voltar", 420, OVERLAY_HINT);
    }

    private void centered(Graphics2D g, String text, int baseline, Font font) {
        g.setFont(font);
        int width = g.getFontMetrics().stringWidth(text);
        if (width > WIDTH - 150) g.setFont(font.deriveFont(font.getSize2D() * (WIDTH - 150) / width));
        g.drawString(text, (WIDTH - g.getFontMetrics().stringWidth(text)) / 2, baseline);
    }
}
