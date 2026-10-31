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
    private boolean shopOpen;

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
    Level levelAt(int target) { return levels.get(target); }

    boolean selectLevel(int target) {
        if (!isLevelUnlocked(target)) return false;
        GameProgress progress = current.progress();
        index = target;
        shopOpen = false;
        current = new GameModel(levels.get(index), progress);
        return true;
    }

    boolean isLevelUnlocked(int target) {
        if (target < 0 || target >= levels.size()) return false;
        for (int i = 0; i < target; i++) {
            if (!current.progress().completedLevelIds().contains(levels.get(i).id)) return false;
        }
        return current.progress().unlockedAbilities().containsAll(levels.get(target).requiredAbilities);
    }

    void reset() {
        index = 0;
        shopOpen = false;
        current = new GameModel(levels.get(0), new GameProgress());
    }

    boolean hasNextLevel() { return index + 1 < levels.size(); }

    Set<GameProgress.Ability> missingNextAbilities() {
        if (!hasNextLevel()) return Set.of();
        Set<GameProgress.Ability> missing = new HashSet<>(levels.get(index + 1).requiredAbilities);
        missing.removeAll(current.progress().unlockedAbilities());
        return Set.copyOf(missing);
    }

    boolean isShopOpen() { return shopOpen; }

    boolean openShop() {
        if (current.state != GameModel.State.WON) return false;
        shopOpen = true;
        return true;
    }

    void closeShop() { shopOpen = false; }

    AbilityShop.Status buy(AbilityShop.Item item) {
        Objects.requireNonNull(item, "item");
        if (!shopOpen) return AbilityShop.Status.UNAVAILABLE;
        return current.buy(item);
    }

    boolean canAdvance() {
        return !shopOpen && current.state == GameModel.State.WON && isLevelUnlocked(index + 1);
    }

    boolean advance() {
        if (!canAdvance()) return false;
        current = new GameModel(levels.get(++index), current.progress());
        return true;
    }

    void replay() {
        closeShop();
        current.restart();
    }
}
