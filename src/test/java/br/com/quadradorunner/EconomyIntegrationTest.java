package br.com.quadradorunner;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Economy across model lifecycles, without adding future level-selection screens. */
class EconomyIntegrationTest {
    @TempDir Path directory;

    @Test void completingAndReplayingLevelNeverCreditsItsRingsTwice() throws Exception {
        Level level = level("a", ring("one", 110), ring("two", 260));
        GameModel first = running(level, new GameProgress());
        runToFinish(first);
        assertEquals(2, first.progress().balance());
        assertEquals(2, first.progress().collectedRings().size());
        GameProgress saved = checkpoint(first.progress());

        GameModel replay = running(level, saved);
        runToFinish(replay);
        assertSame(saved, replay.progress());
        assertEquals(saved, checkpoint(replay.progress()));
        replay.restart();
        replay.state = GameModel.State.RUNNING;
        runToFinish(replay);
        assertSame(saved, replay.progress());
    }

    @Test void visitsBetweenLevelsPreserveBalanceAndOnlyCreditPreviouslyUncollectedPairs() throws Exception {
        Ring shared = ring("shared", 110);
        Ring remaining = ring("remaining", 300);
        Level a = level("a", shared, remaining);
        Level b = level("b", shared);
        GameModel firstA = running(a, new GameProgress());
        step(firstA);
        assertEquals(1, firstA.progress().balance());
        assertFalse(firstA.isRingCollected(remaining));

        GameModel firstB = running(b, checkpoint(firstA.progress()));
        assertFalse(firstB.isRingCollected(shared));
        step(firstB);
        assertEquals(2, firstB.progress().balance());

        GameModel secondA = running(a, checkpoint(firstB.progress()));
        assertTrue(secondA.isRingCollected(shared));
        assertFalse(secondA.isRingCollected(remaining));
        runToFinish(secondA);
        assertEquals(3, secondA.progress().balance());

        GameProgress saved = checkpoint(secondA.progress());
        for (Level revisit : List.of(b, a, b, a)) {
            GameModel model = running(revisit, saved);
            runToFinish(model);
            assertEquals(saved.balance(), model.progress().balance());
            assertEquals(saved.collectedRings(), model.progress().collectedRings());
            assertEquals(saved.unlockedAbilities(), model.progress().unlockedAbilities());
            var completed = new java.util.HashSet<>(saved.completedLevelIds());
            completed.add(revisit.id);
            assertEquals(completed, model.progress().completedLevelIds());
            saved = checkpoint(model.progress());
        }
        assertEquals(Set.of(new GameProgress.RingId("a", "shared"),
            new GameProgress.RingId("a", "remaining"), new GameProgress.RingId("b", "shared")),
            saved.collectedRings());
        assertEquals(3, saved.balance());
    }

    @Test void actualDeathAndRetryPreserveRewardAndAllowOnlyTheMissedRing() throws Exception {
        Ring first = ring("one", 110);
        Ring missed = ring("two", 450);
        Level level = new Level("a", List.of(Level.obstacle(200, 40, 40)), List.of(), List.of(first, missed));
        GameModel model = running(level, new GameProgress());
        for (int i = 0; i < 500 && model.state == GameModel.State.RUNNING; i++) step(model);
        assertEquals(GameModel.State.LOST, model.state);
        assertEquals(1, model.progress().balance());
        assertFalse(model.isRingCollected(missed));
        GameProgress beforeRetry = checkpoint(model.progress());

        GameModel retry = running(level, beforeRetry);
        retry.restart();
        assertEquals(beforeRetry, retry.progress());
        retry.state = GameModel.State.RUNNING;
        while (retry.x < 140) step(retry);
        assertTrue(retry.jump()); // Clear the obstacle on this attempt.
        runToFinish(retry);
        assertEquals(2, retry.progress().balance());
        assertTrue(retry.isRingCollected(first));
        assertTrue(retry.isRingCollected(missed));
        assertEquals(retry.progress(), checkpoint(retry.progress()));
    }

    @Test void ringCollectedOnWinningStepIsCreditedOnceWithNoFinishBonus() {
        Ring finalRing = ring("finish", Level.FINISH - 20);
        GameModel model = running(level("a", finalRing), new GameProgress());
        model.x = Level.FINISH - 1;
        step(model);
        assertEquals(GameModel.State.WON, model.state);
        assertEquals(1, model.progress().balance());
        GameProgress won = model.progress();
        for (int i = 0; i < 120; i++) step(model);
        assertSame(won, model.progress());
        model.restart();
        model.state = GameModel.State.RUNNING;
        model.x = Level.FINISH - 1;
        step(model);
        assertEquals(GameModel.State.WON, model.state);
        assertSame(won, model.progress());
    }

    @Test void loadedBalanceIsNotRecomputedFromCollectionHistoryOrCompletedLevels() throws Exception {
        // Represents an existing snapshot with already spent balance; no purchase system is introduced.
        GameProgress spent = new GameProgress(0,
            Set.of(new GameProgress.RingId("a", "one"), new GameProgress.RingId("b", "one")),
            Set.of(GameProgress.Ability.DASH), Set.of("a", "b"));
        GameProgress saved = checkpoint(spent);
        for (String id : List.of("a", "b", "a")) {
            GameModel model = running(level(id, ring("one", 110)), saved);
            runToFinish(model);
            model.restart();
            assertSame(saved, model.progress());
            assertEquals(spent.unlockedAbilities(), model.progress().unlockedAbilities());
            assertEquals(spent.completedLevelIds(), model.progress().completedLevelIds());
            saved = checkpoint(model.progress());
            assertEquals(0, saved.balance());
        }
        GameModel newRing = running(level("b", ring("one", 110), ring("new", 110)), saved);
        step(newRing);
        assertEquals(1, newRing.progress().balance());
        assertEquals(3, newRing.progress().collectedRings().size());
    }

    @Test void autosaveAcrossVisitsPersistsLatestBalanceWithoutDuplicateCallbacks() throws Exception {
        Path file = directory.resolve("progress.properties");
        var worker = Executors.newSingleThreadExecutor();
        ProgressAutosave autosave = new ProgressAutosave(new GameProgressStore(file), worker);
        var failures = new CopyOnWriteArrayList<Exception>();
        var snapshots = new CopyOnWriteArrayList<GameProgress>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                GameProgress progress = new GameProgress();
                for (String id : List.of("a", "b", "a", "b")) {
                    GameModel model = running(level(id, ring("shared", 110)), progress);
                    GamePanel panel = new GamePanel(model, GameAudio::new, snapshot -> {
                        snapshots.add(snapshot);
                        autosave.save(snapshot, failure -> {
                            if (failure != null) failures.add(failure);
                        });
                    });
                    panel.updateGame(FixedStepLoop.STEP);
                    panel.updateGame(FixedStepLoop.STEP);
                    progress = model.progress();
                }
            });
        } finally { autosave.close(); }
        assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS));
        assertTrue(failures.isEmpty(), failures.toString());
        assertEquals(List.of(1, 2), snapshots.stream().map(GameProgress::balance).toList());
        GameProgress reloaded = new GameProgressStore(file).load();
        assertEquals(snapshots.get(1), reloaded);
        assertEquals(2, reloaded.balance());
        assertEquals(2, reloaded.collectedRings().size());
    }

    private Ring ring(String id, double x) { return new Ring(id, x, Level.GROUND - 30); }

    private Level level(String id, Ring... rings) {
        return new Level(id, List.of(), List.of(), List.of(rings));
    }

    private GameModel running(Level level, GameProgress progress) {
        GameModel model = new GameModel(level, progress);
        model.state = GameModel.State.RUNNING;
        return model;
    }

    private void step(GameModel model) { model.update(FixedStepLoop.STEP); }

    private void runToFinish(GameModel model) {
        for (int i = 0; i < 5000 && model.state == GameModel.State.RUNNING; i++) step(model);
        assertEquals(GameModel.State.WON, model.state, "Failed at x=" + model.x);
    }

    private GameProgress checkpoint(GameProgress progress) throws Exception {
        Path file = directory.resolve("progress.properties");
        new GameProgressStore(file).save(progress);
        return new GameProgressStore(file).load();
    }
}
