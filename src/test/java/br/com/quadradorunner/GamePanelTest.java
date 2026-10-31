package br.com.quadradorunner;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GamePanelTest {
    @Test void bindingsFocusAndLifecycleWorkOnEdtWithoutAWindow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameModel model = new GameModel(new Level(List.of(), List.of()));
            GamePanel panel = new GamePanel(model, () -> new GameAudio(TestAudioOutput::new));
            panel.getActionMap().get("pressed SPACE").actionPerformed(null);
            assertEquals(GameModel.State.RUNNING, model.state);
            panel.getActionMap().get("pressed P").actionPerformed(null);
            panel.getActionMap().get("pressed P").actionPerformed(null);
            assertEquals(GameModel.State.PAUSED, model.state);
            panel.pauseForFocusLoss();
            panel.getActionMap().get("pressed P").actionPerformed(null);
            assertEquals(GameModel.State.RUNNING, model.state);
            panel.pauseForFocusLoss();
            assertEquals(GameModel.State.PAUSED, model.state);
            panel.getActionMap().get("pressed R").actionPerformed(null);
            assertEquals(GameModel.State.READY, model.state);
            panel.addNotify();
            panel.getActionMap().get("pressed SPACE").actionPerformed(null);
            assertEquals(GameModel.State.RUNNING, model.state);
            panel.removeNotify();
            assertEquals(GameModel.State.PAUSED, model.state);
        });
    }

    @Test void rendersEveryStateAndKeepsHudOutsideCameraTransform() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameModel model = new GameModel(Level.standard());
            GamePanel panel = new GamePanel(model);
            panel.setSize(GamePanel.WIDTH, GamePanel.HEIGHT);
            for (GameModel.State state : GameModel.State.values()) {
                model.state = state;
                model.x = switch (state) {
                    case READY -> GameModel.START_X;
                    case RUNNING -> 3300;
                    case PAUSED -> 4600;
                    case LOST -> 6500;
                    case WON -> Level.FINISH;
                };
                model.powerRemaining = state == GameModel.State.RUNNING ? 5 : 0;
                BufferedImage image = new BufferedImage(GamePanel.WIDTH, GamePanel.HEIGHT,
                    BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                try { panel.paint(graphics); }
                finally { graphics.dispose(); }
                assertNotEquals(image.getRGB(0, 0), image.getRGB(0, Level.GROUND + 10));
                if (state == GameModel.State.WON) {
                    assertEquals(new java.awt.Color(83, 227, 192).getRGB(), image.getRGB(800, 46));
                }
                try {
                    Path directory = Path.of("target", "render-review");
                    Files.createDirectories(directory);
                    ImageIO.write(image, "png", directory.resolve(state + ".png").toFile());
                } catch (java.io.IOException exception) {
                    throw new java.io.UncheckedIOException(exception);
                }
            }
        });
    }
}
