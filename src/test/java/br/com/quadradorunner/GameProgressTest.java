package br.com.quadradorunner;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static br.com.quadradorunner.GameProgress.Ability.*;
import static org.junit.jupiter.api.Assertions.*;

class GameProgressTest {
    @Test void newProgressStartsWithoutBalanceCollectionsAbilitiesOrCompletedLevels() {
        GameProgress progress = new GameProgress();
        assertEquals(0, progress.balance());
        assertTrue(progress.collectedRings().isEmpty());
        assertTrue(progress.unlockedAbilities().isEmpty());
        assertTrue(progress.completedLevelIds().isEmpty());
    }

    @Test void representsBalanceSeparatelyFromCollectionHistoryAndAbilities() {
        Set<GameProgress.RingId> rings = Set.of(new GameProgress.RingId("level-01", "ring-01"),
            new GameProgress.RingId("level-02", "ring-01"));
        GameProgress progress = new GameProgress(1, rings, Set.of(DASH, GLIDE), Set.of("level-01"));
        assertEquals(1, progress.balance());
        assertEquals(rings, progress.collectedRings());
        assertEquals(Set.of(DASH, GLIDE), progress.unlockedAbilities());
        assertEquals(Set.of("level-01"), progress.completedLevelIds());
        assertFalse(progress.completedLevelIds().contains("level-02"));
        // Spent balance does not imply removing the history of collected rings.
        GameProgress spent = new GameProgress(0, rings, Set.of(DASH), Set.of("level-01"));
        assertEquals(rings, spent.collectedRings());
    }

    @Test void ringIdentityIncludesLevelAndDoesNotDependOnConcatenation() {
        GameProgress.RingId first = new GameProgress.RingId("level-01", "ring-01");
        assertEquals(first, new GameProgress.RingId("level-01", "ring-01"));
        assertNotEquals(first, new GameProgress.RingId("level-02", "ring-01"));
        assertNotEquals(first, new GameProgress.RingId("level-01", "ring-02"));
        assertNotEquals(new GameProgress.RingId("a:b", "c"), new GameProgress.RingId("a", "b:c"));
        Set<GameProgress.RingId> rings = new HashSet<>();
        rings.add(first);
        rings.add(new GameProgress.RingId("level-01", "ring-01"));
        rings.add(new GameProgress.RingId("level-02", "ring-01"));
        assertEquals(2, rings.size());
    }

    @Test void defensivelyCopiesAllMutableInputSets() {
        Set<GameProgress.RingId> rings = new HashSet<>();
        Set<GameProgress.Ability> abilities = new HashSet<>();
        Set<String> levels = new HashSet<>();
        GameProgress progress = new GameProgress(0, rings, abilities, levels);
        rings.add(new GameProgress.RingId("level-01", "ring-01"));
        abilities.add(DASH);
        levels.add("level-01");
        assertEquals(new GameProgress(), progress);
    }

    @Test void returnedSetsCannotModifyProgress() {
        GameProgress progress = new GameProgress(1,
            Set.of(new GameProgress.RingId("level-01", "ring-01")), Set.of(DASH), Set.of("level-01"));
        assertThrows(UnsupportedOperationException.class, () -> progress.collectedRings().clear());
        assertThrows(UnsupportedOperationException.class, () -> progress.unlockedAbilities().clear());
        assertThrows(UnsupportedOperationException.class, () -> progress.completedLevelIds().clear());
    }

    @Test void rejectsNegativeBalance() {
        assertThrows(IllegalArgumentException.class,
            () -> new GameProgress(-1, Set.of(), Set.of(), Set.of()));
    }

    @Test void rejectsMissingCollectionsAndNullElements() {
        assertThrows(NullPointerException.class, () -> new GameProgress(0, null, Set.of(), Set.of()));
        assertThrows(NullPointerException.class, () -> new GameProgress(0, Set.of(), null, Set.of()));
        assertThrows(NullPointerException.class, () -> new GameProgress(0, Set.of(), Set.of(), null));
        Set<GameProgress.RingId> rings = new HashSet<>();
        rings.add(null);
        Set<GameProgress.Ability> abilities = new HashSet<>();
        abilities.add(null);
        Set<String> levels = new HashSet<>();
        levels.add(null);
        assertThrows(NullPointerException.class, () -> new GameProgress(0, rings, Set.of(), Set.of()));
        assertThrows(NullPointerException.class, () -> new GameProgress(0, Set.of(), abilities, Set.of()));
        assertThrows(NullPointerException.class, () -> new GameProgress(0, Set.of(), Set.of(), levels));
    }

    @Test void rejectsMissingAndBlankIdentifiersWithoutNormalizingValidOnes() {
        assertThrows(NullPointerException.class, () -> new GameProgress.RingId(null, "ring-01"));
        assertThrows(NullPointerException.class, () -> new GameProgress.RingId("level-01", null));
        for (String invalid : new String[]{"", " ", "\t\n"}) {
            assertThrows(IllegalArgumentException.class, () -> new GameProgress.RingId(invalid, "ring-01"));
            assertThrows(IllegalArgumentException.class, () -> new GameProgress.RingId("level-01", invalid));
            assertThrows(IllegalArgumentException.class,
                () -> new GameProgress(0, Set.of(), Set.of(), Set.of(invalid)));
        }
        assertEquals("Level-01", new GameProgress.RingId("Level-01", "Ring-01").levelId());
        assertNotEquals(new GameProgress.RingId("Level-01", "Ring-01"),
            new GameProgress.RingId("level-01", "Ring-01"));
    }

    @Test void snapshotsWithTheSameValuesAreEqualRegardlessOfSetOrder() {
        GameProgress first = new GameProgress(0, Set.of(), Set.of(DASH, GLIDE), Set.of("a", "b"));
        GameProgress second = new GameProgress(0, Set.of(), Set.of(GLIDE, DASH), Set.of("b", "a"));
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }
}
