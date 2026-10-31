package br.com.quadradorunner;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameAudioTest {
    @Test void audioRunsOffCallerThreadAndMutePauseAndCloseAreOrdered() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        TestAudioOutput output = new TestAudioOutput();
        GameAudio audio = new GameAudio(() -> output, worker);
        try {
            audio.configure(false, false);
            audio.play(GameAudio.Effect.JUMP);
            audio.configure(true, false);
            audio.play(GameAudio.Effect.COLLECT);
            audio.configure(false, true);
            audio.configure(false, false);
            audio.play(GameAudio.Effect.WIN);
        } finally { audio.close(); }
        audio.close();
        audio.play(GameAudio.Effect.LOSE);
        assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(List.of("music:true", "JUMP", "silence", "music:false",
            "silence", "music:false", "music:true", "WIN", "close"), output.events);
        assertNotEquals(Thread.currentThread(), output.audioThread);
    }

    @Test void unavailableDeviceIsTriedOnceAndDoesNotBreakGameCommands() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        AtomicInteger attempts = new AtomicInteger();
        GameAudio audio = new GameAudio(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("Simulated unavailable mixer");
        }, worker);
        try {
            audio.configure(false, false);
            audio.play(GameAudio.Effect.JUMP);
            audio.configure(false, true);
        } finally { audio.close(); }
        assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(1, attempts.get());
    }

    @Test void playbackFailureReleasesOutputOnlyOnce() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        AtomicInteger closes = new AtomicInteger();
        GameAudio audio = new GameAudio(() -> new GameAudio.Output() {
            public void play(GameAudio.Effect effect) { throw new IllegalStateException("Device removed"); }
            public void music(boolean playing) { }
            public void silence() { }
            public void close() { closes.incrementAndGet(); }
        }, worker);
        try {
            audio.play(GameAudio.Effect.JUMP);
            audio.play(GameAudio.Effect.WIN);
        } finally { audio.close(); }
        assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(1, closes.get());
    }
}
