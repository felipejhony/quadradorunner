package br.com.quadradorunner;

import java.util.Objects;
import java.util.Set;

/** Immutable permanent-progress snapshot, independent of a running game and storage. */
record GameProgress(int balance, Set<RingId> collectedRings, Set<Ability> unlockedAbilities,
                    Set<String> completedLevelIds) {
    enum Ability {
        DASH, GLIDE, DOUBLE_JUMP, DURABILITY, ROCKET, DASH_UPGRADE, DOUBLE_JUMP_UPGRADE;

        Ability prerequisite() {
            return switch (this) {
                case DASH_UPGRADE -> DASH;
                case DOUBLE_JUMP_UPGRADE -> DOUBLE_JUMP;
                default -> null;
            };
        }
    }

    /** A ring ID is scoped to its level; no string concatenation or positional index. */
    record RingId(String levelId, String ringId) {
        RingId {
            requireId(levelId, "levelId");
            requireId(ringId, "ringId");
        }
    }

    GameProgress() {
        this(0, Set.of(), Set.of(), Set.of());
    }

    GameProgress {
        if (balance < 0) throw new IllegalArgumentException("Balance cannot be negative");
        collectedRings = Set.copyOf(collectedRings);
        unlockedAbilities = Set.copyOf(unlockedAbilities);
        for (Ability ability : unlockedAbilities) {
            if (ability.prerequisite() != null && !unlockedAbilities.contains(ability.prerequisite())) {
                throw new IllegalArgumentException("Upgrade requires its base ability");
            }
        }
        completedLevelIds = Set.copyOf(completedLevelIds);
        for (String levelId : completedLevelIds) requireId(levelId, "completedLevelId");
    }

    private static void requireId(String id, String name) {
        Objects.requireNonNull(id, name);
        if (id.isBlank()) throw new IllegalArgumentException(name + " cannot be blank");
    }
}
