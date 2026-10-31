package br.com.quadradorunner;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LivesTest {
    private Level course() {
        return new Level("hits", List.of(Level.obstacle(200, 70, 80),
            Level.obstacle(650, 70, 80), Level.obstacle(1100, 70, 80)), List.of(), List.of());
    }

    @Test void threeDistinctCollisionsEndAttemptAndRestartRestoresLives() {
        GameModel model = new GameModel(course());
        model.state = GameModel.State.RUNNING;
        for (int i = 0; i < 3; i++) {
            model.x = 190 + i * 450;
            model.update(FixedStepLoop.STEP);
            assertEquals(2 - i, model.lives);
            assertEquals(i == 2 ? GameModel.State.LOST : GameModel.State.RUNNING, model.state);
            if (i < 2) {
                for (int step = 0; step < 125; step++) model.update(FixedStepLoop.STEP);
                assertEquals(2 - i, model.lives, "Same obstacle must not cause repeated damage");
            }
        }
        model.restart();
        assertEquals(3, model.lives);
        assertEquals(GameModel.State.READY, model.state);
    }

    @Test void finalLifeResetsEntireCampaign() {
        Level first = new Level("first", List.of(), List.of(), List.of());
        GameProgress saved = new GameProgress(9, Set.of(new GameProgress.RingId("first", "ring")),
            Set.of(GameProgress.Ability.DASH), Set.of("first"));
        GameSession session = new GameSession(List.of(first, course()), saved);
        assertTrue(session.selectLevel(1));
        GameModel model = session.current();
        model.state = GameModel.State.RUNNING;
        for (int i = 0; i < 600 && session.current() == model; i++) session.update(FixedStepLoop.STEP);
        assertNotSame(model, session.current());
        assertEquals(1, session.levelNumber());
        assertEquals(new GameProgress(), session.current().progress());
        assertEquals(3, session.current().lives);
        assertEquals(GameModel.State.READY, session.current().state);
    }

    @Test void newAttemptAndNewLevelRestoreLives() {
        GameSession session = new GameSession(List.of(course()), new GameProgress());
        session.current().lives = 1;
        session.replay();
        assertEquals(3, session.current().lives);
        session.current().lives = 1;
        session.selectLevel(0);
        assertEquals(3, session.current().lives);
    }
}
