package br.com.quadradorunner;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static br.com.quadradorunner.GameProgress.Ability.*;
import static org.junit.jupiter.api.Assertions.*;

class GameProgressStoreTest {
    @TempDir Path directory;

    private Path file() { return directory.resolve("saves/progress.properties"); }

    private GameProgress snapshot() {
        return new GameProgress(1, Set.of(new GameProgress.RingId("level-01", "ring-01"),
            new GameProgress.RingId("level-02", "ring-01")), Set.of(DASH, GLIDE), Set.of("level-01"));
    }

    @Test void absentFileReturnsInitialProgressWithoutCreatingAnything() throws IOException {
        assertEquals(new GameProgress(), new GameProgressStore(file()).load());
        assertFalse(Files.exists(file().getParent()));
    }

    @Test void newStoreAfterRestartRestoresEntireSnapshotAndSchemaVersion() throws IOException {
        GameProgress original = snapshot();
        new GameProgressStore(file()).save(original);
        assertEquals(original, new GameProgressStore(file()).load());
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(file())) { properties.load(reader); }
        assertEquals("1", properties.getProperty("schema.version"));
        assertEquals("2", properties.getProperty("rings.count"));
        assertEquals("1", properties.getProperty("balance"));
    }

    @Test void replacesExistingSaveCompletelyAndLeavesNoTemporaryFiles() throws IOException {
        GameProgressStore store = new GameProgressStore(file());
        store.save(snapshot());
        GameProgress changed = new GameProgress(0, snapshot().collectedRings(), Set.of(DASH), Set.of());
        store.save(changed);
        assertEquals(changed, new GameProgressStore(file()).load());
        try (var files = Files.list(file().getParent())) {
            assertEquals(List.of(file()), files.toList());
        }
    }

    @Test void supportsUnicodeAndSpecialCharactersWithoutMixingRingIdentities() throws IOException {
        GameProgress progress = new GameProgress(2,
            Set.of(new GameProgress.RingId("fase:á=1", " argola\\\n2 "),
                new GameProgress.RingId("fase", "á=1:argola")), Set.of(), Set.of("fase:á=1"));
        new GameProgressStore(file()).save(progress);
        assertEquals(progress, new GameProgressStore(file()).load());
    }

    @Test void corruptFilesAreReportedAndNeverOverwritten() throws IOException {
        for (String invalid : List.of("", "not a save", "schema.version=1\nbalance=0\n",
                "schema.version=1\nbalance=\\uZZZZ")) {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), invalid);
            GameProgressStore store = new GameProgressStore(file());
            assertThrows(GameProgressStore.SaveFormatException.class, store::load);
            assertThrows(GameProgressStore.SaveFormatException.class, () -> store.save(new GameProgress()));
            assertEquals(invalid, Files.readString(file()));
        }
    }

    @Test void unsupportedVersionsAreExplicitlyReportedAndPreserved() throws IOException {
        GameProgressStore store = new GameProgressStore(file());
        store.save(snapshot());
        String future = Files.readString(file()).replace("schema.version=1", "schema.version=99");
        Files.writeString(file(), future);
        var exception = assertThrows(GameProgressStore.SaveFormatException.class, store::load);
        assertTrue(exception.getMessage().contains("99"));
        assertThrows(GameProgressStore.SaveFormatException.class, () -> store.save(new GameProgress()));
        assertEquals(future, Files.readString(file()));
    }

    @Test void rejectsInvalidValuesDuplicatesCountsAndUnexpectedFields() throws IOException {
        GameProgressStore store = new GameProgressStore(file());
        store.save(snapshot());
        Properties valid = new Properties();
        try (var reader = Files.newBufferedReader(file())) { valid.load(reader); }
        for (String[] change : new String[][] {
            {"balance", "-1"}, {"balance", "2147483648"}, {"balance", "zero"},
            {"rings.count", "-1"}, {"rings.count", "2147483647"}, {"rings.count", "0"},
            {"ring.0.id", " "}, {"ring.0.level", ""}, {"ability.0", "FLY"},
            {"ability.0", valid.getProperty("ability.1")}, {"level.0", "\t"},
            {"unexpected", "value"}
        }) {
            Properties invalid = new Properties();
            invalid.putAll(valid);
            invalid.setProperty(change[0], change[1]);
            write(invalid);
            assertThrows(GameProgressStore.SaveFormatException.class, store::load, change[0] + "=" + change[1]);
        }
        Properties duplicateRing = new Properties();
        duplicateRing.putAll(valid);
        duplicateRing.setProperty("ring.1.level", valid.getProperty("ring.0.level"));
        duplicateRing.setProperty("ring.1.id", valid.getProperty("ring.0.id"));
        write(duplicateRing);
        assertThrows(GameProgressStore.SaveFormatException.class, store::load);
        Properties duplicateLevel = new Properties();
        duplicateLevel.putAll(valid);
        duplicateLevel.setProperty("levels.count", "2");
        duplicateLevel.setProperty("level.1", valid.getProperty("level.0"));
        write(duplicateLevel);
        assertThrows(GameProgressStore.SaveFormatException.class, store::load);
    }

    @Test void ioFailuresAreNotMistakenForMissingSaveOrResetToEmpty() throws IOException {
        Files.createDirectories(file()); // A directory at the save-file path is an I/O error.
        GameProgressStore store = new GameProgressStore(file());
        assertThrows(IOException.class, store::load);
        assertThrows(IOException.class, () -> store.save(snapshot()));
        assertTrue(Files.isDirectory(file()));
        try (var entries = Files.list(file().getParent())) {
            assertEquals(List.of(file()), entries.toList());
        }
    }

    @Test void incompleteTemporaryWriteDoesNotChangeLastCommittedSave() throws IOException {
        GameProgressStore store = new GameProgressStore(file());
        store.save(snapshot());
        Files.writeString(file().resolveSibling("progress-interrupted.tmp"), "schema.version=1\nbal");
        assertEquals(snapshot(), new GameProgressStore(file()).load());
    }

    private void write(Properties properties) throws IOException {
        StringWriter writer = new StringWriter();
        properties.store(writer, "Test data");
        Files.writeString(file(), writer.toString());
    }
}
