package br.com.quadradorunner;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingVisibilityTest {
    @Test void freshStandardLevelShowsBothRingsInInitialViewport() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Level level = Level.standard();
            GameModel model = new GameModel(level);
            model.state = GameModel.State.RUNNING;
            BufferedImage image = render(model, "rings-fresh");
            assertEquals(0, model.collectedRingCount());
            assertTrue(goldPixels(image, level.rings.get(0), 0) > 20);
            assertTrue(goldPixels(image, level.rings.get(1), 0) > 20);
        });
    }

    @Test void partialSaveHidesOnlyCollectedRingAndCameraKeepsOtherRingVisible() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Level level = Level.standard();
            Set<GameProgress.RingId> collected = Set.of(new GameProgress.RingId(level.id, "ring-01"),
                new GameProgress.RingId("other-level", "ring-02"),
                new GameProgress.RingId(level.id, "old-ring-not-in-map"));
            GameModel model = new GameModel(level, new GameProgress(3, collected, Set.of(), Set.of()));
            model.state = GameModel.State.RUNNING;
            BufferedImage image = render(model, "rings-partial");
            assertEquals(1, model.collectedRingCount());
            assertEquals(0, goldPixels(image, level.rings.get(0), 0));
            assertTrue(goldPixels(image, level.rings.get(1), 0) > 20);
            model.x = 1000;
            BufferedImage scrolled = render(model, "rings-camera");
            assertTrue(goldPixels(scrolled, level.rings.get(1), 790) > 20);
        });
    }

    @Test void fullyCollectedSaveShowsStatusWithoutRestoringRingsOrChangingBalance() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Level level = Level.standard();
            Set<GameProgress.RingId> rings = level.rings.stream()
                .map(ring -> new GameProgress.RingId(level.id, ring.id())).collect(Collectors.toSet());
            GameProgress saved = new GameProgress(12, rings, Set.of(), Set.of(level.id));
            GameModel model = new GameModel(level, saved);
            render(model, "rings-all-collected-ready");
            assertEquals(12, model.collectedRingCount());
            model.state = GameModel.State.RUNNING;
            BufferedImage image = render(model, "rings-all-collected-running");
            for (int i = 0; i < 2; i++) assertEquals(0, goldPixels(image, level.rings.get(i), 0));
            int gold = new Color(255, 206, 76).getRGB();
            int statusPixels = 0;
            for (int y = 16; y < 57; y++) {
                for (int x = 420; x < 700; x++) if (image.getRGB(x, y) == gold) statusPixels++;
            }
            assertTrue(statusPixels > 100, "Collected-ring count and wallet should be visible");
            model.restart();
            assertSame(saved, model.progress());
            assertEquals(12, model.collectedRingCount());
            assertEquals(12, model.progress().balance());
        });
    }

    @Test void counterUsesCurrentMapIdentitiesEvenWithSpentBalanceAndHistoricalEntries() {
        Level level = Level.standard();
        Set<GameProgress.RingId> history = new HashSet<>();
        history.add(new GameProgress.RingId(level.id, "ring-01"));
        history.add(new GameProgress.RingId(level.id, "removed-ring"));
        history.add(new GameProgress.RingId("other", "ring-02"));
        GameModel model = new GameModel(level, new GameProgress(0, history, Set.of(GameProgress.Ability.DASH), Set.of()));
        assertEquals(1, model.collectedRingCount());
        assertEquals(0, model.progress().balance());
        assertEquals(history, model.progress().collectedRings());
    }

    private int goldPixels(BufferedImage image, Ring ring, int camera) {
        int count = 0;
        int gold = new Color(255, 206, 76).getRGB();
        for (int y = (int) ring.y(); y < ring.y() + Ring.DIAMETER; y++) {
            for (int x = (int) ring.x() - camera; x < ring.x() - camera + Ring.DIAMETER; x++) {
                if (image.getRGB(x, y) == gold) count++;
            }
        }
        return count;
    }

    private BufferedImage render(GameModel model, String name) {
        GamePanel panel = new GamePanel(model);
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
}
