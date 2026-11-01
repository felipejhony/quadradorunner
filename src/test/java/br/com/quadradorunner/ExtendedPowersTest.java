package br.com.quadradorunner;

import java.awt.Shape;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static br.com.quadradorunner.GameProgress.Ability.*;
import static org.junit.jupiter.api.Assertions.*;

class ExtendedPowersTest {
    @Test void newPowersRequirePurchaseRunningStateAndDistinctPresses() throws Exception {
        GameModel locked = model(List.of());
        locked.state = GameModel.State.RUNNING;
        assertFalse(locked.durability());
        assertFalse(locked.fireRocket());
        GameModel owned = model(List.of(Level.obstacle(650, 50, 60)), DURABILITY, ROCKET);
        assertFalse(owned.durability());
        assertFalse(owned.fireRocket());
        SwingUtilities.invokeAndWait(() -> {
            var panel = new GamePanel(owned);
            owned.state = GameModel.State.RUNNING;
            panel.getActionMap().get("pressed S").actionPerformed(null);
            panel.getActionMap().get("pressed W").actionPerformed(null);
            assertTrue(owned.isDurable());
            assertNotNull(owned.projectile());
            owned.update(FixedStepLoop.STEP);
            double cooldown = owned.durabilityCooldownRemaining();
            panel.getActionMap().get("pressed S").actionPerformed(null);
            assertEquals(cooldown, owned.durabilityCooldownRemaining());
        });
    }

    @Test void shieldLastsFiveSecondsAndRechargesTenSecondsFromActivation() {
        GameModel model = model(List.of(), DURABILITY);
        model.state = GameModel.State.RUNNING;
        assertTrue(model.durability());
        assertFalse(model.durability());
        steps(model, 599);
        assertTrue(model.isDurable());
        steps(model, 1);
        assertFalse(model.isDurable());
        assertEquals(5, model.durabilityCooldownRemaining(), 1e-8);
        assertFalse(model.durability());
        steps(model, 600);
        assertTrue(model.durability());
    }

    @Test void shieldPreventsDamageAndExpiryStillAllowsOverlappingObstacleToHit() {
        GameModel model = model(List.of(Level.obstacle(4000, 50, 60), Level.obstacle(200, 2000, 300)), DURABILITY);
        model.state = GameModel.State.RUNNING;
        model.durability();
        steps(model, 599);
        assertEquals(3, model.lives);
        steps(model, 2);
        assertEquals(2, model.lives);
    }

    @Test void rocketFliesToNearestAheadObstacleAndDestructionLastsOnlyThisAttempt() {
        Shape near = Level.obstacle(650, 50, 60);
        Shape far = Level.obstacle(1000, 50, 60);
        GameModel model = model(List.of(far, near), ROCKET);
        model.state = GameModel.State.RUNNING;
        assertTrue(model.fireRocket());
        assertNotNull(model.projectile());
        assertFalse(model.isDestroyed(near));
        steps(model, 70);
        assertTrue(model.isDestroyed(near));
        assertFalse(model.isDestroyed(far));
        assertNull(model.projectile());
        assertFalse(model.fireRocket());
        model.restart();
        assertFalse(model.isDestroyed(near));
        assertEquals(0, model.rocketCooldownRemaining());
    }

    @Test void noTargetDoesNotConsumeRocketAndEverySpecialObstacleCanBeDestroyed() {
        GameModel empty = model(List.of(Level.obstacle(20, 30, 30), Level.obstacle(2000, 40, 40)), ROCKET);
        empty.state = GameModel.State.RUNNING;
        assertFalse(empty.fireRocket());
        assertEquals(0, empty.rocketCooldownRemaining());
        for (var kind : CourseObstacle.Kind.values()) {
            var obstacle = new CourseObstacle(650, 70, kind);
            GameModel model = model(List.of(obstacle), ROCKET);
            model.state = GameModel.State.RUNNING;
            assertTrue(model.fireRocket());
            steps(model, 110);
            assertTrue(model.isDestroyed(obstacle), kind.name());
            steps(model, 200);
            assertEquals(3, model.lives);
        }
    }

    @Test void pauseFreezesAllTimersAndProjectileAndRestartClearsTransientState() {
        GameModel model = model(List.of(Level.obstacle(650, 50, 60)), DURABILITY, ROCKET, DOUBLE_JUMP);
        model.jump();
        model.jump();
        model.durability();
        model.fireRocket();
        model.pause();
        var projectile = model.projectile();
        steps(model, 150);
        assertEquals(projectile, model.projectile());
        assertEquals(5, model.durabilityTimeRemaining());
        assertEquals(10, model.durabilityCooldownRemaining());
        assertEquals(2, model.doubleJumpCooldownRemaining());
        assertEquals(4, model.rocketCooldownRemaining());
        model.restart();
        assertFalse(model.isDurable());
        assertNull(model.projectile());
        assertEquals(0, model.doubleJumpCooldownRemaining());
    }

    @Test void upgradesHalveCooldownsWithoutAddingThirdJumpOrChangingDashDuration() {
        for (boolean upgraded : new boolean[]{false, true}) {
            var abilities = upgraded ? new GameProgress.Ability[]{DASH, DOUBLE_JUMP, DASH_UPGRADE, DOUBLE_JUMP_UPGRADE}
                : new GameProgress.Ability[]{DASH, DOUBLE_JUMP};
            GameModel model = model(List.of(), abilities);
            model.jump();
            assertTrue(model.jump());
            assertFalse(model.jump());
            model.dash();
            assertEquals(upgraded ? 0.6 : 1.2, model.dashCooldownRemaining());
            assertEquals(upgraded ? 1 : 2, model.doubleJumpCooldownRemaining());
            assertEquals(GameModel.DASH_DURATION, model.dashTimeRemaining());
            // A landing resets jumps but cannot bypass the cooldown of the second jump.
            model.y = Level.GROUND - GameModel.SIZE;
            model.velocityY = 0;
            steps(model, 1);
            assertTrue(model.jump());
            assertFalse(model.jump());
            steps(model, upgraded ? 120 : 240);
            assertEquals(0, model.doubleJumpCooldownRemaining());
        }
    }

    @Test void upgradesRequireBaseDebitOnceAndPreserveOldSaveFields(@TempDir Path directory) throws Exception {
        GameProgress original = new GameProgress(100, Set.of(new GameProgress.RingId("level-01", "ring-01")),
            Set.of(), Set.of("level-01"));
        for (var item : List.of(AbilityShop.Item.DASH_UPGRADE, AbilityShop.Item.DOUBLE_JUMP_UPGRADE)) {
            var rejected = AbilityShop.purchase(original, item);
            assertEquals(AbilityShop.Status.REQUIRES_BASE, rejected.status());
            assertSame(original, rejected.progress());
        }
        var store = new GameProgressStore(directory.resolve("save.properties"));
        store.save(original);
        GameProgress progress = store.load();
        for (var item : AbilityShop.Item.values()) {
            int price = AbilityShop.price(progress, item);
            var purchase = AbilityShop.purchase(progress, item);
            assertEquals(AbilityShop.Status.PURCHASED, purchase.status());
            assertEquals(progress.balance() - price, purchase.progress().balance());
            progress = purchase.progress();
            assertSame(progress, AbilityShop.purchase(progress, item).progress());
        }
        store.save(progress);
        assertEquals(progress, store.load());
        assertEquals(original.collectedRings(), progress.collectedRings());
        assertEquals(original.completedLevelIds(), progress.completedLevelIds());
        assertEquals(3, AbilityShop.price(progress, AbilityShop.Item.DASH_UPGRADE));
    }

    private GameModel model(List<Shape> obstacles, GameProgress.Ability... abilities) {
        return new GameModel(new Level(obstacles, List.of()), new GameProgress(0, Set.of(), Set.of(abilities), Set.of()));
    }

    private void steps(GameModel model, int count) {
        for (int i = 0; i < count; i++) model.update(FixedStepLoop.STEP);
    }
}
