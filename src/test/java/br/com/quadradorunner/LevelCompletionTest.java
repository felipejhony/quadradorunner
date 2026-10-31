package br.com.quadradorunner;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LevelCompletionTest {
    @Test void summaryCountsOnlyNewRingsInCurrentAttemptAndRecordsCompletion() {
        Ring ring = new Ring("new", 110, Level.GROUND - 30);
        GameProgress initial = new GameProgress(5, Set.of(new GameProgress.RingId("a", "old")),
            Set.of(GameProgress.Ability.DASH), Set.of("earlier"));
        GameModel model = new GameModel(new Level("a", List.of(), List.of(), List.of(ring)), initial);
        model.state = GameModel.State.RUNNING;
        model.update(FixedStepLoop.STEP);
        win(model);
        assertEquals(new GameModel.LevelSummary("a", 1, 6), model.levelSummary());
        assertEquals(Set.of("earlier", "a"), model.progress().completedLevelIds());
        assertEquals(initial.unlockedAbilities(), model.progress().unlockedAbilities());
        GameProgress won = model.progress();
        model.update(FixedStepLoop.STEP);
        assertSame(won, model.progress());
        assertEquals(6, model.progress().balance());
    }

    @Test void deathAndRestartResetAttemptCountButPreserveLifetimeRewards() {
        Ring first = new Ring("one", 110, Level.GROUND - 30);
        Ring second = new Ring("two", 210, Level.GROUND - 30);
        GameModel model = new GameModel(new Level("a", List.of(), List.of(), List.of(first, second)));
        model.state = GameModel.State.RUNNING;
        model.update(FixedStepLoop.STEP);
        model.state = GameModel.State.LOST;
        assertThrows(IllegalStateException.class, model::levelSummary);
        model.restart();
        model.state = GameModel.State.RUNNING;
        model.x = 200;
        model.update(FixedStepLoop.STEP);
        win(model);
        assertEquals(new GameModel.LevelSummary("a", 1, 2), model.levelSummary());
        GameProgress previousWin = model.progress();
        model.restart();
        assertThrows(IllegalStateException.class, model::levelSummary);
        win(model);
        assertEquals(new GameModel.LevelSummary("a", 0, 2), model.levelSummary());
        assertSame(previousWin, model.progress());
    }

    @Test void lastStepCollectionIsIncludedAndCompletedLevelPersists(@TempDir Path directory) throws Exception {
        Ring ring = new Ring("finish", Level.FINISH - 20, Level.GROUND - 30);
        GameModel model = new GameModel(new Level("a", List.of(), List.of(), List.of(ring)));
        win(model);
        assertEquals(new GameModel.LevelSummary("a", 1, 1), model.levelSummary());
        Path file = directory.resolve("progress.properties");
        new GameProgressStore(file).save(model.progress());
        GameProgress loaded = new GameProgressStore(file).load();
        assertEquals(model.progress(), loaded);
        assertTrue(loaded.completedLevelIds().contains("a"));
        GameModel restored = new GameModel(model.level, loaded);
        win(restored);
        assertEquals(new GameModel.LevelSummary("a", 0, 1), restored.levelSummary());
    }

    @Test void fatalCollisionNeverMarksLevelCompletedOrProvidesSummary() {
        GameModel model = new GameModel(new Level("a",
            List.of(Level.obstacle(Level.FINISH, 40, 80)), List.of(), List.of()));
        model.lives = 1;
        win(model);
        assertEquals(GameModel.State.LOST, model.state);
        assertTrue(model.progress().completedLevelIds().isEmpty());
        assertThrows(IllegalStateException.class, model::levelSummary);
    }

    private void win(GameModel model) {
        model.state = GameModel.State.RUNNING;
        model.x = Level.FINISH - 1;
        model.update(FixedStepLoop.STEP);
    }
}
