package br.com.quadradorunner;

import java.util.EnumSet;

/** One command per physical press. Jump aliases share one held action. */
final class GameInput {
    enum Key { SPACE, UP, RESTART, PAUSE, ESCAPE, MUTE, ADVANCE, SHOP, BUY_DASH, BUY_GLIDE, BUY_DOUBLE_JUMP, DOWN, LEFT, RIGHT, DASH, GLIDE, LEVELS }
    private final EnumSet<Key> held = EnumSet.noneOf(Key.class);

    boolean press(Key key) {
        boolean jumpHeld = held.contains(Key.SPACE) || held.contains(Key.UP);
        if (!held.add(key)) return false;
        return (key != Key.SPACE && key != Key.UP) || !jumpHeld;
    }

    void release(Key key) { held.remove(key); }
    void clear() { held.clear(); }
}
