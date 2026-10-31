package br.com.quadradorunner;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ProgressResetTest {
    @TempDir Path directory;

    private GameProgress oldProgress() {
        return new GameProgress(10, Set.of(new GameProgress.RingId("level-01", "ring-01")),
            Set.of(GameProgress.Ability.DASH, GameProgress.Ability.GLIDE), Set.of("level-01", "b", "c"));
    }

    private List<Level> levels() {
        return List.of(Level.standard(), new Level("b", List.of(), List.of(), List.of()),
            new Level("c", List.of(), List.of(), List.of()));
    }

    @Test void fullResetReturnsToFirstLevelClearsProgressAndRestoresTemporaryItems() {
        GameSession session = new GameSession(levels(), oldProgress());
        assertTrue(session.isLevelUnlocked(2));
        for (int i = 0; i < 2; i++) {
            session.current().state = GameModel.State.RUNNING;
            session.current().x = Level.FINISH - 1;
            session.current().update(FixedStepLoop.STEP);
            assertTrue(session.advance());
        }
        session.current().powerRemaining = 10;
        session.current().jump();
        session.current().dash();
        session.reset();
        GameModel reset = session.current();
        assertEquals(1, session.levelNumber());
        assertEquals(GameModel.State.READY, reset.state);
        assertEquals(new GameProgress(), reset.progress());
        assertEquals(0, reset.collectedRingCount());
        assertEquals(0, reset.powerRemaining);
        assertEquals(0, reset.dashCooldownRemaining());
        assertFalse(reset.isDashing());
        assertFalse(reset.isGliding());
        for (boolean collected : reset.collected) assertFalse(collected);
        assertTrue(session.isLevelUnlocked(0));
        assertFalse(session.isLevelUnlocked(1));
        assertFalse(session.isLevelUnlocked(2));
    }

    @Test void queuedOldSavesFinishBeforeResetAndNewSessionLoadsOnlyFirstLevelUnlocked() throws Exception {
        Path file = directory.resolve("progress.properties");
        var worker = Executors.newSingleThreadExecutor();
        CountDownLatch release = new CountDownLatch(1);
        var failures = new CopyOnWriteArrayList<Exception>();
        worker.execute(() -> {
            try { release.await(); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        });
        ProgressAutosave autosave = new ProgressAutosave(new GameProgressStore(file), worker);
        try {
            autosave.save(oldProgress(), failure -> { if (failure != null) failures.add(failure); });
            autosave.reset(failure -> { if (failure != null) failures.add(failure); });
        } finally {
            release.countDown();
            autosave.close();
        }
        assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS));
        assertTrue(failures.isEmpty(), failures.toString());
        GameProgress reloaded = new GameProgressStore(file).load();
        assertEquals(new GameProgress(), reloaded);
        GameSession restored = new GameSession(levels(), reloaded);
        assertTrue(restored.isLevelUnlocked(0));
        assertFalse(restored.isLevelUnlocked(1));
        assertFalse(restored.isLevelUnlocked(2));
    }

    @Test void menuButtonAllowsCancellationAndFailedResetPreservesMemoryWhilePendingBlocksInput() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameSession session = new GameSession(levels(), oldProgress());
            AtomicInteger requests = new AtomicInteger();
            GamePanel panel = new GamePanel(session, GameAudio::new, ignored -> { }, ignored -> requests.incrementAndGet());
            JButton button = resetButton(panel);
            assertTrue(button.isVisible());
            button.doClick(0); // Injected confirmation was canceled: no reset begins.
            assertEquals(1, requests.get());
            assertEquals(oldProgress(), session.current().progress());
            panel.getActionMap().get("pressed SPACE").actionPerformed(null);
            assertFalse(button.isVisible());
            panel.getActionMap().get("pressed P").actionPerformed(null);
            assertTrue(button.isVisible());
            GameModel previous = session.current();
            panel.beginReset();
            assertFalse(button.isEnabled());
            panel.getActionMap().get("pressed R").actionPerformed(null);
            panel.updateGame(FixedStepLoop.STEP);
            assertSame(previous, session.current());
            assertEquals(oldProgress(), previous.progress());
            panel.finishReset(false);
            assertTrue(button.isEnabled());
            assertFalse(panel.isResetting());
            assertSame(previous, session.current());
            panel.beginReset();
            panel.finishReset(true);
            assertEquals(new GameProgress(), session.current().progress());
            assertEquals(1, session.levelNumber());
            assertTrue(button.isVisible());
            render(panel);
        });
    }

    @Test void resetWriteFailureIsReportedWithoutReplacingExistingPath() throws Exception {
        Path file = directory.resolve("progress.properties");
        Files.createDirectory(file);
        var worker = Executors.newSingleThreadExecutor();
        var failures = new CopyOnWriteArrayList<Exception>();
        ProgressAutosave autosave = new ProgressAutosave(new GameProgressStore(file), worker);
        try { autosave.reset(failure -> { if (failure != null) failures.add(failure); }); }
        finally { autosave.close(); }
        assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(1, failures.size());
        assertTrue(Files.isDirectory(file));
    }

    private JButton resetButton(GamePanel panel) {
        for (Component component : panel.getComponents()) if (component instanceof JButton button) return button;
        throw new AssertionError("Missing reset button");
    }

    private void render(GamePanel panel) {
        panel.setSize(GamePanel.WIDTH, GamePanel.HEIGHT);
        BufferedImage image = new BufferedImage(GamePanel.WIDTH, GamePanel.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try { panel.paint(graphics); }
        finally { graphics.dispose(); }
        try {
            Path output = Path.of("target", "render-review", "reset-menu.png");
            Files.createDirectories(output.getParent());
            ImageIO.write(image, "png", output.toFile());
        } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
    }
}
