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

class CampaignPanelTest {
    @Test void summaryAdvancesWithoutPurchasesAndPreservesProgress() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameSession session = new GameSession(Level.campaign(), new GameProgress());
            List<GameProgress> saved = new ArrayList<>();
            GamePanel panel = new GamePanel(session, GameAudio::new, saved::add);
            win(panel, session);
            assertTrue(session.canAdvance());
            GameProgress progress = session.current().progress();
            tap(panel, "ENTER");
            assertEquals(2, session.levelNumber());
            assertSame(progress, session.current().progress());
            assertEquals(GameModel.State.READY, session.current().state);
            render(panel, "sunrise-ready");
            win(panel, session);
            assertTrue(session.canAdvance());
            tap(panel, "ENTER");
            assertEquals(3, session.levelNumber());
            assertEquals(2, saved.size());
            assertTrue(session.current().progress().unlockedAbilities().isEmpty());
        });
    }

    @Test void sunriseUsesWarmBackgroundAndCampaignHasNoSkillGates() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameSession session = at(1);
            session.current().state = GameModel.State.RUNNING;
            GamePanel panel = new GamePanel(session, GameAudio::new, ignored -> { });
            BufferedImage image = render(panel, "sunrise-running");
            Color sky = new Color(image.getRGB(500, 160));
            assertTrue(sky.getRed() > sky.getBlue());
            assertTrue(session.current().level.obstacles.size() >= 6);
            for (Level level : Level.campaign()) assertTrue(level.gates.isEmpty());
        });
    }

    @Test void shortLevelUsesItsOwnFinishForCameraFlagAndProgressBar() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameSession session = at(1);
            session.current().state = GameModel.State.RUNNING;
            session.current().x = session.current().level.finish;
            GamePanel panel = new GamePanel(session, GameAudio::new, ignored -> { });
            BufferedImage image = render(panel, "campaign-short-finish");
            assertEquals(new Color(83, 227, 192).getRGB(), image.getRGB(800, 54));
            assertEquals(new Color(225, 237, 247).getRGB(), image.getRGB(731, 350));
            panel.updateGame(FixedStepLoop.STEP);
            assertEquals(GameModel.State.WON, session.current().state);
        });
    }

    private GameSession at(int index) {
        GameSession session = new GameSession(Level.campaign(), new GameProgress(0, Set.of(),
            Set.of(GameProgress.Ability.DASH, GameProgress.Ability.GLIDE), Set.of("level-01", "level-02")));
        for (int i = 0; i < index; i++) {
            session.current().state = GameModel.State.RUNNING;
            session.current().x = session.current().level.finish - 1;
            session.current().update(FixedStepLoop.STEP);
            assertTrue(session.advance());
        }
        return session;
    }

    private void win(GamePanel panel, GameSession session) {
        session.current().state = GameModel.State.RUNNING;
        session.current().x = session.current().level.finish - 1;
        panel.updateGame(FixedStepLoop.STEP);
    }

    private void tap(GamePanel panel, String key) {
        panel.getActionMap().get("pressed " + key).actionPerformed(null);
        panel.getActionMap().get("released " + key).actionPerformed(null);
    }

    private BufferedImage render(GamePanel panel, String name) {
        panel.setSize(GamePanel.WIDTH, GamePanel.HEIGHT);
        BufferedImage image = new BufferedImage(GamePanel.WIDTH, GamePanel.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try { panel.paint(graphics); }
        finally { graphics.dispose(); }
        try {
            Path file = Path.of("target", "render-review", name + ".png");
            Files.createDirectories(file.getParent());
            ImageIO.write(image, "png", file.toFile());
        } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
        return image;
    }
}
