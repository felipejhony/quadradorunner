package br.com.quadradorunner;

/** Original procedural score and effects: mono signed 16-bit little-endian PCM. */
final class AudioSamples {
    static final int SAMPLE_RATE = 22050;
    private static final double MUSIC_SECONDS = 16;

    private AudioSamples() { }

    static byte[] music() {
        return music(GameAudio.Music.NIGHT);
    }

    static byte[] music(GameAudio.Music music) {
        if (music == GameAudio.Music.POOLS) return poolMusic();
        double[] samples = new double[(int) (SAMPLE_RATE * MUSIC_SECONDS)];
        // Four slow major/minor seventh chords, with a soft pentatonic arpeggio.
        int[][] chords = music == GameAudio.Music.SUNRISE
            ? new int[][] {{60, 64, 67, 71}, {65, 69, 72, 76}, {67, 71, 74, 78}, {60, 64, 67, 72}}
            : new int[][] {{48, 52, 55, 59}, {45, 48, 52, 55}, {41, 45, 48, 52}, {43, 47, 50, 57}};
        for (int chord = 0; chord < chords.length; chord++) {
            for (int note : chords[chord]) addTone(samples, chord * 4, 4, note, note, 0.022);
            for (int beat = 0; beat < 8; beat++) {
                int note = chords[chord][beat % 4] + 24;
                addTone(samples, chord * 4 + beat * 0.5, 0.48, note, note, 0.035);
            }
        }
        if (music == GameAudio.Music.SPACE) addDrums(samples);
        return pcm(samples);
    }

    private static byte[] poolMusic() {
        double[] dry = new double[(int) (SAMPLE_RATE * MUSIC_SECONDS)];
        int[][] chords = {{50, 57, 60, 64}, {55, 62, 65, 69}, {53, 60, 64, 67}, {48, 55, 62, 65}};
        for (int bar = 0; bar < chords.length; bar++) {
            for (int note : chords[bar]) addTone(dry, bar * 4, 4, note, note, 0.018);
            for (int beat = 0; beat < 6; beat++) {
                int note = chords[bar][beat % 4] + 24;
                addWaterNote(dry, bar * 4 + beat * 0.6, note);
            }
        }
        double[] wet = dry.clone();
        // Two quiet reflections suggest the interior of a stone water chamber.
        for (int echo = 1; echo <= 2; echo++) {
            int delay = (int) (SAMPLE_RATE * 0.23 * echo);
            double gain = echo == 1 ? 0.3 : 0.15;
            for (int i = delay; i < wet.length; i++) wet[i] += dry[i - delay] * gain;
        }
        for (int i = 0; i < wet.length; i++) {
            double fade = Math.min(1, (wet.length - 1 - i) / (SAMPLE_RATE * 0.45));
            wet[i] *= fade * fade;
        }
        return pcm(wet);
    }

    private static void addWaterNote(double[] samples, double start, int note) {
        int offset = (int) (start * SAMPLE_RATE);
        int length = (int) (SAMPLE_RATE * 0.8);
        double frequency = 440 * Math.pow(2, (note - 69) / 12.0);
        for (int i = 0; i < length && offset + i < samples.length; i++) {
            double t = i / (double) SAMPLE_RATE;
            double fraction = i / (double) length;
            double envelope = Math.min(1, t / 0.012) * Math.exp(-6 * fraction) * (1 - fraction);
            double phase = 2 * Math.PI * frequency * t;
            samples[offset + i] += (Math.sin(phase) + 0.2 * Math.sin(phase * 2.01)) * envelope * 0.045;
        }
    }

    static byte[] effect(GameAudio.Effect effect) {
        double duration = switch (effect) {
            case JUMP -> 0.16;
            case COLLECT -> 0.36;
            case LOSE -> 0.5;
            case WIN -> 0.9;
            case DASH -> 0.18;
            case GLIDE -> 0.3;
            case PURCHASE -> 0.45;
            case DENIED -> 0.14;
            case SHIELD -> 0.4;
            case ROCKET -> 0.25;
        };
        double[] samples = new double[(int) (duration * SAMPLE_RATE)];
        switch (effect) {
            case JUMP -> addTone(samples, 0, duration, 67, 79, 0.18);
            case COLLECT -> {
                addTone(samples, 0, 0.18, 79, 79, 0.16);
                addTone(samples, 0.15, 0.21, 86, 86, 0.14);
            }
            case LOSE -> addTone(samples, 0, duration, 55, 36, 0.16);
            case WIN -> {
                int[] notes = {72, 76, 79, 84};
                for (int i = 0; i < notes.length; i++) {
                    addTone(samples, i * 0.16, 0.4, notes[i], notes[i], 0.13);
                }
            }
            case DASH -> addTone(samples, 0, duration, 48, 76, 0.12);
            case GLIDE -> addTone(samples, 0, duration, 81, 74, 0.09);
            case PURCHASE -> {
                addTone(samples, 0, 0.22, 76, 76, 0.12);
                addTone(samples, 0.18, 0.27, 88, 88, 0.12);
            }
            case DENIED -> addTone(samples, 0, duration, 48, 45, 0.09);
            case SHIELD -> {
                addTone(samples, 0, duration, 60, 84, 0.1);
                addTone(samples, 0.05, 0.3, 67, 91, 0.06);
            }
            case ROCKET -> addTone(samples, 0, duration, 42, 88, 0.13);
        }
        return pcm(samples);
    }

    private static void addDrums(double[] samples) {
        java.util.Random noise = new java.util.Random(73);
        // 120 BPM: kick on beats 1/3, snare on 2/4, hi-hat on eighth notes.
        for (int beat = 0; beat < MUSIC_SECONDS * 2; beat++) {
            double start = beat * 0.5;
            addDrum(samples, start, beat % 2 == 0 ? 0.24 : 0.16, beat % 2 == 0, false, noise);
            addDrum(samples, start, 0.06, false, true, noise);
            addDrum(samples, start + 0.25, 0.06, false, true, noise);
        }
    }

    private static void addDrum(double[] samples, double start, double duration,
                                boolean kick, boolean hiHat, java.util.Random noise) {
        int offset = (int) (start * SAMPLE_RATE);
        int length = (int) (duration * SAMPLE_RATE);
        double phase = 0;
        double previousNoise = 0;
        for (int i = 0; i < length && offset + i < samples.length; i++) {
            double time = i / (double) SAMPLE_RATE;
            double fraction = i / (double) length;
            double attack = Math.min(1, time / 0.002);
            double envelope = attack * Math.exp(-fraction * 7) * (1 - fraction);
            double sound;
            if (kick) {
                double frequency = 48 + 95 * Math.exp(-time * 35);
                phase += 2 * Math.PI * frequency / SAMPLE_RATE;
                sound = Math.sin(phase) * 0.12;
            } else {
                double currentNoise = noise.nextDouble() * 2 - 1;
                double highPass = (currentNoise - previousNoise) * 0.5;
                previousNoise = currentNoise;
                sound = hiHat ? highPass * 0.035
                    : currentNoise * 0.065 + Math.sin(2 * Math.PI * 180 * time) * 0.025;
            }
            samples[offset + i] += sound * envelope;
        }
    }

    private static void addTone(double[] samples, double start, double duration,
                                int fromNote, int toNote, double volume) {
        int offset = (int) (start * SAMPLE_RATE);
        int length = (int) (duration * SAMPLE_RATE);
        double phase = 0;
        for (int i = 0; i < length && offset + i < samples.length; i++) {
            double fraction = i / (double) length;
            double note = fromNote + (toNote - fromNote) * fraction;
            double frequency = 440 * Math.pow(2, (note - 69) / 12);
            phase += 2 * Math.PI * frequency / SAMPLE_RATE;
            // Smooth attack and release prevent clicks, including the music loop boundary.
            double envelope = Math.pow(Math.sin(Math.PI * fraction), 2);
            samples[offset + i] += Math.sin(phase) * envelope * volume;
        }
    }

    private static byte[] pcm(double[] samples) {
        byte[] bytes = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            short value = (short) Math.round(Math.max(-1, Math.min(1, samples[i])) * Short.MAX_VALUE);
            bytes[i * 2] = (byte) value;
            bytes[i * 2 + 1] = (byte) (value >> 8);
        }
        return bytes;
    }
}
