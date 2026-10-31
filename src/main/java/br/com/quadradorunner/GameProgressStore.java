package br.com.quadradorunner;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import static br.com.quadradorunner.GameProgress.*;

/** One local save slot. Blocking I/O: callers must keep it outside the Swing EDT.
 * Intended for one writer; concurrent game instances are not coordinated.
 */
final class GameProgressStore {
    private static final int SCHEMA_VERSION = 1;
    private final Path file;

    GameProgressStore() {
        this(Path.of(System.getProperty("user.home"), ".quadrado-runner", "progress.properties"));
    }

    GameProgressStore(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    GameProgress load() throws IOException {
        Properties data = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            data.load(reader);
        } catch (NoSuchFileException exception) {
            return new GameProgress();
        } catch (IllegalArgumentException exception) {
            throw invalid("Malformed properties", exception);
        }
        try {
            int version = number(data, "schema.version");
            if (version != SCHEMA_VERSION) {
                throw new SaveFormatException("Unsupported save schema version: " + version);
            }
            int balance = number(data, "balance");
            int ringCount = count(data, "rings.count");
            Set<RingId> rings = new HashSet<>();
            for (int i = 0; i < ringCount; i++) {
                RingId ring = new RingId(required(data, "ring." + i + ".level"),
                    required(data, "ring." + i + ".id"));
                if (!rings.add(ring)) throw new IllegalArgumentException("Duplicate ring ID");
            }
            int abilityCount = count(data, "abilities.count");
            Set<Ability> abilities = new HashSet<>();
            for (int i = 0; i < abilityCount; i++) {
                if (!abilities.add(Ability.valueOf(required(data, "ability." + i)))) {
                    throw new IllegalArgumentException("Duplicate ability");
                }
            }
            int levelCount = count(data, "levels.count");
            Set<String> levels = new HashSet<>();
            for (int i = 0; i < levelCount; i++) {
                if (!levels.add(required(data, "level." + i))) {
                    throw new IllegalArgumentException("Duplicate completed level ID");
                }
            }
            if (data.size() != 5L + 2L * ringCount + abilityCount + levelCount) {
                throw new IllegalArgumentException("Unexpected save fields");
            }
            return new GameProgress(balance, rings, abilities, levels);
        } catch (IllegalArgumentException exception) {
            throw invalid("Invalid or incomplete save", exception);
        }
    }

    void save(GameProgress progress) throws IOException {
        Objects.requireNonNull(progress, "progress");
        // Refuse to replace corrupt or newer-version data with a fresh snapshot.
        load();
        Properties data = encode(progress);
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "progress-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                data.store(writer, "Quadrado Runner progress");
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            // No non-atomic fallback: if unsupported, fail and keep the old save intact.
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Properties encode(GameProgress progress) {
        Properties data = new Properties();
        data.setProperty("schema.version", Integer.toString(SCHEMA_VERSION));
        data.setProperty("balance", Integer.toString(progress.balance()));
        data.setProperty("rings.count", Integer.toString(progress.collectedRings().size()));
        int index = 0;
        for (RingId ring : progress.collectedRings()) {
            data.setProperty("ring." + index + ".level", ring.levelId());
            data.setProperty("ring." + index + ".id", ring.ringId());
            index++;
        }
        data.setProperty("abilities.count", Integer.toString(progress.unlockedAbilities().size()));
        index = 0;
        for (Ability ability : progress.unlockedAbilities()) {
            data.setProperty("ability." + index++, ability.name());
        }
        data.setProperty("levels.count", Integer.toString(progress.completedLevelIds().size()));
        index = 0;
        for (String level : progress.completedLevelIds()) data.setProperty("level." + index++, level);
        return data;
    }

    private int number(Properties data, String key) {
        int value = Integer.parseInt(required(data, key));
        if (value < 0) throw new IllegalArgumentException(key + " cannot be negative");
        return value;
    }

    private int count(Properties data, String key) {
        int value = number(data, key);
        if (value > data.size()) throw new IllegalArgumentException("Invalid count: " + key);
        return value;
    }

    private String required(Properties data, String key) {
        String value = data.getProperty(key);
        if (value == null) throw new IllegalArgumentException("Missing save field: " + key);
        return value;
    }

    private SaveFormatException invalid(String message, Exception cause) {
        return new SaveFormatException(message + ": " + file, cause);
    }

    static final class SaveFormatException extends IOException {
        SaveFormatException(String message) { super(message); }
        SaveFormatException(String message, Throwable cause) { super(message, cause); }
    }
}
