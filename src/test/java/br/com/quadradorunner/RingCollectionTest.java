package br.com.quadradorunner;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingCollectionTest {
    private Ring reachable() { return new Ring("one", 110, Level.GROUND - 30); }
    private Level level(Ring... rings) { return new Level("a", List.of(), List.of(), List.of(rings)); }
    private void step(GameModel model) { model.update(FixedStepLoop.STEP); }

    @Test void overlapCollectsOnlyOnceAndCreditsOneWithoutLosingOtherProgress() {
        GameProgress initial = new GameProgress(4, Set.of(new GameProgress.RingId("other", "old")),
            Set.of(GameProgress.Ability.DASH), Set.of("other"));
        Ring ring = reachable();
        GameModel model = new GameModel(level(ring), initial);
        model.state = GameModel.State.RUNNING;
        step(model);
        GameProgress collected = model.progress();
        for (int i = 0; i < 5; i++) step(model);
        assertSame(collected, model.progress());
        assertEquals(5, model.progress().balance());
        assertEquals(2, model.progress().collectedRings().size());
        assertEquals(initial.unlockedAbilities(), model.progress().unlockedAbilities());
        assertEquals(initial.completedLevelIds(), model.progress().completedLevelIds());
        assertEquals(1, initial.collectedRings().size());
        assertTrue(model.isRingCollected(ring));
    }

    @Test void savedRingCannotBeCollectedAgain() {
        Ring ring = reachable();
        GameProgress saved = new GameProgress(0, Set.of(new GameProgress.RingId("a", ring.id())),
            Set.of(), Set.of());
        GameModel model = new GameModel(level(ring), saved);
        model.state = GameModel.State.RUNNING;
        step(model);
        assertSame(saved, model.progress());
        assertEquals(0, model.progress().balance());
    }

    @Test void allOverlappingRingsWithDifferentIdsAreCollected() {
        Ring first = reachable();
        Ring second = new Ring("two", first.x(), first.y());
        GameModel model = new GameModel(level(first, second));
        model.state = GameModel.State.RUNNING;
        step(model);
        assertEquals(2, model.progress().balance());
        assertTrue(model.isRingCollected(first));
        assertTrue(model.isRingCollected(second));
    }

    @Test void collectionRequiresRunningStateAndActualIntersection() {
        for (GameModel.State state : GameModel.State.values()) {
            if (state == GameModel.State.RUNNING) continue;
            GameModel model = new GameModel(level(reachable()));
            model.state = state;
            step(model);
            assertEquals(new GameProgress(), model.progress());
        }
        GameModel distant = new GameModel(level(new Ring("far", 1000, 100)));
        distant.state = GameModel.State.RUNNING;
        step(distant);
        assertEquals(new GameProgress(), distant.progress());
    }

    @Test void fatalCollisionHasPrecedenceOverRingCollection() {
        Ring ring = reachable();
        Level level = new Level("a", List.of(Level.obstacle(110, 80, 80)), List.of(), List.of(ring));
        GameModel model = new GameModel(level);
        model.state = GameModel.State.RUNNING;
        model.lives = 1;
        step(model);
        assertEquals(GameModel.State.LOST, model.state);
        assertEquals(new GameProgress(), model.progress());
    }

    @Test void restartDoesNotRestoreNewlyCollectedRingsOrRemoveTheirReward() {
        Ring ring = reachable();
        GameModel model = new GameModel(level(ring));
        model.state = GameModel.State.RUNNING;
        step(model);
        GameProgress collected = model.progress();
        model.state = GameModel.State.LOST;
        model.restart();
        assertSame(collected, model.progress());
        assertTrue(model.isRingCollected(ring));
        assertEquals(1, model.progress().balance());
    }

    @Test void ringCollectionDoesNotGrantOrConsumeTemporaryDoubleJump() {
        GameModel model = new GameModel(level(reachable()));
        model.state = GameModel.State.RUNNING;
        step(model);
        assertEquals(0, model.powerRemaining);
        assertEquals(0, model.jumpsUsed);
        assertTrue(model.jump());
        assertFalse(model.jump());
    }

    @Test void maximumBalanceDoesNotOverflowOrConsumeRingWithoutReward() {
        Ring ring = reachable();
        GameProgress maximum = new GameProgress(Integer.MAX_VALUE, Set.of(), Set.of(), Set.of());
        GameModel model = new GameModel(level(ring), maximum);
        model.state = GameModel.State.RUNNING;
        step(model);
        assertSame(maximum, model.progress());
        assertFalse(model.isRingCollected(ring));
    }
}
