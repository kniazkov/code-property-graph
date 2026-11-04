/* Code Property Graph: assertions over real CLI output. MIT License. */
package com.kniazkov.cpg.e2e;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

/** Captures the process status, streams and files from a single invocation. */
record CliRun(Path work, int status, String stdout, String stderr) {
    CliRun expectStatus(int expected) {
        assertEquals(expected, status, () -> work + "\nstdout:\n" + stdout + "\nstderr:\n" + stderr);
        return this;
    }

    JsonSnapshot snapshot() throws Exception {
        expectStatus(0);
        return JsonSnapshot.read(work.resolve("snapshot.json"));
    }

    /** Checks the complete generated file set and file contents, allowing platform line endings. */
    void expectSources(Map<String, String> expectedResources) throws IOException {
        Path dir = work.resolve("generated");
        try (var paths = Files.walk(dir)) {
            assertEquals(expectedResources.keySet(), paths.filter(Files::isRegularFile)
                    .map(p -> dir.relativize(p).toString().replace(java.io.File.separatorChar, '/'))
                    .collect(Collectors.toSet()), "Generated file names");
        }
        for (var entry : expectedResources.entrySet())
            assertEquals(resource(entry.getValue()).replace("\r\n", "\n"),
                    Files.readString(dir.resolve(entry.getKey())).replace("\r\n", "\n"), entry.getKey());
    }

    void expectNoExports() {
        assertTrue(Files.notExists(work.resolve("generated")), "Unexpected generated source directory");
        assertTrue(Files.notExists(work.resolve("snapshot.json")), "Unexpected JSON snapshot");
    }

    static String resource(String path) throws IOException {
        try (var in = CliRun.class.getResourceAsStream(path)) {
            return new String(Objects.requireNonNull(in, "Missing fixture " + path).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
