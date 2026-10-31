package br.com.quadradorunner;

import java.util.ArrayList;
import java.util.List;

/** Access recorded events only after the audio executor has terminated. */
final class TestAudioOutput implements GameAudio.Output {
    final List<String> events = new ArrayList<>();
    Thread audioThread;

    @Override public void play(GameAudio.Effect effect) {
        audioThread = Thread.currentThread();
        events.add(effect.name());
    }
    @Override public void music(boolean playing) { events.add("music:" + playing); }
    @Override public void silence() { events.add("silence"); }
    @Override public void close() { events.add("close"); }
}
