package br.com.quadradorunner;

import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GamePanelAudioTest {
    @Test void onlyAcceptedJumpsNewCollectionsAndTerminalTransitionsPlayEffects() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        TestAudioOutput output = new TestAudioOutput();
        try {
            SwingUtilities.invokeAndWait(() -> {
                GameModel model = new GameModel(new Level(List.of(Level.obstacle(650, 40, 80)),
                    List.of(new Rectangle2D.Double(100, 0, 50, Level.GROUND))));
                GamePanel panel = new GamePanel(model, () -> new GameAudio(() -> output, worker));
                panel.addNotify();
                try {
                    press(panel, "SPACE");
                    release(panel, "SPACE");
                    press(panel, "SPACE"); // Air jump without power must be silent.
                    panel.updateGame(FixedStepLoop.STEP); // Collect once.
                    panel.updateGame(FixedStepLoop.STEP);
                    model.x = Level.FINISH - 1;
                    panel.updateGame(FixedStepLoop.STEP);
                    panel.updateGame(FixedStepLoop.STEP); // No repeated victory.
                    press(panel, "R");
                    model.state = GameModel.State.RUNNING;
                    model.x = 620;
                    panel.updateGame(FixedStepLoop.STEP);
                    panel.updateGame(FixedStepLoop.STEP); // No repeated defeat.
                } finally { panel.removeNotify(); }
            });
        } finally { worker.shutdown(); }
        assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(List.of("JUMP", "COLLECT", "WIN", "LOSE"),
            output.events.stream().filter(event -> !event.contains(":") && !event.equals("close")
                && !event.equals("silence")).toList());
        assertEquals("close", output.events.get(output.events.size() - 1));
    }

    @Test void muteIgnoresKeyRepeatAndFocusGainKeepsRunningGamePaused() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        TestAudioOutput output = new TestAudioOutput();
        try {
            SwingUtilities.invokeAndWait(() -> {
                GameModel model = new GameModel(new Level(List.of(), List.of()));
                GamePanel panel = new GamePanel(model, () -> new GameAudio(() -> output, worker));
                panel.addNotify();
                try {
                    press(panel, "M");
                    press(panel, "M"); // Repetition must not unmute.
                    press(panel, "SPACE");
                    release(panel, "SPACE");
                    model.powerRemaining = 10;
                    release(panel, "M");
                    press(panel, "M");
                    press(panel, "SPACE"); // Only this jump should be audible.
                    panel.pauseForFocusLoss();
                    panel.audioFocusGained();
                    assertEquals(GameModel.State.PAUSED, model.state);
                    press(panel, "P");
                    assertEquals(GameModel.State.RUNNING, model.state);
                } finally { panel.removeNotify(); }
            });
        } finally { worker.shutdown(); }
        assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(1, output.events.stream().filter("JUMP"::equals).count());
        assertTrue(output.events.contains("silence"));
        assertEquals("close", output.events.get(output.events.size() - 1));
    }

    private void press(GamePanel panel, String key) {
        panel.getActionMap().get("pressed " + key).actionPerformed(null);
    }

    private void release(GamePanel panel, String key) {
        panel.getActionMap().get("released " + key).actionPerformed(null);
    }
}
