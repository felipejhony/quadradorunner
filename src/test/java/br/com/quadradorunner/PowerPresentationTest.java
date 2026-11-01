package br.com.quadradorunner;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PowerPresentationTest {
    @Test void eachPurchaseRaisesOtherPricesAndReloadPreservesThem(@TempDir Path directory) throws Exception {
        GameProgress original = new GameProgress(24, Set.of(), Set.of(), Set.of());
        GameProgress first = AbilityShop.purchase(original, AbilityShop.Item.DASH).progress();
        assertEquals(20, first.balance());
        assertEquals(9, AbilityShop.price(first, AbilityShop.Item.GLIDE));
        assertEquals(8, AbilityShop.price(first, AbilityShop.Item.DOUBLE_JUMP));
        GameProgressStore store = new GameProgressStore(directory.resolve("save.properties"));
        store.save(first);
        assertEquals(9, AbilityShop.price(store.load(), AbilityShop.Item.GLIDE));
        var second = AbilityShop.purchase(store.load(), AbilityShop.Item.GLIDE);
        assertEquals(AbilityShop.Status.PURCHASED, second.status());
        assertEquals(11, second.progress().balance());
        assertEquals(11, AbilityShop.price(second.progress(), AbilityShop.Item.DOUBLE_JUMP));
        assertEquals(0, AbilityShop.purchase(second.progress(), AbilityShop.Item.DOUBLE_JUMP).progress().balance());
        GameProgress poor = new GameProgress(6, Set.of(), first.unlockedAbilities(), Set.of());
        assertSame(poor, AbilityShop.purchase(poor, AbilityShop.Item.GLIDE).progress());
    }

    @Test void hudDisplaysOnlyPurchasedIcons() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage empty = hud(Set.of());
            for (GameProgress.Ability ability : GameProgress.Ability.values()) {
                if (ability.prerequisite() != null) continue;
                BufferedImage owned = hud(Set.of(ability));
                assertNotEquals(empty.getRGB(22, 100), owned.getRGB(22, 100));
                assertEquals(empty.getRGB(64, 100), owned.getRGB(64, 100));
            }
            BufferedImage all = hud(Set.of(GameProgress.Ability.values()));
            for (int x : new int[]{22, 64, 106, 148, 190}) assertNotEquals(empty.getRGB(x, 100), all.getRGB(x, 100));
        });
    }

    private BufferedImage hud(Set<GameProgress.Ability> abilities) {
        GameModel model = new GameModel(new Level(List.of(), List.of()),
            new GameProgress(0, Set.of(), abilities, Set.of()));
        model.state = GameModel.State.RUNNING;
        GamePanel panel = new GamePanel(model);
        panel.setSize(GamePanel.WIDTH, GamePanel.HEIGHT);
        BufferedImage image = new BufferedImage(GamePanel.WIDTH, GamePanel.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try { panel.paint(graphics); } finally { graphics.dispose(); }
        return image;
    }

    @Test void sunriseScoreDiffersFromNightAndThemeChangesRunOnAudioWorker() throws Exception {
        byte[] night = AudioSamples.music(GameAudio.Music.NIGHT);
        byte[] sunrise = AudioSamples.music(GameAudio.Music.SUNRISE);
        assertEquals(night.length, sunrise.length);
        assertFalse(Arrays.equals(night, sunrise));
        var events = new CopyOnWriteArrayList<String>();
        var worker = Executors.newSingleThreadExecutor();
        GameAudio audio = new GameAudio(() -> new GameAudio.Output() {
            public void play(GameAudio.Effect effect) { }
            public void theme(GameAudio.Music theme) { events.add(theme.name()); }
            public void music(boolean playing) { events.add("playing:" + playing); }
            public void silence() { }
            public void close() { events.add("closed"); }
        }, worker);
        audio.configure(false, false, GameAudio.Music.SUNRISE);
        audio.configure(true, false, GameAudio.Music.SUNRISE);
        audio.configure(false, false, GameAudio.Music.NIGHT);
        audio.close();
        assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(List.of("SUNRISE", "playing:true", "SUNRISE", "playing:false", "NIGHT", "playing:true", "closed"), events);
    }
}
