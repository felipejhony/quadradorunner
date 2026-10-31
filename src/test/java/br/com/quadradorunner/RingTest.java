package br.com.quadradorunner;

import java.awt.geom.Rectangle2D;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingTest {
    @Test void collisionUsesOuterDiskRatherThanBoundingBoxOrOutlineOnly() {
        Ring ring = new Ring("ring-01", 100, 100);
        assertTrue(ring.intersects(new Rectangle2D.Double(110, 110, 4, 4)));
        assertFalse(ring.intersects(new Rectangle2D.Double(100, 100, 1, 1)));
        assertFalse(ring.intersects(new Rectangle2D.Double(76, 100, 24, 24)));
        assertFalse(ring.intersects(new Rectangle2D.Double(200, 200, 34, 34)));
    }

    @Test void geometryCannotMutateEntityCoordinates() {
        Ring ring = new Ring("ring-01", 100, 100);
        ring.shape().setFrame(0, 0, 1, 1);
        assertEquals(100, ring.shape().getX());
        assertEquals(Ring.DIAMETER, ring.shape().getWidth());
    }

    @Test void rejectsInvalidIdsAndCoordinates() {
        assertThrows(NullPointerException.class, () -> new Ring(null, 0, 0));
        for (String id : new String[]{"", " ", "\t"}) {
            assertThrows(IllegalArgumentException.class, () -> new Ring(id, 0, 0));
        }
        for (double value : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new Ring("ring", value, 0));
            assertThrows(IllegalArgumentException.class, () -> new Ring("ring", 0, value));
        }
    }
}
