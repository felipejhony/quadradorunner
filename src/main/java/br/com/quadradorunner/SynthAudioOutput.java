package br.com.quadradorunner;

import java.util.EnumMap;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;

/** Clips are opened, used and released only on the audio worker. */
final class SynthAudioOutput implements GameAudio.Output {
    private static final AudioFormat FORMAT = new AudioFormat(AudioSamples.SAMPLE_RATE, 16, 1, true, false);
    private final EnumMap<GameAudio.Effect, Clip> effects = new EnumMap<>(GameAudio.Effect.class);
    private Clip background;
    private boolean musicPlaying;

    SynthAudioOutput() {
        try {
            background = open(AudioSamples.music());
            for (GameAudio.Effect effect : GameAudio.Effect.values()) {
                effects.put(effect, open(AudioSamples.effect(effect)));
            }
        } catch (RuntimeException exception) {
            close();
            throw exception;
        }
    }

    private Clip open(byte[] samples) {
        Clip clip = null;
        try {
            clip = AudioSystem.getClip();
            clip.open(FORMAT, samples, 0, samples.length);
            return clip;
        } catch (LineUnavailableException | RuntimeException exception) {
            if (clip != null) clip.close();
            throw new IllegalStateException("Cannot open audio device", exception);
        }
    }

    @Override public void play(GameAudio.Effect effect) {
        Clip clip = effects.get(effect);
        clip.stop();
        clip.setFramePosition(0);
        clip.start();
    }

    @Override public void music(boolean playing) {
        if (playing == musicPlaying) return;
        musicPlaying = playing;
        if (playing) background.loop(Clip.LOOP_CONTINUOUSLY);
        else background.stop();
    }

    @Override public void silence() {
        for (Clip clip : effects.values()) clip.stop();
        music(false);
    }

    @Override public void close() {
        for (Clip clip : effects.values()) clip.close();
        effects.clear();
        if (background != null) {
            background.close();
            background = null;
        }
        musicPlaying = false;
    }
}
