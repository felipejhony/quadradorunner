package br.com.quadradorunner;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AudioSamplesTest {
    @Test void musicHasSixteenSecondsLowVolumeAndSmoothLoopBoundary() {
        byte[] music = AudioSamples.music();
        assertEquals(16 * AudioSamples.SAMPLE_RATE * 2, music.length);
        checkSignal(music, 5000);
    }

    @Test void spaceScoreAddsRhythmicDrumsAndKeepsMelodyAndLoopHeadroom() {
        byte[] night = AudioSamples.music(GameAudio.Music.NIGHT);
        byte[] space = AudioSamples.music(GameAudio.Music.SPACE);
        assertEquals(night.length, space.length);
        checkSignal(space, 10000);
        assertArrayEquals(space, AudioSamples.music(GameAudio.Music.SPACE));
        for (int beat = 0; beat < 32; beat++) {
            double differenceEnergy = 0;
            for (int frame = 0; frame < AudioSamples.SAMPLE_RATE / 10; frame++) {
                int offset = ((int) (beat * 0.5 * AudioSamples.SAMPLE_RATE) + frame) * 2;
                double difference = sample(space, offset) - sample(night, offset);
                differenceEnergy += difference * difference;
            }
            assertTrue(differenceEnergy > 100000, "Each beat must have percussion");
            int quietOffset = (int) ((beat * 0.5 + 0.4) * AudioSamples.SAMPLE_RATE) * 2;
            assertEquals(sample(night, quietOffset), sample(space, quietOffset), "Melody remains between drum hits");
        }
    }

    @Test void everyEffectIsAudibleShortAndHasSmoothEndpoints() {
        for (GameAudio.Effect effect : GameAudio.Effect.values()) {
            byte[] samples = AudioSamples.effect(effect);
            assertTrue(samples.length > AudioSamples.SAMPLE_RATE / 10);
            assertTrue(samples.length < AudioSamples.SAMPLE_RATE * 2);
            checkSignal(samples, 16000);
        }
    }

    private void checkSignal(byte[] pcm, int maximum) {
        int peak = 0;
        double energy = 0;
        for (int i = 0; i < pcm.length; i += 2) {
            int sample = sample(pcm, i);
            peak = Math.max(peak, Math.abs(sample));
            energy += (double) sample * sample;
        }
        assertTrue(peak > 1000, "Signal should not be silent");
        assertTrue(peak < maximum, "Keep headroom for overlapping sounds");
        assertTrue(energy > 0);
        assertEquals(0, sample(pcm, 0));
        assertTrue(Math.abs(sample(pcm, pcm.length - 2)) <= 1);
    }

    private int sample(byte[] pcm, int offset) {
        return (short) ((pcm[offset] & 0xff) | (pcm[offset + 1] << 8));
    }
}
