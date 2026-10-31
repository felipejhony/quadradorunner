package br.com.quadradorunner;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LevelTest {
    @Test void standardLevelHasStableExplicitUniqueRingIdsAndRandomObstacles() {
        Level first = Level.standard();
        Level second = Level.standard();
        assertEquals("level-01", first.id);
        assertTrue(first.obstacles.size() >= 18 && first.obstacles.size() <= 25);
        assertTrue(first.powerUps.isEmpty());
        assertEquals(12, first.rings.size());
        assertEquals(first.rings, second.rings);
        assertEquals(12, first.rings.stream().map(Ring::id).distinct().count());
        assertEquals("ring-01", first.rings.get(0).id());
        assertEquals("ring-12", first.rings.get(11).id());
        for (Ring ring : first.rings) {
            assertTrue(ring.x() + Ring.DIAMETER < Level.FINISH);
            assertTrue(ring.y() + Ring.DIAMETER < Level.GROUND);
            assertTrue(first.obstacles.stream().noneMatch(obstacle -> obstacle.intersects(ring.shape().getBounds2D())));
        }
    }

    @Test void rejectsDuplicateIdsWithinLevelButAllowsSameLocalIdInOtherLevel() {
        Ring ring = new Ring("ring-01", 100, 100);
        assertThrows(IllegalArgumentException.class,
            () -> new Level("a", List.of(), List.of(), List.of(ring, new Ring("ring-01", 200, 100))));
        assertDoesNotThrow(() -> new Level("a", List.of(), List.of(), List.of(ring)));
        assertDoesNotThrow(() -> new Level("b", List.of(), List.of(), List.of(ring)));
        assertThrows(NullPointerException.class, () -> new Level(null, List.of(), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Level(" ", List.of(), List.of(), List.of()));
    }

    @Test void ringListIsDefensivelyCopiedAndCannotBeChanged() {
        List<Ring> rings = new ArrayList<>(List.of(new Ring("ring-01", 100, 100)));
        Level level = new Level("a", List.of(), List.of(), rings);
        rings.clear();
        assertEquals(1, level.rings.size());
        assertThrows(UnsupportedOperationException.class, () -> level.rings.clear());
    }
}
