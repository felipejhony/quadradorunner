package br.com.quadradorunner;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class FixedStepLoopTest {
    @Test void callbackTimingDoesNotChangeTrajectory() {
        GameModel a = new GameModel(new Level(List.of(), List.of()));
        GameModel b = new GameModel(new Level(List.of(), List.of()));
        a.jump();
        b.jump();
        FixedStepLoop first = new FixedStepLoop();
        FixedStepLoop second = new FixedStepLoop();
        for (int i = 0; i < 100; i++) first.advance(0.01, a::update);
        for (int i = 0; i < 20; i++) {
            second.advance(0.013, b::update);
            second.advance(0.037, b::update);
        }
        assertEquals(a.x, b.x);
        assertEquals(a.y, b.y);
        assertEquals(a.velocityY, b.velocityY);
    }

    @Test void catchesUpOrdinaryDelayCapsStallsAndResetDiscardsRemainder() {
        FixedStepLoop loop = new FixedStepLoop();
        int[] count = {0};
        loop.advance(0.2, dt -> { assertEquals(FixedStepLoop.STEP, dt); count[0]++; });
        assertEquals(24, count[0]);
        count[0] = 0;
        loop.advance(10, dt -> count[0]++);
        assertEquals(FixedStepLoop.MAX_STEPS, count[0]);
        loop.advance(FixedStepLoop.STEP / 2, dt -> fail());
        loop.reset();
        loop.advance(FixedStepLoop.STEP / 2, dt -> fail());
        assertThrows(IllegalArgumentException.class, () -> loop.advance(Double.NaN, dt -> fail()));
    }
}
