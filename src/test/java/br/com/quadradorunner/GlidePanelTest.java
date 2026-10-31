package br.com.quadradorunner;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlidePanelTest {
    @Test void heldKeyReleaseFocusAndPauseDoNotLeaveGlideStuckOrWriteSaves() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameModel model = new GameModel(level("a"), progress());
            falling(model);
            List<GameProgress> saves = new ArrayList<>();
            GamePanel panel = new GamePanel(model, GameAudio::new, saves::add);
            press(panel, "SPACE");
            press(panel, "SPACE");
            assertTrue(model.isGliding());
            panel.updateGame(FixedStepLoop.STEP);
            assertEquals(120, model.velocityY);
            release(panel, "SPACE");
            assertFalse(model.isGliding());
            press(panel, "SPACE");
            panel.pauseForFocusLoss();
            double y = model.y;
            panel.updateGame(FixedStepLoop.STEP);
            assertEquals(y, model.y);
            assertFalse(model.isGliding());
            panel.audioFocusGained();
            assertEquals(GameModel.State.PAUSED, model.state);
            press(panel, "P");
            assertFalse(model.isGliding());
            release(panel, "SPACE");
            press(panel, "SPACE");
            assertTrue(model.isGliding());
            release(panel, "P");
            press(panel, "P");
            release(panel, "P");
            press(panel, "P");
            assertEquals(GameModel.State.RUNNING, model.state);
            assertFalse(model.isGliding());
            press(panel, "SPACE"); // Still held: repeat cannot re-arm after pause.
            assertFalse(model.isGliding());
            release(panel, "SPACE");
            press(panel, "SPACE");
            assertTrue(model.isGliding());
            press(panel, "R");
            assertFalse(model.isGliding());
            assertTrue(saves.isEmpty());
        });
    }

    @Test void heldDuringAscentActivatesOnDescentButDoesNotCarryToNextLevel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameSession session = new GameSession(List.of(level("a"), level("b")), progress());
            GamePanel panel = new GamePanel(session, GameAudio::new, ignored -> { });
            press(panel, "SPACE");
            press(panel, "SPACE");
            assertFalse(session.current().isGliding());
            for (int i = 0; i < 60; i++) panel.updateGame(FixedStepLoop.STEP);
            assertTrue(session.current().isGliding());
            session.current().x = Level.FINISH - 1;
            panel.updateGame(FixedStepLoop.STEP);
            assertEquals(GameModel.State.WON, session.current().state);
            assertFalse(session.current().isGliding());
            press(panel, "ENTER");
            GameModel next = session.current();
            assertEquals("b", next.level.id);
            assertEquals(GameModel.State.READY, next.state);
            falling(next);
            press(panel, "SPACE"); // Held from previous level, not a new command.
            assertFalse(next.isGliding());
            release(panel, "SPACE");
            press(panel, "SPACE");
            assertTrue(next.isGliding());
        });
    }

    @Test void rendersGliderOnlyWhenPurchasedHeldAndDescending() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameModel model = new GameModel(level("a"), progress());
            falling(model);
            GamePanel panel = new GamePanel(model);
            press(panel, "SPACE");
            BufferedImage active = render(panel, "glide-active");
            int cyan = new Color(134, 234, 255).getRGB();
            assertEquals(cyan, active.getRGB(117, 160));
            assertEquals(cyan, active.getRGB(110, 190));
            release(panel, "SPACE");
            BufferedImage released = render(panel, "glide-released");
            assertNotEquals(cyan, released.getRGB(117, 160));
            assertNotEquals(cyan, released.getRGB(110, 190));
            GameModel locked = new GameModel(level("a"));
            falling(locked);
            GamePanel lockedPanel = new GamePanel(locked);
            press(lockedPanel, "SPACE");
            assertFalse(locked.isGliding());
            render(lockedPanel, "glide-locked");
        });
    }

    private Level level(String id) { return new Level(id, List.of(), List.of(), List.of()); }

    private GameProgress progress() {
        return new GameProgress(0, Set.of(), Set.of(GameProgress.Ability.GLIDE), Set.of());
    }

    private void falling(GameModel model) {
        model.state = GameModel.State.RUNNING;
        model.grounded = false;
        model.y = 180;
        model.velocityY = 500;
    }

    private BufferedImage render(GamePanel panel, String name) {
        panel.setSize(GamePanel.WIDTH, GamePanel.HEIGHT);
        BufferedImage image = new BufferedImage(GamePanel.WIDTH, GamePanel.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try { panel.paint(graphics); }
        finally { graphics.dispose(); }
        try {
            Path directory = Path.of("target", "render-review");
            Files.createDirectories(directory);
            ImageIO.write(image, "png", directory.resolve(name + ".png").toFile());
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
        return image;
    }

    private void press(GamePanel panel, String key) {
        panel.getActionMap().get("pressed " + key).actionPerformed(null);
    }

    private void release(GamePanel panel, String key) {
        panel.getActionMap().get("released " + key).actionPerformed(null);
    }
}
