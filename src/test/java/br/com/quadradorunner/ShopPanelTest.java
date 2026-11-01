package br.com.quadradorunner;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ShopPanelTest {
    @Test void shopButtonsAndKeyboardSharePurchaseRules() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameSession session = session(new GameProgress(30, Set.of(), Set.of(), Set.of()));
            GamePanel panel = new GamePanel(session, GameAudio::new, progress -> { });
            win(panel, session);
            press(panel, "B");
            javax.swing.JPanel choices = java.util.Arrays.stream(panel.getComponents())
                .filter(c -> c instanceof javax.swing.JScrollPane && c.isVisible())
                .map(c -> (javax.swing.JPanel) ((javax.swing.JScrollPane) c).getViewport().getView()).findFirst().orElseThrow();
            assertEquals(7, choices.getComponentCount());
            assertFalse(choices.getComponent(5).isEnabled()); // Upgrade requires Dash.
            ((javax.swing.JButton) choices.getComponent(0)).doClick();
            assertTrue(session.current().progress().unlockedAbilities().contains(GameProgress.Ability.DASH));
            assertFalse(choices.getComponent(0).isEnabled());
            press(panel, "DOWN");
            press(panel, "ENTER");
            assertTrue(session.current().progress().unlockedAbilities().contains(GameProgress.Ability.GLIDE));
            for (int i = 0; i < 5; i++) { release(panel, "DOWN"); press(panel, "DOWN"); }
            var scroll = (javax.swing.JScrollPane) choices.getParent().getParent();
            assertTrue(scroll.getViewport().getViewPosition().y > 0);
            assertTrue(scroll.getViewport().getViewRect().intersects(choices.getComponent(6).getBounds()));
            press(panel, "4");
            assertTrue(session.current().progress().unlockedAbilities().contains(GameProgress.Ability.DURABILITY));
            release(panel, "B");
            press(panel, "B");
            assertFalse(choices.isVisible());
        });
    }
    @Test void bindingsRejectOutsideShopRepeatedKeysDuplicatesAndInsufficientFunds() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameSession session = session(new GameProgress(4, Set.of(), Set.of(), Set.of()));
            List<GameProgress> changes = new ArrayList<>();
            GamePanel panel = new GamePanel(session, GameAudio::new, changes::add);
            press(panel, "B");
            assertFalse(session.isShopOpen());
            press(panel, "1");
            release(panel, "1");
            assertTrue(changes.isEmpty());
            win(panel, session);
            release(panel, "B");
            press(panel, "B");
            assertTrue(session.isShopOpen());
            press(panel, "1");
            press(panel, "1");
            release(panel, "1");
            press(panel, "1");
            press(panel, "2");
            assertEquals(2, changes.size()); // One conclusion and one successful purchase.
            assertEquals(0, session.current().progress().balance());
            assertEquals(Set.of(GameProgress.Ability.DASH), session.current().progress().unlockedAbilities());
            panel.pauseForFocusLoss();
            panel.audioFocusGained();
            assertTrue(session.isShopOpen());
            assertEquals(GameModel.State.WON, session.current().state);
            press(panel, "ESCAPE");
            assertFalse(session.isShopOpen());
            press(panel, "R");
            assertEquals(GameModel.State.READY, session.current().state);
            assertEquals(0, session.current().progress().balance());
            assertEquals(2, changes.size());
        });
    }

    @Test void purchasesPersistWithSpentBalanceAndRingsRemainAbsent(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("progress.properties");
        var worker = Executors.newSingleThreadExecutor();
        var errors = new CopyOnWriteArrayList<Exception>();
        ProgressAutosave autosave = new ProgressAutosave(new GameProgressStore(file), worker);
        Level level = Level.standard();
        Set<GameProgress.RingId> rings = level.rings.stream()
            .map(ring -> new GameProgress.RingId(level.id, ring.id())).collect(Collectors.toSet());
        GameProgress initial = new GameProgress(15, rings, Set.of(), Set.of());
        try {
            SwingUtilities.invokeAndWait(() -> {
                GameSession session = new GameSession(List.of(level), initial);
                GamePanel panel = new GamePanel(session, GameAudio::new, snapshot -> autosave.save(snapshot,
                    failure -> { if (failure != null) errors.add(failure); }));
                win(panel, session);
                press(panel, "B");
                press(panel, "1");
                press(panel, "2");
                assertEquals(2, session.current().progress().balance());
                assertEquals(rings, session.current().progress().collectedRings());
            });
        } finally { autosave.close(); }
        assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS));
        assertTrue(errors.isEmpty(), errors.toString());
        GameSession restored = new GameSession(List.of(level), new GameProgressStore(file).load());
        assertEquals(2, restored.current().progress().balance());
        assertEquals(Set.of(GameProgress.Ability.DASH, GameProgress.Ability.GLIDE),
            restored.current().progress().unlockedAbilities());
        for (Ring ring : level.rings) assertTrue(restored.current().isRingCollected(ring));
        restored.current().state = GameModel.State.RUNNING;
        restored.current().x = Level.FINISH - 1;
        restored.current().update(FixedStepLoop.STEP);
        restored.openShop();
        assertEquals(AbilityShop.Status.ALREADY_OWNED, restored.buy(AbilityShop.Item.DASH));
        assertEquals(AbilityShop.Status.ALREADY_OWNED, restored.buy(AbilityShop.Item.GLIDE));
        assertEquals(2, restored.current().progress().balance());
    }

    @Test void rendersAvailableInsufficientAndOwnedItemsWithPurchaseFeedback() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameSession session = session(new GameProgress(13, Set.of(), Set.of(), Set.of()));
            GamePanel panel = new GamePanel(session, GameAudio::new, progress -> { });
            win(panel, session);
            press(panel, "B");
            render(panel, "shop-available");
            press(panel, "1");
            press(panel, "2");
            render(panel, "shop-owned");
            GameProgress bought = session.current().progress();
            assertEquals(0, bought.balance());
            assertEquals(2, bought.unlockedAbilities().size());
            release(panel, "1");
            press(panel, "1");
            render(panel, "shop-duplicate");
            assertSame(bought, session.current().progress());
            GameSession poor = session(new GameProgress());
            GamePanel poorPanel = new GamePanel(poor, GameAudio::new, progress -> { });
            win(poorPanel, poor);
            press(poorPanel, "B");
            press(poorPanel, "2");
            render(poorPanel, "shop-insufficient");
            assertEquals(0, poor.current().progress().balance());
            assertTrue(poor.current().progress().unlockedAbilities().isEmpty());
        });
    }

    private void render(GamePanel panel, String name) {
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
    }

    private GameSession session(GameProgress progress) {
        return new GameSession(List.of(new Level("a", List.of(), List.of(), List.of())), progress);
    }

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
