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
    @Test void summaryBlocksAdvanceUntilPurchaseAndPreservesProgressDuringTransition() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameSession session = new GameSession(Level.campaign(), new GameProgress(4, Set.of(), Set.of(), Set.of()));
            List<GameProgress> saved = new ArrayList<>();
            GamePanel panel = new GamePanel(session, GameAudio::new, saved::add);
            win(panel, session);
            assertFalse(session.canAdvance());
            render(panel, "campaign-dash-locked");
            tap(panel, "ENTER");
            assertEquals(1, session.levelNumber());
            tap(panel, "B");
            tap(panel, "1");
            tap(panel, "B");
            assertTrue(session.canAdvance());
            render(panel, "campaign-dash-unlocked");
            GameProgress bought = session.current().progress();
            tap(panel, "ENTER");
            assertEquals(2, session.levelNumber());
            assertSame(bought, session.current().progress());
            assertEquals(GameModel.State.READY, session.current().state);
            render(panel, "campaign-dash-ready");
            win(panel, session);
            assertFalse(session.canAdvance());
            assertEquals(Set.of(GameProgress.Ability.GLIDE), session.missingNextAbilities());
            render(panel, "campaign-glide-locked");
            tap(panel, "ENTER");
            assertEquals(2, session.levelNumber());
            assertEquals(3, saved.size()); // First win, purchase, second win.
            assertEquals(0, session.current().progress().balance());
        });
    }

    @Test void rendersBothSkillGatesWithDistinctVisualInstructions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (int index : List.of(1, 2)) {
                GameSession session = at(index);
                session.current().state = GameModel.State.RUNNING;
                GamePanel panel = new GamePanel(session, GameAudio::new, ignored -> { });
                BufferedImage image = render(panel, index == 1 ? "route-dash" : "route-glide");
                int expected = (index == 1 ? new Color(181, 157, 255) : new Color(134, 234, 255)).getRGB();
                assertEquals(expected, image.getRGB(650, 200));
                assertEquals(index + 1, session.levelNumber());
            }
        });
    }

    @Test void shortLevelUsesItsOwnFinishForCameraFlagAndProgressBar() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameSession session = at(1);
            session.current().state = GameModel.State.RUNNING;
            session.current().x = session.current().level.finish;
            GamePanel panel = new GamePanel(session, GameAudio::new, ignored -> { });
            BufferedImage image = render(panel, "campaign-short-finish");
            assertEquals(new Color(83, 227, 192).getRGB(), image.getRGB(800, 46));
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
