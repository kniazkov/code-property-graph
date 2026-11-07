/* Code Property Graph: black-box test workspace. MIT License. */
package com.kniazkov.cpg.e2e;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Builds an input folder and invokes the packaged CLI in a fresh working directory for each run. */
final class CliSandbox {
    private final Path workspace;
    private final long timeoutNanos;
    final Path input;

    CliSandbox(Path workspace) throws IOException {
        this(workspace, Duration.ofSeconds(45));
    }

    /** A finite per-sandbox deadline; ordinary scenarios retain the 45-second default. */
    CliSandbox(Path workspace, Duration timeout) throws IOException {
        Objects.requireNonNull(timeout, "timeout");
        try { timeoutNanos = timeout.toNanos(); }
        catch (ArithmeticException error) { throw new IllegalArgumentException("CLI timeout must fit a finite nanosecond budget", error); }
        if (timeoutNanos <= 0) throw new IllegalArgumentException("CLI timeout must be positive");
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

    /** Requests diagnostic JSON and sources; invalid C++ syntax retains JSON but blocks sources. */
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
        int status = awaitExit(child, command, err);
        return new CliRun(work, status, Files.readString(out), Files.readString(err));
    }

    /** Kept separate so deadline and cleanup behavior can be tested without sleeping. */
    int awaitExit(Process child, List<String> command, Path err) throws IOException, InterruptedException {
        try {
            if (!child.waitFor(timeoutNanos, TimeUnit.NANOSECONDS))
                fail("Timed out after " + Duration.ofNanos(timeoutNanos) + ": " + command + "\n" + Files.readString(err));
            return child.exitValue();
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
                child.waitFor();
            }
        }
    }
}
