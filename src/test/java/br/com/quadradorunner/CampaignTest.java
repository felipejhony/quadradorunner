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
        assertEquals(17, levels.get(0).obstacles.size());
        assertEquals(3, levels.get(0).powerUps.size());
        assertEquals(12, levels.get(0).rings.size());
        assertTrue(levels.get(0).requiredAbilities.isEmpty());
        assertEquals(Set.of(GameProgress.Ability.DASH), levels.get(1).requiredAbilities);
        assertEquals(Set.of(GameProgress.Ability.GLIDE), levels.get(2).requiredAbilities);
        assertEquals(6, levels.get(1).rings.size());
        for (Level level : levels) {
            assertEquals(level.rings.size(), level.rings.stream().map(Ring::id).distinct().count());
            for (Ring ring : level.rings) assertTrue(ring.x() + Ring.DIAMETER < level.finish);
        }
    }

    @Test void accessRequiresEveryPrecedingCompletionAndTheTargetSkill() {
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
        assertFalse(firstCompleted.isLevelUnlocked(1));
        GameSession dashReady = new GameSession(Level.campaign(), progress(Set.of("level-01"), Set.of(GameProgress.Ability.DASH)));
        assertTrue(dashReady.isLevelUnlocked(1));
        assertFalse(dashReady.isLevelUnlocked(2));
        GameSession missingGlide = new GameSession(Level.campaign(), progress(Set.of("level-01", "level-02"),
            Set.of(GameProgress.Ability.DASH)));
        assertFalse(missingGlide.isLevelUnlocked(2));
        GameSession skippedFirst = new GameSession(Level.campaign(), progress(Set.of("level-02"),
            Set.of(GameProgress.Ability.DASH, GameProgress.Ability.GLIDE)));
        assertFalse(skippedFirst.isLevelUnlocked(2));
        GameSession all = new GameSession(Level.campaign(), progress(Set.of("level-01", "level-02"),
            Set.of(GameProgress.Ability.DASH, GameProgress.Ability.GLIDE)));
        assertTrue(all.isLevelUnlocked(2));
    }

    @Test void actualCampaignCanBeFinishedFromEmptySaveBuyingEachRequiredSkillWithoutFarming() throws Exception {
        GameSession session = new GameSession(Level.campaign(), new GameProgress());
        runFirst(session.current());
        assertEquals(GameModel.State.WON, session.current().state);
        assertTrue(session.current().progress().balance() >= AbilityShop.Item.DASH.price);
        assertFalse(session.canAdvance());
        assertEquals(Set.of(GameProgress.Ability.DASH), session.missingNextAbilities());
        session.openShop();
        assertEquals(AbilityShop.Status.PURCHASED, session.buy(AbilityShop.Item.DASH));
        session.closeShop();
        checkpoint(session.current().progress());
        assertTrue(session.advance());
        int previousBalance = session.current().progress().balance();
        runDash(session.current(), 580);
        assertEquals(GameModel.State.WON, session.current().state);
        assertEquals(previousBalance + 6, session.current().progress().balance());
        assertFalse(session.canAdvance());
        assertEquals(Set.of(GameProgress.Ability.GLIDE), session.missingNextAbilities());
        session.openShop();
        assertEquals(AbilityShop.Status.PURCHASED, session.buy(AbilityShop.Item.GLIDE));
        session.closeShop();
        checkpoint(session.current().progress());
        assertTrue(session.advance());
        runGlide(session.current(), 480);
        assertEquals(GameModel.State.WON, session.current().state);
        assertEquals(Set.of("level-01", "level-02", "level-03"), session.current().progress().completedLevelIds());
        assertFalse(session.hasNextLevel());
        assertFalse(session.advance());
        GameProgress loaded = checkpoint(session.current().progress());
        GameSession restored = new GameSession(Level.campaign(), loaded);
        assertTrue(restored.isLevelUnlocked(1));
        assertTrue(restored.isLevelUnlocked(2));
        restored.reset();
        assertTrue(restored.isLevelUnlocked(0));
        assertFalse(restored.isLevelUnlocked(1));
        assertFalse(restored.isLevelUnlocked(2));
        assertEquals(new GameProgress(), restored.current().progress());
    }

    @Test void owningDashWithoutUsingItOrUsingItTooEarlyCannotFinishRoute() {
        for (double activateAt : new double[]{Double.POSITIVE_INFINITY, 400}) {
            GameModel model = new GameModel(Level.dashRoute(), progress(Set.of("level-01"), Set.of(GameProgress.Ability.DASH)));
            runDash(model, activateAt);
            assertEquals(GameModel.State.LOST, model.state);
            assertEquals(GameProgress.Ability.DASH, model.blockedGate());
            assertFalse(model.progress().completedLevelIds().contains("level-02"));
            assertTrue(model.x < model.level.finish);
        }
    }

    @Test void dashRouteFundsGlideEvenIfPlayerArrivesWithZeroBalance() {
        GameProgress spentOnDash = new GameProgress(0, Set.of(), Set.of(GameProgress.Ability.DASH), Set.of("level-01"));
        GameModel model = new GameModel(Level.dashRoute(), spentOnDash);
        runDash(model, 580);
        assertEquals(GameModel.State.WON, model.state);
        assertEquals(6, model.progress().balance());
        assertEquals(AbilityShop.Status.PURCHASED, model.buy(AbilityShop.Item.GLIDE));
        assertEquals(0, model.progress().balance());
        assertTrue(model.progress().unlockedAbilities().contains(GameProgress.Ability.GLIDE));
    }

    @Test void owningGlideWithoutUsingItOrEnteringDuringAscentCannotFinishRoute() {
        for (double jumpAt : new double[]{Double.POSITIVE_INFINITY, 590}) {
            GameModel model = new GameModel(Level.glideRoute(), progress(Set.of("level-01", "level-02"),
                Set.of(GameProgress.Ability.GLIDE)));
            runGlide(model, jumpAt);
            assertEquals(GameModel.State.LOST, model.state);
            assertEquals(GameProgress.Ability.GLIDE, model.blockedGate());
            assertFalse(model.progress().completedLevelIds().contains("level-03"));
        }
    }

    @Test void directModelWithoutPurchasedSkillCannotBypassGateAndClearsReasonOnRetry() {
        GameModel model = new GameModel(Level.glideRoute());
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
        GameModel dashGate = new GameModel(Level.dashRoute(), both);
        runGlide(dashGate, 480);
        assertEquals(GameModel.State.LOST, dashGate.state);
        assertEquals(GameProgress.Ability.DASH, dashGate.blockedGate());
        GameModel glideGate = new GameModel(Level.glideRoute(), both);
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

    private GameProgress progress(Set<String> completed, Set<GameProgress.Ability> abilities) {
        return new GameProgress(0, Set.of(), abilities, completed);
    }

    private void runFirst(GameModel model) {
        model.jump();
        for (int i = 0; i < 5000 && model.state == GameModel.State.RUNNING; i++) {
            if (model.grounded && model.level.obstacles.stream().anyMatch(obstacle -> {
                double distance = obstacle.getBounds2D().getMinX() - (model.x + GameModel.SIZE);
                return distance > 0 && distance < 55;
            })) model.jump();
            model.update(FixedStepLoop.STEP);
        }
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
