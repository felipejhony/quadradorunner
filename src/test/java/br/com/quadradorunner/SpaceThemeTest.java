package br.com.quadradorunner;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpaceThemeTest {
    @Test void ufosAreVisibleOnlyInThirdPhase() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            int dome = new Color(113, 235, 214).getRGB();
            for (String id : List.of("level-01", "level-02", "level-03")) {
                GameModel model = new GameModel(new Level(id, List.of(), List.of(), List.of()));
                model.state = GameModel.State.RUNNING;
                GamePanel panel = new GamePanel(model);
                panel.setSize(GamePanel.WIDTH, GamePanel.HEIGHT);
                BufferedImage image = new BufferedImage(GamePanel.WIDTH, GamePanel.HEIGHT, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                try { panel.paint(graphics); } finally { graphics.dispose(); }
                for (int i = 0; i < 4; i++) {
                    int x = Math.floorMod(150 + i * 265, GamePanel.WIDTH + 140) - 70;
                    assertEquals(id.equals("level-03"), image.getRGB(x + 44, 142 + i % 3 * 32) == dome);
                }
            }
        });
    }

    @Test void thirdPhaseSelectsSpaceScoreOnAudioWorker() throws Exception {
        var worker = Executors.newSingleThreadExecutor();
        var themes = new CopyOnWriteArrayList<GameAudio.Music>();
        GameAudio audio = new GameAudio(() -> new GameAudio.Output() {
            public void play(GameAudio.Effect effect) { }
            public void music(boolean playing) { }
            public void silence() { }
            public void close() { }
            public void theme(GameAudio.Music theme) { themes.add(theme); }
        }, worker);
        try {
            SwingUtilities.invokeAndWait(() -> {
                GameModel model = new GameModel(new Level("level-03", List.of(), List.of(), List.of()));
                GamePanel panel = new GamePanel(model, () -> audio, ignored -> { });
                panel.addNotify();
                panel.removeNotify();
            });
        } finally { audio.close(); }
        assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(List.of(GameAudio.Music.SPACE), themes);
    }
}
