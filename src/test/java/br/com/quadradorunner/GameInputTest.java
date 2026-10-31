package br.com.quadradorunner;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static br.com.quadradorunner.GameInput.Key.*;

class GameInputTest {
    @Test void jumpAliasesRequireAllHeldJumpKeysToBeReleased() {
        GameInput input = new GameInput();
        assertTrue(input.press(SPACE));
        assertFalse(input.press(SPACE));
        assertFalse(input.press(UP));
        input.release(UP);
        assertFalse(input.press(SPACE));
        assertFalse(input.press(UP));
        input.release(SPACE);
        input.release(UP);
        assertTrue(input.press(UP));
    }

    @Test void everyCommandIgnoresRepeatAndFocusLossClearsHeldKeys() {
        GameInput input = new GameInput();
        for (GameInput.Key key : GameInput.Key.values()) {
            input.clear();
            assertTrue(input.press(key));
            assertFalse(input.press(key));
            input.release(key);
            assertTrue(input.press(key));
            input.clear();
            assertTrue(input.press(key));
        }
    }
}
