package br.com.quadradorunner;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PoolLevelTest {
    @Test void fourthLevelRequiresPreviousCompletionsAndOldSaveCanSelectIt(@TempDir Path directory) throws Exception {
        GameProgress oldSave = new GameProgress(4, Set.of(), Set.of(GameProgress.Ability.DASH),
            Set.of("level-01", "level-02", "level-03"));
        var store = new GameProgressStore(directory.resolve("save.properties"));
        store.save(oldSave);
        GameSession session = new GameSession(Level.campaign(), store.load());
        assertEquals(4, session.levelCount());
        assertTrue(session.isLevelUnlocked(3));
        assertTrue(session.selectLevel(3));
        assertEquals("Piscinas", session.current().level.title);
        assertEquals(oldSave, session.current().progress());
        assertFalse(session.hasNextLevel());
        GameSession missing = new GameSession(Level.campaign(), new GameProgress(0, Set.of(), Set.of(),
            Set.of("level-01", "level-03")));
        assertFalse(missing.isLevelUnlocked(3));
    }

    @Test void poolsHaveStableCollectiblesSafeClearancesAndCanBeCompletedAcrossSeeds() {
        var expectedIds = Level.pools(new Random(1)).rings.stream().map(Ring::id).toList();
        for (int seed = 0; seed < 100; seed++) {
            Level level = Level.pools(new Random(seed));
            assertEquals("level-04", level.id);
            assertEquals(Level.FINISH, level.finish);
            assertEquals(40, level.rings.size());
            assertEquals(expectedIds, level.rings.stream().map(Ring::id).toList());
            assertFalse(level.pits.isEmpty());
            assertTrue(level.obstacles.size() >= 12);
            double previousEnd = GameModel.START_X;
            for (var obstacle : level.obstacles) {
                var bounds = obstacle.getBounds2D();
                assertTrue(bounds.getMinX() - previousEnd >= ObstacleDetector.OBSTACLE_CLEARANCE);
                assertTrue(ObstacleDetector.isFree(level, (int) bounds.getX(), (int) bounds.getWidth()));
                previousEnd = bounds.getMaxX();
            }
            for (int start = 650; start + 1200 < level.finish - 200; start += 400) {
                int left = start;
                assertTrue(level.obstacles.stream().anyMatch(o -> o.getBounds2D().getMinX() >= left
                    && o.getBounds2D().getMaxX() < left + 1200), "Empty section: " + start + ", seed=" + seed);
            }
            GameModel model = new GameModel(level, new GameProgress(0, Set.of(),
                Set.of(GameProgress.Ability.DASH), Set.of()));
            CoursePilot.complete(model);
            assertEquals(GameModel.State.WON, model.state, "seed=" + seed + ", x=" + model.x);
            assertEquals(GameModel.MAX_LIVES, model.lives, "seed=" + seed);
        }
    }

    @Test void fullCampaignProvidesEnoughUniqueRingsForEveryPowerAndUpgrade() {
        int ringCount = Level.campaign().stream().mapToInt(level -> level.rings.size()).sum();
        GameProgress budget = new GameProgress(ringCount, Set.of(), Set.of(), Set.of());
        for (var item : AbilityShop.Item.values()) {
            var result = AbilityShop.purchase(budget, item);
            assertEquals(AbilityShop.Status.PURCHASED, result.status(), item.name());
            budget = result.progress();
        }
        assertEquals(7, budget.unlockedAbilities().size());
        assertTrue(budget.balance() >= 0);
    }

    @Test void poolScoreIsDistinctDeterministicQuietAndLoopsSmoothly() {
        byte[] pools = AudioSamples.music(GameAudio.Music.POOLS);
        assertArrayEquals(pools, AudioSamples.music(GameAudio.Music.POOLS));
        assertEquals(16 * AudioSamples.SAMPLE_RATE * 2, pools.length);
        for (var other : List.of(GameAudio.Music.NIGHT, GameAudio.Music.SUNRISE, GameAudio.Music.SPACE)) {
            assertFalse(Arrays.equals(pools, AudioSamples.music(other)));
        }
        int peak = 0;
        for (int i = 0; i < pools.length; i += 2) peak = Math.max(peak, Math.abs(sample(pools, i)));
        assertTrue(peak > 1000);
        assertTrue(peak < 10000);
        assertEquals(0, sample(pools, 0));
        assertEquals(0, sample(pools, pools.length - 2));
    }

    @Test void indoorSceneryAndThemeAreRenderedAndSelectedWithoutChangingPhysics() throws Exception {
        var themes = new CopyOnWriteArrayList<GameAudio.Music>();
        var worker = Executors.newSingleThreadExecutor();
        GameAudio audio = new GameAudio(() -> new GameAudio.Output() {
            public void play(GameAudio.Effect effect) { }
            public void theme(GameAudio.Music theme) { themes.add(theme); }
            public void music(boolean playing) { }
            public void silence() { }
            public void close() { }
        }, worker);
        try {
            SwingUtilities.invokeAndWait(() -> {
                GameSession session = new GameSession(Level.campaign(), new GameProgress(0, Set.of(),
                    Set.of(GameProgress.Ability.DURABILITY, GameProgress.Ability.ROCKET),
                    Set.of("level-01", "level-02", "level-03")));
                assertTrue(session.selectLevel(3));
                var model = session.current();
                model.state = GameModel.State.RUNNING;
                model.durability();
                model.fireRocket();
                var panel = new GamePanel(session, () -> audio, ignored -> { });
                panel.setSize(GamePanel.WIDTH, GamePanel.HEIGHT);
                panel.addNotify();
                try {
                    render(panel, "pools-running");
                    double x = model.x;
                    double remaining = model.durabilityTimeRemaining();
                    render(panel, "pools-running");
                    assertEquals(x, model.x);
                    assertEquals(remaining, model.durabilityTimeRemaining());
                    panel.getActionMap().get("pressed F").actionPerformed(null);
                    render(panel, "pools-selection");
                } finally { panel.removeNotify(); }
            });
        } finally { audio.close(); }
        assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue(themes.contains(GameAudio.Music.POOLS));
        // Scenery itself has a continuous closed roof and a substantial water surface.
        var image = new BufferedImage(960, 540, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { PoolScenery.draw(g, 0); } finally { g.dispose(); }
        Color roof = new Color(image.getRGB(700, 40));
        assertTrue(roof.getRed() < 100);
        int waterPixels = 0;
        for (int y = 350; y < 420; y++) for (int x = 0; x < 960; x++) {
            Color pixel = new Color(image.getRGB(x, y));
            if (pixel.getBlue() > pixel.getRed() + 50) waterPixels++;
        }
        assertTrue(waterPixels > 10000);
    }

    private int sample(byte[] bytes, int offset) {
        return (short) ((bytes[offset] & 255) | bytes[offset + 1] << 8);
    }

    private void render(GamePanel panel, String name) {
        var image = new BufferedImage(960, 540, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try { panel.paint(graphics); } finally { graphics.dispose(); }
        try {
            Path path = Path.of("target/render-review/" + name + ".png");
            Files.createDirectories(path.getParent());
            ImageIO.write(image, "png", path.toFile());
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
}
