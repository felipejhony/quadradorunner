package br.com.quadradorunner;

import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CourseObstacleTest {
    @Test void rendersCorridorsFallingWarningAndSplitHalvesWithoutAdvancingPhysics() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            for (var kind : CourseObstacle.Kind.values()) {
                var obstacle = new CourseObstacle(700, kind == CourseObstacle.Kind.SPLITTER ? 140 : 70, kind);
                GameModel model = model(obstacle, true);
                model.state = GameModel.State.RUNNING;
                while (model.x < 610) model.update(FixedStepLoop.STEP);
                var panel = new GamePanel(model);
                panel.setSize(GamePanel.WIDTH, GamePanel.HEIGHT);
                var picture = new java.awt.image.BufferedImage(GamePanel.WIDTH, GamePanel.HEIGHT,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var g = picture.createGraphics();
                double elapsed = model.obstacleElapsed(obstacle);
                try { panel.paint(g); } finally { g.dispose(); }
                assertEquals(elapsed, model.obstacleElapsed(obstacle));
                try {
                    var path = java.nio.file.Path.of("target/render-review/course-" + kind + ".png");
                    java.nio.file.Files.createDirectories(path.getParent());
                    javax.imageio.ImageIO.write(picture, "png", path.toFile());
                } catch (java.io.IOException failure) { throw new AssertionError(failure); }
            }
        });
    }
    @Test void generationRestrictsStacksAndFallsToThirdRouteAndOffersSplittersEverywhere() {
        for (int seed = 0; seed < 30; seed++) {
            List<Level> levels = List.of(Level.standard(new Random(seed)),
                Level.dashRoute(new Random(seed)), Level.glideRoute(new Random(seed)));
            for (int index = 0; index < levels.size(); index++) {
                var kinds = levels.get(index).obstacles.stream().filter(CourseObstacle.class::isInstance)
                    .map(CourseObstacle.class::cast).map(o -> o.kind).collect(java.util.stream.Collectors.toSet());
                assertTrue(kinds.contains(CourseObstacle.Kind.SPLITTER));
                if (index < 2) assertEquals(Set.of(CourseObstacle.Kind.SPLITTER), kinds);
                else assertEquals(Set.of(CourseObstacle.Kind.values()), kinds);
            }
        }
    }

    @Test void splitterIsSafeOnGroundButJumpingCostsALife() {
        var obstacle = new CourseObstacle(700, 140, CourseObstacle.Kind.SPLITTER);
        GameModel runner = model(obstacle, false);
        runner.state = GameModel.State.RUNNING;
        double widest = 0;
        double narrowest = 100;
        for (int i = 0; i < 400; i++) {
            runner.update(FixedStepLoop.STEP);
            if (runner.x >= obstacle.x && runner.x < obstacle.getMaxX()) {
                double gap = Level.GROUND - runner.obstacleShape(obstacle).getBounds2D().getMaxY();
                widest = Math.max(widest, gap);
                narrowest = Math.min(narrowest, gap);
                assertTrue(gap > GameModel.SIZE);
            }
        }
        assertTrue(widest > narrowest);
        assertEquals(GameModel.MAX_LIVES, runner.lives);
        GameModel jumper = model(obstacle, false);
        jumper.state = GameModel.State.RUNNING;
        for (int i = 0; i < 400; i++) {
            if (jumper.x > 620 && jumper.x < 625) jumper.jump();
            jumper.update(FixedStepLoop.STEP);
        }
        assertEquals(GameModel.MAX_LIVES - 1, jumper.lives);
    }

    @Test void fallingObstacleHitsRunnerButTimelyDashAvoidsItWithoutInvulnerability() {
        var obstacle = new CourseObstacle(700, 70, CourseObstacle.Kind.FALLING);
        GameModel runner = model(obstacle, true);
        runner.state = GameModel.State.RUNNING;
        for (int i = 0; i < 420; i++) runner.update(FixedStepLoop.STEP);
        assertEquals(GameModel.MAX_LIVES - 1, runner.lives);
        GameModel dasher = model(obstacle, true);
        CoursePilot.complete(dasher);
        assertEquals(GameModel.MAX_LIVES, dasher.lives);
        assertEquals(GameModel.State.WON, dasher.state);
    }

    @Test void stackedCorridorsRequireTheirMatchingRoute() {
        GameModel under = model(new CourseObstacle(700, 70, CourseObstacle.Kind.STACK_UNDER), false);
        CoursePilot.complete(under);
        assertEquals(GameModel.MAX_LIVES, under.lives);
        GameModel middle = model(new CourseObstacle(700, 70, CourseObstacle.Kind.STACK_MIDDLE), false);
        CoursePilot.complete(middle);
        assertEquals(GameModel.MAX_LIVES, middle.lives);
        GameModel wrong = model(new CourseObstacle(700, 70, CourseObstacle.Kind.STACK_MIDDLE), false);
        wrong.state = GameModel.State.RUNNING;
        for (int i = 0; i < 400; i++) wrong.update(FixedStepLoop.STEP);
        assertEquals(GameModel.MAX_LIVES - 1, wrong.lives);
    }

    @Test void pauseFreezesAnimationAndRestartResetsProximityTrigger() {
        var obstacle = new CourseObstacle(700, 140, CourseObstacle.Kind.SPLITTER);
        GameModel model = model(obstacle, false);
        model.state = GameModel.State.RUNNING;
        for (int i = 0; i < 230; i++) model.update(FixedStepLoop.STEP);
        assertTrue(model.obstacleElapsed(obstacle) > 0);
        var before = model.obstacleShape(obstacle).getBounds2D();
        model.pause();
        model.update(FixedStepLoop.STEP);
        assertEquals(before, model.obstacleShape(obstacle).getBounds2D());
        model.restart();
        assertEquals(-1, model.obstacleElapsed(obstacle));
        assertEquals(140, model.obstacleShape(obstacle).getBounds2D().getHeight());
    }

    private GameModel model(CourseObstacle obstacle, boolean dash) {
        return new GameModel(new Level("test", "Test", 1400, List.of(obstacle), List.of(), List.of(), List.of()),
            new GameProgress(0, Set.of(), dash ? Set.of(GameProgress.Ability.DASH) : Set.of(), Set.of()));
    }
}
