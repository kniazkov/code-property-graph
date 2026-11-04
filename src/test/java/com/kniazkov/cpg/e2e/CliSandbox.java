/* Code Property Graph: black-box test workspace. MIT License. */
package com.kniazkov.cpg.e2e;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Builds an input folder and invokes the packaged CLI in a fresh working directory for each run. */
final class CliSandbox {
    private final Path workspace;
    final Path input;

    CliSandbox(Path workspace) throws IOException {
        this.workspace = workspace;
        input = Files.createDirectory(workspace.resolve("input sources"));
    }

    CliSandbox source(String name, String contents) throws IOException {
        Files.createDirectories(input.resolve(name).getParent());
        Files.writeString(input.resolve(name), contents, StandardOpenOption.CREATE_NEW);
        return this;
    }

    /** Copies a checked-in scenario, preserving headers and nested relative paths. */
    CliSandbox directory(Path source) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.sorted().toList()) {
                Path destination = input.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else Files.copy(path, destination);
            }
        }
        return this;
    }

    /** Always requests both exports, including for negative cases where neither should be created. */
    CliRun run(String language, String... options) throws IOException, InterruptedException {
        return runInput(language, "--input", input, options);
    }

    /** Imports a database from a fresh unrelated process working directory. */
    CliRun runDatabase(Path database, String... options) throws IOException, InterruptedException {
        return runInput("cpp", "--compile-commands", database, options);
    }

    /** Imports all variants for analysis without asking for a potentially conflicting source set. */
    CliRun runDatabaseJson(Path database, String... options) throws IOException, InterruptedException {
        return runInput("cpp", "--compile-commands", database, false, options);
    }

    private CliRun runInput(String language, String inputFlag, Path source, String... options)
            throws IOException, InterruptedException {
        return runInput(language, inputFlag, source, true, options);
    }

    private CliRun runInput(String language, String inputFlag, Path source, boolean sources, String... options)
            throws IOException, InterruptedException {
        Path jar = Path.of(Objects.requireNonNull(System.getProperty("cpg.e2e.jar"), "Run via mvn verify"));
        assertTrue(Files.isRegularFile(jar), () -> "Missing packaged CLI: " + jar);
        Path work = Files.createTempDirectory(workspace, "run-");
        Path out = work.resolve("stdout.txt"), err = work.resolve("stderr.txt");
        String exe = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", exe).toString(),
                "-Dfile.encoding=UTF-8", "-jar", jar.toAbsolutePath().toString(), "--from", language,
                inputFlag, source.toString(), "--json", "snapshot.json"));
        if (sources) command.addAll(List.of("--out", "generated"));
        command.addAll(List.of(options));
        Process child = new ProcessBuilder(command).directory(work.toFile())
                .redirectOutput(out.toFile()).redirectError(err.toFile()).start();
        try {
            if (!child.waitFor(45, TimeUnit.SECONDS)) fail("Timed out: " + command + "\n" + Files.readString(err));
            return new CliRun(work, child.exitValue(), Files.readString(out), Files.readString(err));
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
                child.waitFor();
            }
        }
    }
}
