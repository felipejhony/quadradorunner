package br.com.quadradorunner;

/** Drives the real physics using the movement required by each obstacle. */
final class CoursePilot {
    static void steer(GameModel model) {
        boolean jump = model.level.pits.stream().anyMatch(pit -> {
            double distance = pit.x() - model.x - GameModel.SIZE;
            return distance > 0 && distance < 35;
        });
        for (var obstacle : model.level.obstacles) {
            double distance = obstacle.getBounds2D().getMinX() - model.x - GameModel.SIZE;
            if (obstacle instanceof CourseObstacle course) {
                if (course.kind == CourseObstacle.Kind.FALLING && distance > 90 && distance < 110) model.dash();
                if (course.kind != CourseObstacle.Kind.STACK_MIDDLE) continue;
            }
            jump |= distance > 0 && distance < 65;
        }
        if (model.grounded && jump) model.jump();
    }

    static void complete(GameModel model) {
        model.jump();
        for (int frame = 0; frame < 5000 && model.state == GameModel.State.RUNNING; frame++) {
            steer(model);
            model.update(FixedStepLoop.STEP);
        }
    }
}
