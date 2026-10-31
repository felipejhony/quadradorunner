package br.com.quadradorunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ProgressAutosaveTest {
    @TempDir Path directory;

    @Test void panelCollectionsSaveInOrderOffEdtAndSurviveNewSession() throws Exception {
        Path file = directory.resolve("progress.properties");
        var worker = Executors.newSingleThreadExecutor();
        var failures = new CopyOnWriteArrayList<Exception>();
        var callbackThreads = new CopyOnWriteArrayList<Boolean>();
        GameProgressStore store = new GameProgressStore(file);
        ProgressAutosave autosave = new ProgressAutosave(store, worker);
        Ring one = new Ring("one", 110, Level.GROUND - 30);
        Ring two = new Ring("two", 210, Level.GROUND - 30);
        Level level = new Level("a", List.of(), List.of(), List.of(one, two));
        var snapshots = new CopyOnWriteArrayList<GameProgress>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                GameModel model = new GameModel(level);
                GamePanel panel = new GamePanel(model, GameAudio::new, snapshot -> {
                    snapshots.add(snapshot);
                    autosave.save(snapshot, failure -> {
                        callbackThreads.add(SwingUtilities.isEventDispatchThread());
                        if (failure != null) failures.add(failure);
                    });
                });
                model.state = GameModel.State.RUNNING;
                panel.updateGame(FixedStepLoop.STEP);
                panel.updateGame(FixedStepLoop.STEP); // Same overlap must not enqueue another save.
                model.x = 200;
                panel.updateGame(FixedStepLoop.STEP);
            });
        } finally { autosave.close(); }
        assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS));
        assertTrue(failures.isEmpty(), failures.toString());
        assertEquals(List.of(false, false), callbackThreads);
        assertEquals(2, snapshots.size());
        GameModel restored = new GameModel(level, new GameProgressStore(file).load());
        assertEquals(snapshots.get(1), restored.progress());
        assertEquals(2, restored.progress().balance());
        assertTrue(restored.isRingCollected(one));
        assertTrue(restored.isRingCollected(two));
    }

    @Test void failedSaveReportsErrorAndCanBeRetriedBeforeClosing() throws Exception {
        Path file = directory.resolve("progress.properties");
        Files.createDirectory(file);
        var worker = Executors.newSingleThreadExecutor();
        ProgressAutosave autosave = new ProgressAutosave(new GameProgressStore(file), worker);
        var failures = new CopyOnWriteArrayList<Exception>();
        CountDownLatch failed = new CountDownLatch(1);
        GameProgress progress = new GameProgress(1, Set.of(new GameProgress.RingId("a", "one")),
            Set.of(), Set.of());
        CountDownLatch saved = new CountDownLatch(1);
        try {
            autosave.save(progress, failure -> {
                if (failure != null) failures.add(failure);
                failed.countDown();
            });
            assertTrue(failed.await(10, TimeUnit.SECONDS));
            assertEquals(1, failures.size());
            Files.delete(file);
            autosave.save(progress, failure -> {
                if (failure != null) failures.add(failure);
                saved.countDown();
            });
        } finally { autosave.close(); }
        assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(0, saved.getCount());
        assertEquals(1, failures.size());
        assertEquals(progress, new GameProgressStore(file).load());
        autosave.close();
        assertThrows(IllegalStateException.class, () -> autosave.save(progress, failure -> { }));
    }
}
