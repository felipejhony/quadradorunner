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
        return pcm(samples);
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
        }
        return pcm(samples);
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
