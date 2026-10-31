package br.com.quadradorunner;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class RingProgressTest {
    @Test void savedRingDoesNotReappearInNewModelLoadedFromDisk(@TempDir Path directory) throws Exception {
        Ring ring = new Ring("one", 100, 100);
        Level level = new Level("a", List.of(), List.of(), List.of(ring));
        Path file = directory.resolve("progress.properties");
        GameProgress saved = new GameProgress(1, Set.of(new GameProgress.RingId("a", "one")),
            Set.of(), Set.of());
        new GameProgressStore(file).save(saved);
        GameModel model = new GameModel(level, new GameProgressStore(file).load());
        assertTrue(model.isRingCollected(ring));
        assertEquals(saved, model.progress());
    }

    @Test void savedRingIdentityIsIndependentOfOrderAndPosition() {
        GameProgress saved = new GameProgress(1, Set.of(new GameProgress.RingId("a", "one")),
            Set.of(), Set.of());
        Ring first = new Ring("one", 100, 100);
        Ring second = new Ring("two", 200, 100);
        GameModel original = new GameModel(new Level("a", List.of(), List.of(), List.of(first, second)), saved);
        assertTrue(original.isRingCollected(first));
        assertFalse(original.isRingCollected(second));
        Ring moved = new Ring("one", 400, 300);
        GameModel reordered = new GameModel(new Level("a", List.of(), List.of(), List.of(second, moved)), saved);
        assertTrue(reordered.isRingCollected(moved));
        assertFalse(reordered.isRingCollected(second));
        GameModel otherLevel = new GameModel(new Level("b", List.of(), List.of(), List.of(first)), saved);
        assertFalse(otherLevel.isRingCollected(first));
    }

    @Test void restartPreservesSavedRingsBalanceAbilitiesAndCompletedLevels() {
        Ring ring = new Ring("one", 100, 100);
        GameProgress saved = new GameProgress(1, Set.of(new GameProgress.RingId("a", "one")),
            Set.of(GameProgress.Ability.DASH), Set.of("a"));
        GameModel model = new GameModel(new Level("a", List.of(), List.of(), List.of(ring)), saved);
        model.restart();
        assertEquals(saved, model.progress());
        assertTrue(model.isRingCollected(ring));
    }
}
