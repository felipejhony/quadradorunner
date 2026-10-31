package br.com.quadradorunner;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Ordered level progression, preserving the current permanent snapshot. No Swing or I/O. */
final class GameSession {
    private final List<Level> levels;
    private int index;
    private GameModel current;

    GameSession(List<Level> levels, GameProgress progress) {
        this.levels = List.copyOf(levels);
        if (this.levels.isEmpty()) throw new IllegalArgumentException("A session needs at least one level");
        Set<String> ids = new HashSet<>();
        for (Level level : this.levels) {
            if (!ids.add(level.id)) throw new IllegalArgumentException("Duplicate level ID: " + level.id);
        }
        current = new GameModel(this.levels.get(0), progress);
    }

    GameSession(GameModel model) {
        current = Objects.requireNonNull(model, "model");
        levels = List.of(model.level);
    }

    GameModel current() { return current; }
    int levelNumber() { return index + 1; }
    int levelCount() { return levels.size(); }

    boolean canAdvance() {
        return current.state == GameModel.State.WON && index + 1 < levels.size();
    }

    boolean advance() {
        if (!canAdvance()) return false;
        current = new GameModel(levels.get(++index), current.progress());
        return true;
    }

    void replay() { current.restart(); }
}
