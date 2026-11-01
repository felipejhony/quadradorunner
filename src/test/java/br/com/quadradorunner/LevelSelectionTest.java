package br.com.quadradorunner;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LevelSelectionTest {
    private GameProgress progress() {
        return new GameProgress(2,
            Set.of(new GameProgress.RingId("level-01", "ring-01"),
                new GameProgress.RingId("level-02", "ring-01")),
            Set.of(GameProgress.Ability.DASH), Set.of("level-01"));
    }

    @Test void selectionPreservesPermanentSnapshotAndResetsAttempt() {
        GameProgress progress = progress();
        GameSession session = new GameSession(Level.campaign(), progress);
        assertTrue(session.selectLevel(1));
        session.current().jump();
        session.current().dash();
        assertTrue(session.selectLevel(0));
        assertSame(progress, session.current().progress());
        assertEquals(GameModel.State.READY, session.current().state);
        assertEquals(GameModel.START_X, session.current().x);
        assertEquals(0, session.current().dashCooldownRemaining());
        assertTrue(session.current().isRingCollected(session.current().level.rings.get(0)));
        assertTrue(session.selectLevel(1));
        assertTrue(session.current().isRingCollected(session.current().level.rings.get(0)));
    }

    @Test void refusesLockedAndInvalidChoicesWithoutReplacingModel() {
        GameSession session = new GameSession(Level.campaign(), new GameProgress(0, Set.of(),
            Set.of(GameProgress.Ability.DASH, GameProgress.Ability.GLIDE), Set.of()));
        GameModel original = session.current();
        for (int target : new int[]{-1, 1, 2, 3}) assertFalse(session.selectLevel(target));
        assertSame(original, session.current());
        assertFalse(new GameSession(Level.campaign(), progress()).selectLevel(2));
    }

    @Test void reloadAllowsReplayAndUnlockedNextWithoutRestoringRings(@TempDir Path directory) throws Exception {
        GameProgressStore store = new GameProgressStore(directory.resolve("save.properties"));
        store.save(progress());
        GameSession restored = new GameSession(Level.campaign(), new GameProgressStore(directory.resolve("save.properties")).load());
        assertTrue(restored.selectLevel(1));
        assertEquals(progress(), restored.current().progress());
        assertTrue(restored.current().isRingCollected(restored.current().level.rings.get(0)));
        restored.current().jump();
        restored.current().x = 350;
        restored.current().y = Level.GROUND - GameModel.SIZE;
        restored.current().update(FixedStepLoop.STEP);
        assertEquals(2, restored.current().progress().balance());
        restored.reset();
        assertFalse(restored.selectLevel(1));
        assertEquals(1, restored.levelNumber());
    }

    @Test void canResumeUnlockedNextLevelWithoutCompletingEarlierLevelAgain() {
        GameProgress saved = new GameProgress(0, Set.of(), Set.of(GameProgress.Ability.DASH), Set.of("level-01"));
        GameSession session = new GameSession(Level.campaign(), saved);
        assertTrue(session.selectLevel(1));
        assertSame(saved, session.current().progress());
        assertFalse(session.current().progress().completedLevelIds().contains("level-02"));
        assertFalse(session.selectLevel(2));
    }

    @Test void menuPausesBlocksCommandsCancelsAndSelectsThroughButtons() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameSession session = new GameSession(Level.campaign(), progress());
            GamePanel panel = new GamePanel(session, GameAudio::new,
                snapshot -> fail("Selecting must not change permanent progress"));
            session.current().jump();
            press(panel, "F");
            assertEquals(GameModel.State.PAUSED, session.current().state);
            GameModel original = session.current();
            press(panel, "R");
            press(panel, "P");
            assertEquals(GameModel.State.PAUSED, original.state);
            JPanel choices = null;
            for (var component : panel.getComponents()) {
                if (component instanceof JPanel child && child.isVisible()) choices = child;
            }
            assertNotNull(choices);
            assertTrue(choices.isVisible());
            assertFalse(choices.getComponent(2).isEnabled());
            press(panel, "F"); // OS repeat cannot toggle the open menu.
            assertTrue(choices.isVisible());
            panel.pauseForFocusLoss();
            panel.audioFocusGained();
            assertTrue(choices.isVisible());
            render(panel);
            press(panel, "ESCAPE");
            assertFalse(choices.isVisible());
            assertSame(original, session.current());
            panel.getActionMap().get("released F").actionPerformed(null);
            press(panel, "F");
            ((JButton) choices.getComponent(1)).doClick();
            assertEquals(2, session.levelNumber());
            assertEquals(GameModel.State.READY, session.current().state);
            assertFalse(choices.isVisible());
            press(panel, "SPACE");
            assertEquals(GameModel.State.RUNNING, session.current().state);
            assertEquals(GameModel.State.PAUSED, original.state);
            panel.getActionMap().get("released F").actionPerformed(null);
            press(panel, "F");
            panel.beginReset();
            ((JButton) choices.getComponent(1)).doClick();
            panel.finishReset(true);
            assertFalse(choices.isVisible());
            assertEquals(1, session.levelNumber());
            assertEquals(new GameProgress(), session.current().progress());
        });
    }

    @Test void arrowsSelectUnlockedLevelsAndEnterStartsHighlightedLevel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameSession session = new GameSession(Level.campaign(), progress());
            GamePanel panel = new GamePanel(session, GameAudio::new, snapshot -> { });
            press(panel, "F");
            press(panel, "DOWN");
            press(panel, "ENTER");
            assertEquals(2, session.levelNumber());
            assertEquals(GameModel.State.READY, session.current().state);
            panel.getActionMap().get("released F").actionPerformed(null);
            panel.getActionMap().get("released ENTER").actionPerformed(null);
            press(panel, "F");
            press(panel, "RIGHT"); // Wrap past the locked third level.
            press(panel, "ENTER");
            assertEquals(1, session.levelNumber());
        });
    }

    @Test void swingKeyBindingsKeepSelectionVisibleAndActivateItWithoutMouse() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameSession session = new GameSession(Level.campaign(), progress());
            GamePanel panel = new GamePanel(session, GameAudio::new, snapshot -> { });
            keyStroke(panel, "F");
            JPanel choices = java.util.Arrays.stream(panel.getComponents())
                .filter(component -> component instanceof JPanel).map(component -> (JPanel) component)
                .findFirst().orElseThrow();
            assertTrue(((JButton) choices.getComponent(0)).isSelected());
            keyStroke(panel, "DOWN");
            assertFalse(((JButton) choices.getComponent(0)).isSelected());
            assertTrue(((JButton) choices.getComponent(1)).isSelected());
            BufferedImage selected = buttonImage((JButton) choices.getComponent(1));
            keyStroke(panel, "UP");
            BufferedImage unselected = buttonImage((JButton) choices.getComponent(1));
            assertNotEquals(selected.getRGB(15, 15), unselected.getRGB(15, 15));
            keyStroke(panel, "LEFT"); // Skip the locked third phase.
            assertTrue(((JButton) choices.getComponent(1)).isSelected());
            keyStroke(panel, "ENTER");
            assertEquals(2, session.levelNumber());
            assertFalse(choices.isVisible());
            keyStroke(panel, "F");
            keyStroke(panel, "RIGHT");
            keyStroke(panel, "SPACE");
            assertEquals(1, session.levelNumber());
        });
    }

    private BufferedImage buttonImage(JButton button) {
        button.setSize(640, 55);
        BufferedImage image = new BufferedImage(640, 55, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try { button.paint(graphics); } finally { graphics.dispose(); }
        return image;
    }

    private void keyStroke(GamePanel panel, String key) {
        for (String event : new String[]{"pressed ", "released "}) {
            Object action = panel.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(javax.swing.KeyStroke.getKeyStroke(event + key));
            assertNotNull(action, "Missing binding: " + event + key);
            panel.getActionMap().get(action).actionPerformed(null);
        }
    }

    private void press(GamePanel panel, String key) {
        panel.getActionMap().get("pressed " + key).actionPerformed(null);
    }

    private void render(GamePanel panel) {
        panel.setSize(GamePanel.WIDTH, GamePanel.HEIGHT);
        for (var component : panel.getComponents()) {
            if (component instanceof JPanel child) child.doLayout();
        }
        BufferedImage image = new BufferedImage(GamePanel.WIDTH, GamePanel.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try { panel.paint(g); } finally { g.dispose(); }
        try {
            Path directory = Path.of("target/render-review");
            Files.createDirectories(directory);
            ImageIO.write(image, "png", directory.resolve("level-selection.png").toFile());
        } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
    }
}
