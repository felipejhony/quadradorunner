package br.com.quadradorunner;

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

class LevelSummaryPanelTest {
    @Test void advanceAndReplayBindingsUseCurrentModelAndIgnoreHeldEnter() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameSession session = new GameSession(List.of(level("a"), level("b"), level("c")),
                new GameProgress(5, Set.of(), Set.of(), Set.of()));
            List<GameProgress> saved = new ArrayList<>();
            GamePanel panel = new GamePanel(session, GameAudio::new, saved::add);
            GameModel first = session.current();
            win(panel, session);
            assertEquals(1, saved.size());
            assertEquals(Set.of("a"), saved.get(0).completedLevelIds());
            press(panel, "ENTER");
            GameModel second = session.current();
            assertEquals("b", second.level.id);
            assertEquals(GameModel.State.READY, second.state);
            press(panel, "SPACE");
            assertEquals(GameModel.State.RUNNING, second.state);
            press(panel, "P");
            assertEquals(GameModel.State.PAUSED, second.state);
            release(panel, "P");
            press(panel, "P");
            assertEquals(GameModel.State.RUNNING, second.state);
            win(panel, session);
            assertEquals(2, saved.size());
            assertEquals(Set.of("a", "b"), saved.get(1).completedLevelIds());
            press(panel, "ENTER"); // Key is still held from the first advance.
            assertSame(second, session.current());
            release(panel, "ENTER");
            press(panel, "ENTER");
            GameModel third = session.current();
            assertEquals("c", third.level.id);
            win(panel, session);
            press(panel, "R");
            assertEquals(GameModel.State.READY, third.state);
            assertEquals(GameModel.State.WON, first.state);
            assertEquals(GameModel.State.WON, second.state);
            assertEquals(GameModel.START_X, third.x);
            assertEquals(Set.of("a", "b", "c"), third.progress().completedLevelIds());
            assertTrue(saved.stream().allMatch(progress -> progress.balance() == 5));
        });
    }

    @Test void rendersSummaryWithNextLevelAndEndOfSequenceWithoutChangingProgress() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (int count : List.of(1, 2)) {
                Ring ring = new Ring("finish", Level.FINISH - 20, Level.GROUND - 30);
                Level first = new Level("a", List.of(), List.of(), List.of(ring));
                GameSession session = new GameSession(count == 1 ? List.of(first) : List.of(first, level("b")),
                    new GameProgress(7, Set.of(), Set.of(), Set.of()));
                GamePanel panel = new GamePanel(session, GameAudio::new, progress -> { });
                win(panel, session);
                GameProgress won = session.current().progress();
                assertEquals(new GameModel.LevelSummary("a", 1, 8), session.current().levelSummary());
                panel.setSize(GamePanel.WIDTH, GamePanel.HEIGHT);
                BufferedImage image = new BufferedImage(GamePanel.WIDTH, GamePanel.HEIGHT,
                    BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                try { panel.paint(graphics); }
                finally { graphics.dispose(); }
                assertSame(won, session.current().progress());
                boolean goldText = false;
                int gold = new java.awt.Color(255, 206, 76).getRGB();
                for (int y = 285; y < 312; y++) {
                    for (int x = 250; x < 710; x++) {
                        if (image.getRGB(x, y) == gold) goldText = true;
                    }
                }
                assertTrue(goldText, "Balance must be visible in the summary");
                try {
                    Path directory = Path.of("target", "render-review");
                    Files.createDirectories(directory);
                    ImageIO.write(image, "png", directory.resolve(count == 1
                        ? "summary-final.png" : "summary-next.png").toFile());
                } catch (java.io.IOException exception) {
                    throw new java.io.UncheckedIOException(exception);
                }
                if (count == 1) {
                    press(panel, "ENTER");
                    assertEquals(GameModel.State.WON, session.current().state);
                    assertSame(won, session.current().progress());
                }
            }
        });
    }

    private Level level(String id) { return new Level(id, List.of(), List.of(), List.of()); }

    private void win(GamePanel panel, GameSession session) {
        session.current().state = GameModel.State.RUNNING;
        session.current().x = Level.FINISH - 1;
        panel.updateGame(FixedStepLoop.STEP);
    }

    private void press(GamePanel panel, String key) {
        panel.getActionMap().get("pressed " + key).actionPerformed(null);
    }

    private void release(GamePanel panel, String key) {
        panel.getActionMap().get("released " + key).actionPerformed(null);
    }
}
