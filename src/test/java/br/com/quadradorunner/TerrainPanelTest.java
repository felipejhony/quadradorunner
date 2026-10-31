package br.com.quadradorunner;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainPanelTest {
    @Test void everyPhaseDrawsThickWhiteObstacleOutlineAndVisiblePits() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (String id : List.of("level-01", "level-02", "level-03")) {
                Level level = new Level(id, "Test", Level.FINISH,
                    List.of(Level.obstacle(400, 60, 60)), List.of(), List.of(), List.of(),
                    List.of(new Level.Pit(600, 100)));
                GameModel model = new GameModel(level);
                model.state = GameModel.State.RUNNING;
                GamePanel panel = new GamePanel(model);
                panel.setSize(GamePanel.WIDTH, GamePanel.HEIGHT);
                BufferedImage image = new BufferedImage(GamePanel.WIDTH, GamePanel.HEIGHT, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                try { panel.paint(graphics); } finally { graphics.dispose(); }
                assertEquals(Color.WHITE.getRGB(), image.getRGB(400, 390), id);
                assertEquals(Color.WHITE.getRGB(), image.getRGB(399, 390), id);
                assertEquals(new Color(12, 18, 30).getRGB(), image.getRGB(650, 430), id);
            }
        });
    }
}
