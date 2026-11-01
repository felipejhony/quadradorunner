package br.com.quadradorunner;

import java.awt.Shape;
import java.awt.geom.*;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.EnumSet;

/** Static world geometry; coordinates are in world pixels. */
final class Level {
    static final int GROUND = 428;
    static final int FINISH = 8400;
    static final String STANDARD_ID = "level-01";
    final String id;
    final String title;
    final int finish;
    final List<Pit> pits;
    record Pit(int x, int width) {
        Pit { if (x < 0 || width <= 0) throw new IllegalArgumentException("Invalid pit"); }
        int end() { return x + width; }
    }
    final List<Shape> obstacles;
    final List<Rectangle2D.Double> powerUps;
    final List<Ring> rings;
    final List<AbilityGate> gates;
    final Set<GameProgress.Ability> requiredAbilities;

    Level(List<Shape> obstacles, List<Rectangle2D.Double> powerUps) {
        this(STANDARD_ID, obstacles, powerUps, List.of());
    }

    Level(String id, List<Shape> obstacles, List<Rectangle2D.Double> powerUps, List<Ring> rings) {
        this(id, id, FINISH, obstacles, powerUps, rings, List.of());
    }

    Level(String id, String title, int finish, List<Shape> obstacles,
          List<Rectangle2D.Double> powerUps, List<Ring> rings, List<AbilityGate> gates) {
        this(id, title, finish, obstacles, powerUps, rings, gates, List.of());
    }

    Level(String id, String title, int finish, List<Shape> obstacles,
          List<Rectangle2D.Double> powerUps, List<Ring> rings, List<AbilityGate> gates, List<Pit> pits) {
        this.pits = List.copyOf(pits);
        this.id = Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("Level ID cannot be blank");
        this.title = Objects.requireNonNull(title, "title");
        if (title.isBlank() || finish <= GameModel.START_X) throw new IllegalArgumentException("Invalid level layout");
        this.finish = finish;
        this.obstacles = List.copyOf(obstacles);
        this.powerUps = List.copyOf(powerUps);
        this.rings = List.copyOf(rings);
        Set<String> ringIds = new HashSet<>();
        for (Ring ring : this.rings) {
            if (!ringIds.add(ring.id())) throw new IllegalArgumentException("Duplicate ring ID: " + ring.id());
        }
        this.gates = List.copyOf(gates);
        Set<String> gateIds = new HashSet<>();
        Set<GameProgress.Ability> required = EnumSet.noneOf(GameProgress.Ability.class);
        for (AbilityGate gate : this.gates) {
            if (!gateIds.add(gate.id()) || (long) gate.x() + gate.width() >= finish) {
                throw new IllegalArgumentException("Duplicate gate ID or gate beyond finish");
            }
            required.add(gate.ability());
        }
        requiredAbilities = Set.copyOf(required);
    }

    static Level standard() {
        return standard(new java.util.Random());
    }

    static Level standard(java.util.Random random) {
        List<Rectangle2D.Double> powerUps = List.of();
        // Explicit IDs must survive reordering and future changes to world positions.
        List<Ring> rings = List.of(
            new Ring("ring-01", 350, GROUND - 36), new Ring("ring-02", 850, GROUND - 130),
            new Ring("ring-03", 1260, GROUND - 120), new Ring("ring-04", 1660, GROUND - 36),
            new Ring("ring-05", 2120, GROUND - 130), new Ring("ring-06", 2600, GROUND - 36),
            new Ring("ring-07", 3070, GROUND - 100), new Ring("ring-08", 3500, GROUND - 36),
            new Ring("ring-09", 4450, GROUND - 120), new Ring("ring-10", 4850, GROUND - 36),
            new Ring("ring-11", 5800, GROUND - 120), new Ring("ring-12", 8150, GROUND - 36));
        return ObstacleDetector.fill(new Level(STANDARD_ID, "A pista", FINISH,
            List.of(), powerUps, rings, List.of()), random);
    }

    private static boolean overlapsRingCorridor(int x, int width, List<Ring> rings) {
        return rings.stream().anyMatch(ring -> x < ring.x() + Ring.DIAMETER + 20
            && x + width + 20 > ring.x());
    }

    static List<Level> campaign() {
        return List.of(standard(), dashRoute(), glideRoute(), pools());
    }

    static Level dashRoute() {
        return dashRoute(new java.util.Random());
    }

    static Level dashRoute(java.util.Random random) {
        return ObstacleDetector.fill(withRandomPits(new Level("level-02", "Amanhecer", FINISH,
            List.of(),
            List.of(), List.of(
                new Ring("ring-01", 350, GROUND - 36), new Ring("ring-02", 500, GROUND - 36),
                new Ring("ring-03", 850, GROUND - 36), new Ring("ring-04", 1220, GROUND - 36),
                new Ring("ring-05", 1630, GROUND - 36), new Ring("ring-06", 2940, GROUND - 36)),
            List.of()), random), random);
    }

    static Level glideRoute() {
        return glideRoute(new java.util.Random());
    }

    static Level glideRoute(java.util.Random random) {
        return ObstacleDetector.fill(withRandomPits(new Level("level-03", "Corrente aérea", FINISH, List.of(), List.of(), List.of(
            new Ring("ring-01", 350, GROUND - 36), new Ring("ring-02", 550, GROUND - 130),
            new Ring("ring-03", 720, GROUND - 120), new Ring("ring-04", 1050, GROUND - 36),
            new Ring("ring-05", 1300, GROUND - 36), new Ring("ring-06", 1650, GROUND - 36)),
            List.of()), random), random);
    }

    static Level pools() {
        return pools(new java.util.Random());
    }

    static Level pools(java.util.Random random) {
        List<Ring> rings = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            rings.add(new Ring("ring-" + (i + 1), 350 + (i / 5) * 1000 + (i % 5) * 42,
                GROUND - (i % 3 == 1 ? 120 : 36)));
        }
        List<Pit> pools = List.of(new Pit(1800, 110), new Pit(3800, 110),
            new Pit(5800, 110), new Pit(7800, 110));
        return ObstacleDetector.fill(new Level("level-04", "Piscinas", FINISH,
            List.of(), List.of(), rings, List.of(), pools), random);
    }

    static Level withRandomPits(Level level, java.util.Random random) {
        List<Pit> pits = new java.util.ArrayList<>();
        for (int section = 900; section < level.finish - 600; section += 900) {
            for (int attempt = 0; attempt < 40; attempt++) {
                Pit pit = new Pit(section + random.nextInt(300), 90 + random.nextInt(41));
                boolean blocked = level.obstacles.stream().anyMatch(shape ->
                    shape.getBounds2D().getMaxX() > pit.x() - 180
                    && shape.getBounds2D().getMinX() < pit.end() + 180);
                if (blocked || overlapsRingCorridor(pit.x(), pit.width(), level.rings)) continue;
                pits.add(pit);
                break;
            }
        }
        return new Level(level.id, level.title, level.finish, level.obstacles,
            level.powerUps, level.rings, level.gates, pits);
    }

    boolean hasGround(double x, double width) {
        return pits.stream().noneMatch(pit -> x >= pit.x() && x + width <= pit.end());
    }

    static Rectangle2D.Double obstacle(int x, int width, int height) {
        return new Rectangle2D.Double(x, GROUND - height, width, height);
    }

    static Shape triangle(int x, int width, int height) {
        Path2D path = new Path2D.Double();
        path.moveTo(x, GROUND);
        path.lineTo(x + width / 2.0, GROUND - height);
        path.lineTo(x + width, GROUND);
        path.closePath();
        return path;
    }

    static Shape circle(int x, int diameter) {
        return new Ellipse2D.Double(x, GROUND - diameter, diameter, diameter);
    }

    static Shape diamond(int x, int width, int height) {
        Path2D path = new Path2D.Double();
        path.moveTo(x + width / 2.0, GROUND - height);
        path.lineTo(x + width, GROUND - height / 2.0);
        path.lineTo(x + width / 2.0, GROUND);
        path.lineTo(x, GROUND - height / 2.0);
        path.closePath();
        return path;
    }

}
