package br.com.quadradorunner;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingRenderingTest {
    @Test void collectedRingIsAbsentWhileUncollectedRingIsGolden() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Ring ring = new Ring("one", 350, Level.GROUND - 36);
            Level level = new Level("a", List.of(), List.of(), List.of(ring));
            GameModel visibleModel = new GameModel(level);
            visibleModel.state = GameModel.State.RUNNING;
            BufferedImage visible = render(visibleModel);
            GameProgress saved = new GameProgress(1, Set.of(new GameProgress.RingId("a", "one")),
                Set.of(), Set.of());
            GameModel hiddenModel = new GameModel(level, saved);
            hiddenModel.state = GameModel.State.RUNNING;
            BufferedImage hidden = render(hiddenModel);
            int gold = new Color(255, 206, 76).getRGB();
            int visibleGold = 0;
            int hiddenGold = 0;
            for (int y = (int) ring.y(); y < ring.y() + Ring.DIAMETER; y++) {
                for (int x = (int) ring.x(); x < ring.x() + Ring.DIAMETER; x++) {
                    if (visible.getRGB(x, y) == gold) visibleGold++;
                    if (hidden.getRGB(x, y) == gold) hiddenGold++;
                }
            }
            assertTrue(visibleGold > 20);
            assertEquals(0, hiddenGold);
            try {
                Path directory = Path.of("target", "render-review");
                Files.createDirectories(directory);
                ImageIO.write(visible, "png", directory.resolve("ring-visible.png").toFile());
                ImageIO.write(hidden, "png", directory.resolve("ring-collected.png").toFile());
            } catch (java.io.IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
        });
    }

    private BufferedImage render(GameModel model) {
        GamePanel panel = new GamePanel(model);
        panel.setSize(GamePanel.WIDTH, GamePanel.HEIGHT);
        BufferedImage image = new BufferedImage(GamePanel.WIDTH, GamePanel.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try { panel.paint(graphics); }
        finally { graphics.dispose(); }
        return image;
    }
}
