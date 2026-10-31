package br.com.quadradorunner;

import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PitTest {
    private Level level() {
        return new Level("pit", "Pit", Level.FINISH, List.of(), List.of(), List.of(),
            List.of(), List.of(new Level.Pit(500, 120)));
    }

    @Test void fallingReturnsToStartWithoutRestoringOrLosingLivesOrProgress() {
        GameProgress progress = new GameProgress(4, Set.of(new GameProgress.RingId("pit", "r")),
            Set.of(GameProgress.Ability.GLIDE), Set.of("previous"));
        GameModel model = new GameModel(level(), progress);
        model.lives = 2;
        model.state = GameModel.State.RUNNING;
        model.x = 505;
        model.update(FixedStepLoop.STEP);
        assertFalse(model.grounded);
        assertTrue(model.y > Level.GROUND - GameModel.SIZE);
        for (int i = 0; i < 120 && model.state == GameModel.State.RUNNING; i++) model.update(FixedStepLoop.STEP);
        assertEquals(GameModel.State.READY, model.state);
        assertEquals(GameModel.START_X, model.x);
        assertEquals(2, model.lives);
        assertEquals(progress, model.progress());
    }

    @Test void singleJumpCanCrossPitWithoutGlide() {
        GameModel model = new GameModel(level());
        model.x = 440;
        model.jump();
        for (int i = 0; i < 110; i++) model.update(FixedStepLoop.STEP);
        assertTrue(model.x > 620);
        assertTrue(model.grounded);
        assertEquals(3, model.lives);
        assertEquals(GameModel.State.RUNNING, model.state);
    }

    @Test void campaignHasEqualLengthsAndPitsOnlyFromSecondLevel() {
        List<Level> campaign = Level.campaign();
        assertTrue(campaign.get(0).pits.isEmpty());
        for (Level level : campaign) assertEquals(Level.FINISH, level.finish);
        for (int seed = 0; seed < 50; seed++) {
            Level source = campaign.get(1);
            Level level = Level.withRandomPits(source, new Random(seed));
            assertFalse(level.pits.isEmpty());
            for (Level.Pit pit : level.pits) {
                assertTrue(pit.width() >= 90 && pit.width() <= 130);
                assertTrue(pit.x() > 500 && pit.end() < level.finish - 200);
                assertTrue(level.obstacles.stream().noneMatch(shape ->
                    shape.getBounds2D().getMaxX() > pit.x() - 180
                    && shape.getBounds2D().getMinX() < pit.end() + 180));
                assertTrue(level.rings.stream().noneMatch(ring ->
                    ring.x() < pit.end() + 20 && ring.x() + Ring.DIAMETER + 20 > pit.x()));
            }
        }
        assertNotEquals(Level.withRandomPits(campaign.get(1), new Random(1)).pits,
            Level.withRandomPits(campaign.get(1), new Random(2)).pits);
    }
}
