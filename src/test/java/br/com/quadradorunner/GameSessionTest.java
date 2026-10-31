package br.com.quadradorunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameSessionTest {
    @Test void advanceTransfersPermanentProgressAndStartsNextLevelFresh() {
        Ring shared = new Ring("one", 110, Level.GROUND - 30);
        Level a = new Level("a", List.of(), List.of(), List.of(shared));
        Level b = new Level("b", List.of(), List.of(), List.of(shared));
        GameProgress initial = new GameProgress(4, Set.of(), Set.of(GameProgress.Ability.GLIDE), Set.of());
        GameSession session = new GameSession(List.of(a, b), initial);
        session.current().state = GameModel.State.RUNNING;
        session.current().update(FixedStepLoop.STEP);
        win(session.current());
        GameProgress completed = session.current().progress();
        assertTrue(session.canAdvance());
        assertTrue(session.advance());
        GameModel next = session.current();
        assertEquals(2, session.levelNumber());
        assertEquals(2, session.levelCount());
        assertEquals(GameModel.State.READY, next.state);
        assertSame(completed, next.progress());
        assertEquals(GameModel.START_X, next.x);
        assertEquals(0, next.powerRemaining);
        assertFalse(next.isRingCollected(shared)); // Same local ID, different level.
        next.state = GameModel.State.RUNNING;
        next.update(FixedStepLoop.STEP);
        win(next);
        assertEquals(new GameModel.LevelSummary("b", 1, 6), next.levelSummary());
        assertEquals(Set.of("a", "b"), next.progress().completedLevelIds());
        assertFalse(session.canAdvance());
        assertFalse(session.advance());
        assertSame(next, session.current());
    }

    @Test void advanceIsBlockedBeforeVictoryAndAtEndOfSequence() {
        Level first = level("a");
        GameSession session = new GameSession(List.of(first, level("b")), new GameProgress());
        GameModel model = session.current();
        for (GameModel.State state : List.of(GameModel.State.READY, GameModel.State.RUNNING,
                GameModel.State.PAUSED, GameModel.State.LOST)) {
            model.state = state;
            assertFalse(session.advance());
            assertSame(model, session.current());
        }
        GameSession only = new GameSession(List.of(first), new GameProgress());
        win(only.current());
        assertFalse(only.canAdvance());
        assertFalse(only.advance());
        assertEquals(GameModel.State.WON, only.current().state);
    }

    @Test void replayKeepsCurrentLevelAndAllProgress() {
        GameSession session = new GameSession(List.of(level("a"), level("b")), new GameProgress());
        win(session.current());
        session.advance();
        win(session.current());
        GameModel last = session.current();
        GameProgress saved = last.progress();
        session.replay();
        assertSame(last, session.current());
        assertEquals(2, session.levelNumber());
        assertEquals(GameModel.State.READY, last.state);
        assertSame(saved, last.progress());
        win(last);
        assertEquals(0, last.levelSummary().newRings());
        assertSame(saved, last.progress());
    }

    @Test void validatesSequenceAndCopiesItsLevelList() {
        assertThrows(IllegalArgumentException.class, () -> new GameSession(List.of(), new GameProgress()));
        assertThrows(IllegalArgumentException.class,
            () -> new GameSession(List.of(level("a"), level("a")), new GameProgress()));
        List<Level> levels = new ArrayList<>(List.of(level("a"), level("b")));
        GameSession session = new GameSession(levels, new GameProgress());
        levels.clear();
        assertEquals(2, session.levelCount());
    }

    private Level level(String id) { return new Level(id, List.of(), List.of(), List.of()); }

    private void win(GameModel model) {
        model.state = GameModel.State.RUNNING;
        model.x = Level.FINISH - 1;
        model.update(FixedStepLoop.STEP);
    }
}
