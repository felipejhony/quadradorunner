package br.com.quadradorunner;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PresentationTest {
    @Test void bundledFontsSupportPortugueseAndShipTheirLicense() throws Exception {
        for (var font : List.of(GameUi.regular(14), GameUi.bold(38))) {
            assertEquals("Oxanium", font.getFamily());
            assertEquals(-1, font.canDisplayUpTo("AÇÃO CONCLUÍDA · — ç ã ê ó / 0123456789"));
        }
        try (var input = GameUi.class.getResourceAsStream("/fonts/OFL-Oxanium.txt")) {
            assertNotNull(input);
            String license = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(license.contains("SIL OPEN FONT LICENSE Version 1.1"));
            assertTrue(license.contains("Copyright 2019"));
        }
    }

    @Test void gameButtonsKeepActionsAndVisibleHoverPressedAndDisabledStates() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameButton button = new GameButton("Zerar progresso", true);
            button.setSize(226, 38);
            int[] clicks = {0};
            button.addActionListener(event -> clicks[0]++);
            BufferedImage normal = renderButton(button);
            button.getModel().setRollover(true);
            BufferedImage hover = renderButton(button);
            assertNotEquals(normal.getRGB(15, 15), hover.getRGB(15, 15));
            button.getModel().setArmed(true);
            button.getModel().setPressed(true);
            BufferedImage pressed = renderButton(button);
            assertNotEquals(hover.getRGB(15, 15), pressed.getRGB(15, 15));
            button.getModel().setArmed(false);
            button.getModel().setPressed(false);
            button.doClick(0);
            assertEquals(1, clicks[0]);
            button.setEnabled(false);
            button.doClick(0);
            assertEquals(1, clicks[0]);
            assertNotEquals(hover.getRGB(15, 15), renderButton(button).getRGB(15, 15));
            assertEquals("Zerar progresso", button.getAccessibleContext().getAccessibleName());
            assertTrue(button.getFontMetrics(button.getFont()).stringWidth(button.getText()) < button.getWidth() - 28);
        });
    }

    @Test void hudShowsZeroHalfAndFullProgressAndSupportsMaximumBalance() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameModel model = new GameModel(Level.standard(), new GameProgress(Integer.MAX_VALUE,
                Set.of(), Set.of(GameProgress.Ability.DASH, GameProgress.Ability.GLIDE), Set.of()));
            GamePanel panel = new GamePanel(model);
            model.state = GameModel.State.RUNNING;
            int track = new Color(57, 79, 98).getRGB();
            int fill = GameUi.MINT.getRGB();
            BufferedImage empty = render(panel, "hud-empty");
            assertEquals(track, empty.getRGB(800, 54));
            model.x = (GameModel.START_X + model.level.finish) / 2.0;
            BufferedImage half = render(panel, "hud-half");
            assertEquals(fill, half.getRGB(800, 54));
            assertEquals(track, half.getRGB(900, 54));
            model.x = model.level.finish;
            BufferedImage full = render(panel, "hud-full");
            assertEquals(fill, full.getRGB(900, 54));
            assertEquals(Integer.MAX_VALUE, model.progress().balance());
        });
    }

    @Test void collectionFeedbackExpiresAndReplayDoesNotRestoreTheRing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Ring ring = new Ring("one", 100, Level.GROUND - 24);
            Level level = new Level("feedback", List.of(), List.of(), List.of(ring));
            GameModel model = new GameModel(level);
            GamePanel panel = new GamePanel(model);
            model.state = GameModel.State.RUNNING;
            panel.updateGame(FixedStepLoop.STEP);
            BufferedImage collected = render(panel, "feedback-collect");
            assertTrue(goldFeedbackPixels(collected, model) > 20);
            assertEquals(1, model.progress().balance());
            for (int i = 0; i < 145; i++) panel.updateGame(FixedStepLoop.STEP);
            assertEquals(0, goldFeedbackPixels(render(panel, "feedback-expired"), model));
            tap(panel, "R");
            model.state = GameModel.State.RUNNING;
            panel.updateGame(FixedStepLoop.STEP);
            assertEquals(0, goldFeedbackPixels(render(panel, "feedback-replay"), model));
            assertTrue(model.isRingCollected(ring));
            assertEquals(1, model.progress().balance());
        });
    }

    @Test void newEffectsRequireAcceptedCommandsAndRespectMuteAndKeyRepeat() throws Exception {
        var worker = Executors.newSingleThreadExecutor();
        TestAudioOutput output = new TestAudioOutput();
        try {
            SwingUtilities.invokeAndWait(() -> {
                GameModel model = new GameModel(new Level(List.of(), List.of()),
                    new GameProgress(10, Set.of(), Set.of(GameProgress.Ability.DASH), Set.of()));
                GameSession session = new GameSession(model);
                GamePanel panel = new GamePanel(session, () -> new GameAudio(() -> output, worker), ignored -> { });
                panel.addNotify();
                try {
                    tap(panel, "D"); // READY: no effect.
                    model.state = GameModel.State.RUNNING;
                    press(panel, "D");
                    press(panel, "D");
                    tap(panel, "G"); // Not purchased.
                    model.state = GameModel.State.WON;
                    tap(panel, "1"); // Shop closed: no denial sound.
                    tap(panel, "B");
                    press(panel, "2"); // Successful purchase.
                    press(panel, "2"); // Repeat ignored.
                    release(panel, "2");
                    tap(panel, "2"); // Already owned.
                    tap(panel, "R");
                    model.state = GameModel.State.RUNNING;
                    model.grounded = false;
                    model.y = Level.GROUND - 100;
                    model.velocityY = 50;
                    press(panel, "G"); // Activation while descending.
                    press(panel, "G");
                    panel.updateGame(FixedStepLoop.STEP);
                    panel.updateGame(FixedStepLoop.STEP);
                    release(panel, "G");
                    model.velocityY = -1;
                    press(panel, "G"); // Held through the apex.
                    panel.updateGame(FixedStepLoop.STEP);
                    panel.updateGame(FixedStepLoop.STEP);
                    tap(panel, "M");
                    release(panel, "G");
                    tap(panel, "G"); // Muted activation.
                    release(panel, "D");
                    tap(panel, "D"); // Muted dash.
                } finally { panel.removeNotify(); }
            });
        } finally { worker.shutdown(); }
        assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(1, output.events.stream().filter("DASH"::equals).count());
        assertEquals(2, output.events.stream().filter("GLIDE"::equals).count());
        assertEquals(1, output.events.stream().filter("PURCHASE"::equals).count());
        assertEquals(1, output.events.stream().filter("DENIED"::equals).count());
    }

    private int goldFeedbackPixels(BufferedImage image, GameModel model) {
        int count = 0;
        int playerX = (int) Math.min(model.x, 210);
        for (int y = 355; y < 385; y++) {
            for (int x = playerX; x < playerX + 180; x++) {
                if (image.getRGB(x, y) == new Color(255, 206, 76).getRGB()) count++;
            }
        }
        return count;
    }

    private BufferedImage renderButton(GameButton button) {
        BufferedImage image = new BufferedImage(button.getWidth(), button.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try { button.paint(g); } finally { g.dispose(); }
        return image;
    }

    private BufferedImage render(GamePanel panel, String name) {
        panel.setSize(GamePanel.WIDTH, GamePanel.HEIGHT);
        BufferedImage image = new BufferedImage(GamePanel.WIDTH, GamePanel.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try { panel.paint(g); } finally { g.dispose(); }
        try {
            Path file = Path.of("target", "render-review", name + ".png");
            Files.createDirectories(file.getParent());
            ImageIO.write(image, "png", file.toFile());
        } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
        return image;
    }

    private void press(GamePanel panel, String key) { panel.getActionMap().get("pressed " + key).actionPerformed(null); }
    private void release(GamePanel panel, String key) { panel.getActionMap().get("released " + key).actionPerformed(null); }
    private void tap(GamePanel panel, String key) { press(panel, key); release(panel, key); }
}
