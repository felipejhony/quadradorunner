package br.com.quadradorunner;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DamageFeedbackTest {
    @Test void blinksThenRestoresVisibilityAndExpires() {
        DamageFeedback feedback = new DamageFeedback();
        assertTrue(feedback.playerVisible());
        feedback.start(100, 100);
        assertTrue(feedback.active());
        assertFalse(feedback.playerVisible());
        feedback.update(0.1);
        assertTrue(feedback.playerVisible());
        feedback.update(0.1);
        assertFalse(feedback.playerVisible());
        feedback.update(1);
        assertFalse(feedback.active());
        assertTrue(feedback.playerVisible());
    }

    @Test void fragmentsMoveFadeAndDisappearWithoutChangingGraphicsTransform() {
        DamageFeedback feedback = new DamageFeedback();
        feedback.start(100, 100);
        BufferedImage first = render(feedback);
        feedback.update(0.4);
        BufferedImage next = render(feedback);
        assertNotEquals(first.getRGB(110, 100), next.getRGB(110, 100));
        feedback.update(1);
        BufferedImage expired = render(feedback);
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 200; x++) assertEquals(0, expired.getRGB(x, y));
        }
    }

    private BufferedImage render(DamageFeedback feedback) {
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            var transform = g.getTransform();
            feedback.draw(g);
            assertEquals(transform, g.getTransform());
        } finally { g.dispose(); }
        return image;
    }
}
