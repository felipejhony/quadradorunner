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

class DashPanelTest {
    @Test void heldKeyDoesNotReactivateAfterCooldownAndFocusLossFreezesDash() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameModel model = new GameModel(new Level(List.of(), List.of()),
                new GameProgress(0, Set.of(), Set.of(GameProgress.Ability.DASH), Set.of()));
            List<GameProgress> saves = new ArrayList<>();
            GamePanel panel = new GamePanel(model, GameAudio::new, saves::add);
            press(panel, "D");
            assertFalse(model.isDashing());
            release(panel, "D");
            press(panel, "SPACE");
            press(panel, "D");
            assertTrue(model.isDashing());
            for (int i = 0; i < 144; i++) panel.updateGame(FixedStepLoop.STEP);
            assertEquals(0, model.dashCooldownRemaining());
            press(panel, "D"); // Automatic repeat must not start another impulse.
            assertFalse(model.isDashing());
            release(panel, "D");
            press(panel, "D");
            assertTrue(model.isDashing());
            panel.pauseForFocusLoss();
            double x = model.x;
            double active = model.dashTimeRemaining();
            for (int i = 0; i < 120; i++) panel.updateGame(FixedStepLoop.STEP);
            assertEquals(x, model.x);
            assertEquals(active, model.dashTimeRemaining());
            panel.audioFocusGained();
            assertEquals(GameModel.State.PAUSED, model.state);
            press(panel, "P");
            panel.updateGame(FixedStepLoop.STEP);
            assertTrue(model.x > x);
            press(panel, "R");
            assertFalse(model.isDashing());
            assertEquals(0, model.dashCooldownRemaining());
            assertTrue(model.progress().unlockedAbilities().contains(GameProgress.Ability.DASH));
            assertTrue(saves.isEmpty());
        });
    }

    @Test void rendersLockedActiveAndCooldownStatesWithoutChangingProgress() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameModel locked = new GameModel(new Level(List.of(), List.of()));
            locked.state = GameModel.State.RUNNING;
            GamePanel lockedPanel = new GamePanel(locked);
            press(lockedPanel, "D");
            assertFalse(locked.isDashing());
            render(lockedPanel, "dash-locked");

            GameModel model = new GameModel(new Level(List.of(), List.of()),
                new GameProgress(0, Set.of(), Set.of(GameProgress.Ability.DASH), Set.of()));
            model.state = GameModel.State.RUNNING;
            GamePanel panel = new GamePanel(model);
            GameProgress before = model.progress();
            press(panel, "D");
            BufferedImage active = render(panel, "dash-active");
            assertEquals(new Color(181, 157, 255).getRGB(),
                active.getRGB((int) model.x + 10, (int) model.y + 10));
            for (int i = 0; i < 24; i++) panel.updateGame(FixedStepLoop.STEP);
            assertFalse(model.isDashing());
            assertTrue(model.dashCooldownRemaining() > 0);
            render(panel, "dash-cooldown");
            assertSame(before, model.progress());
        });
    }

    private BufferedImage render(GamePanel panel, String name) {
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

    private void press(GamePanel panel, String key) {
        panel.getActionMap().get("pressed " + key).actionPerformed(null);
    }

    private void release(GamePanel panel, String key) {
        panel.getActionMap().get("released " + key).actionPerformed(null);
    }
}
