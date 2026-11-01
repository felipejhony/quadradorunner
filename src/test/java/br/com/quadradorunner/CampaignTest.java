package br.com.quadradorunner;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CampaignTest {
    @TempDir Path directory;

    @Test void campaignPreservesFirstLevelAndDeclaresDistinctSkillRoutes() {
        List<Level> levels = Level.campaign();
        assertEquals(List.of("level-01", "level-02", "level-03"), levels.stream().map(level -> level.id).toList());
        assertEquals(8400, levels.get(0).finish);
        assertTrue(levels.get(0).obstacles.size() >= 18 && levels.get(0).obstacles.size() <= 25);
        assertTrue(levels.get(0).powerUps.isEmpty());
        assertEquals(12, levels.get(0).rings.size());
        assertTrue(levels.get(0).requiredAbilities.isEmpty());
        assertTrue(levels.get(1).requiredAbilities.isEmpty());
        assertTrue(levels.get(2).requiredAbilities.isEmpty());
        assertEquals(6, levels.get(1).rings.size());
        for (Level level : levels) {
            assertEquals(level.rings.size(), level.rings.stream().map(Ring::id).distinct().count());
            for (Ring ring : level.rings) assertTrue(ring.x() + Ring.DIAMETER < level.finish);
        }
    }

    @Test void accessRequiresPrecedingCompletionWithoutPurchases() {
        GameSession fresh = new GameSession(Level.campaign(), new GameProgress());
        assertTrue(fresh.isLevelUnlocked(0));
        assertFalse(fresh.isLevelUnlocked(1));
        assertFalse(fresh.isLevelUnlocked(2));
        assertFalse(fresh.isLevelUnlocked(-1));
        assertFalse(fresh.isLevelUnlocked(3));
        GameSession purchasedOnly = new GameSession(Level.campaign(), progress(Set.of(),
            Set.of(GameProgress.Ability.DASH, GameProgress.Ability.GLIDE)));
        assertFalse(purchasedOnly.isLevelUnlocked(1));
        assertFalse(purchasedOnly.isLevelUnlocked(2));
        GameSession firstCompleted = new GameSession(Level.campaign(), progress(Set.of("level-01"), Set.of()));
        assertTrue(firstCompleted.isLevelUnlocked(1));
        GameSession dashReady = new GameSession(Level.campaign(), progress(Set.of("level-01"), Set.of(GameProgress.Ability.DASH)));
        assertTrue(dashReady.isLevelUnlocked(1));
        assertFalse(dashReady.isLevelUnlocked(2));
        GameSession missingGlide = new GameSession(Level.campaign(), progress(Set.of("level-01", "level-02"),
            Set.of(GameProgress.Ability.DASH)));
        assertTrue(missingGlide.isLevelUnlocked(2));
        GameSession skippedFirst = new GameSession(Level.campaign(), progress(Set.of("level-02"),
            Set.of(GameProgress.Ability.DASH, GameProgress.Ability.GLIDE)));
        assertFalse(skippedFirst.isLevelUnlocked(2));
        GameSession all = new GameSession(Level.campaign(), progress(Set.of("level-01", "level-02"),
            Set.of(GameProgress.Ability.DASH, GameProgress.Ability.GLIDE)));
        assertTrue(all.isLevelUnlocked(2));
    }

    @Test void actualCampaignCanBeFinishedWithDashForFallingObstacles() throws Exception {
        GameSession session = new GameSession(Level.campaign(), new GameProgress());
        for (int index = 0; index < session.levelCount(); index++) {
            runFirst(session.current());
            assertEquals(GameModel.State.WON, session.current().state);
            if (index == 1) {
                assertTrue(session.openShop());
                assertEquals(AbilityShop.Status.PURCHASED, session.buy(AbilityShop.Item.DASH));
                session.closeShop();
            }
            checkpoint(session.current().progress());
            if (session.hasNextLevel()) {
                assertTrue(session.missingNextAbilities().isEmpty());
                assertTrue(session.advance());
            }
        }
        assertEquals(Set.of("level-01", "level-02", "level-03"), session.current().progress().completedLevelIds());
        assertFalse(session.advance());
    }

    @Test void owningDashWithoutUsingItOrUsingItTooEarlyCannotFinishRoute() {
        for (double activateAt : new double[]{Double.POSITIVE_INFINITY, 400}) {
            GameModel model = new GameModel(gateRoute(GameProgress.Ability.DASH), progress(Set.of("level-01"), Set.of(GameProgress.Ability.DASH)));
            runDash(model, activateAt);
            assertEquals(GameModel.State.LOST, model.state);
            assertEquals(GameProgress.Ability.DASH, model.blockedGate());
            assertFalse(model.progress().completedLevelIds().contains("level-02"));
            assertTrue(model.x < model.level.finish);
        }
    }

    @Test void gateRouteRingsCanFundIncreasedGlidePrice() {
        GameProgress spentOnDash = new GameProgress(3, Set.of(), Set.of(GameProgress.Ability.DASH), Set.of("level-01"));
        GameModel model = new GameModel(gateRoute(GameProgress.Ability.DASH), spentOnDash);
        runDash(model, 580);
        assertEquals(GameModel.State.WON, model.state);
        assertEquals(9, model.progress().balance());
        assertEquals(AbilityShop.Status.PURCHASED, model.buy(AbilityShop.Item.GLIDE));
        assertEquals(0, model.progress().balance());
        assertTrue(model.progress().unlockedAbilities().contains(GameProgress.Ability.GLIDE));
    }

    @Test void owningGlideWithoutUsingItOrEnteringDuringAscentCannotFinishRoute() {
        for (double jumpAt : new double[]{Double.POSITIVE_INFINITY, 590}) {
            GameModel model = new GameModel(gateRoute(GameProgress.Ability.GLIDE), progress(Set.of("level-01", "level-02"),
                Set.of(GameProgress.Ability.GLIDE)));
            runGlide(model, jumpAt);
            assertEquals(GameModel.State.LOST, model.state);
            assertEquals(GameProgress.Ability.GLIDE, model.blockedGate());
            assertFalse(model.progress().completedLevelIds().contains("level-03"));
        }
    }

    @Test void directModelWithoutPurchasedSkillCannotBypassGateAndClearsReasonOnRetry() {
        GameModel model = new GameModel(gateRoute(GameProgress.Ability.GLIDE));
        runGlide(model, 480);
        assertEquals(GameModel.State.LOST, model.state);
        assertEquals(GameProgress.Ability.GLIDE, model.blockedGate());
        model.restart();
        assertNull(model.blockedGate());
        assertEquals(GameModel.State.READY, model.state);
        assertFalse(model.progress().completedLevelIds().contains("level-03"));
    }

    @Test void usingTheWrongPurchasedMovementDoesNotBypassSkillChallenge() {
        GameProgress both = progress(Set.of("level-01", "level-02"),
            Set.of(GameProgress.Ability.DASH, GameProgress.Ability.GLIDE));
        GameModel dashGate = new GameModel(gateRoute(GameProgress.Ability.DASH), both);
        runGlide(dashGate, 480);
        assertEquals(GameModel.State.LOST, dashGate.state);
        assertEquals(GameProgress.Ability.DASH, dashGate.blockedGate());
        GameModel glideGate = new GameModel(gateRoute(GameProgress.Ability.GLIDE), both);
        runDash(glideGate, 580);
        assertEquals(GameModel.State.LOST, glideGate.state);
        assertEquals(GameProgress.Ability.GLIDE, glideGate.blockedGate());
    }

    @Test void gateAndLevelConfigurationRejectInvalidAndDuplicateLayouts() {
        assertThrows(NullPointerException.class, () -> new AbilityGate(null, 650, 28, GameProgress.Ability.DASH));
        assertThrows(IllegalArgumentException.class, () -> new AbilityGate(" ", 650, 28, GameProgress.Ability.DASH));
        assertThrows(IllegalArgumentException.class, () -> new AbilityGate("a", -1, 28, GameProgress.Ability.DASH));
        assertThrows(IllegalArgumentException.class, () -> new AbilityGate("a", 650, 0, GameProgress.Ability.DASH));
        AbilityGate gate = new AbilityGate("a", 650, 28, GameProgress.Ability.DASH);
        assertThrows(IllegalArgumentException.class, () -> new Level("a", "a", 2000, List.of(), List.of(), List.of(), List.of(gate, gate)));
        assertThrows(IllegalArgumentException.class, () -> new Level("a", "a", 660, List.of(), List.of(), List.of(), List.of(gate)));
        assertThrows(IllegalArgumentException.class, () -> new Level("a", "a", 100, List.of(), List.of(), List.of(), List.of()));
    }

    private Level gateRoute(GameProgress.Ability ability) {
        return new Level(ability == GameProgress.Ability.DASH ? "level-02" : "level-03", "Test gate", 2000,
            List.of(), List.of(), List.of(new Ring("a", 350, Level.GROUND - 36),
                new Ring("b", 500, Level.GROUND - 36), new Ring("c", 850, Level.GROUND - 36),
                new Ring("d", 1100, Level.GROUND - 36), new Ring("e", 1400, Level.GROUND - 36),
                new Ring("f", 1750, Level.GROUND - 36)),
            List.of(new AbilityGate("test", 650, 28, ability)));
    }

    private GameProgress progress(Set<String> completed, Set<GameProgress.Ability> abilities) {
        return new GameProgress(0, Set.of(), abilities, completed);
    }

    private void runFirst(GameModel model) {
        CoursePilot.complete(model);
    }

    private void runDash(GameModel model, double activateAt) {
        model.jump();
        boolean activated = false;
        for (int i = 0; i < 5000 && model.state == GameModel.State.RUNNING; i++) {
            if (!activated && model.x >= activateAt) { model.dash(); activated = true; }
            model.update(FixedStepLoop.STEP);
        }
    }

    private void runGlide(GameModel model, double jumpAt) {
        model.jump();
        boolean jumped = false;
        for (int i = 0; i < 5000 && model.state == GameModel.State.RUNNING; i++) {
            if (!jumped && model.x >= jumpAt && model.grounded) {
                model.jump();
                model.setGlideHeld(true);
                jumped = true;
            }
            model.update(FixedStepLoop.STEP);
        }
    }

    private GameProgress checkpoint(GameProgress progress) throws Exception {
        Path file = directory.resolve("progress.properties");
        new GameProgressStore(file).save(progress);
        GameProgress loaded = new GameProgressStore(file).load();
        assertEquals(progress, loaded);
        return loaded;
    }
}
