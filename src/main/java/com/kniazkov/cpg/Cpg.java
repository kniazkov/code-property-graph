/* Code Property Graph: folder pipeline and command-line entry point. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.visualization.DotVisualizer;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Parses one folder, applies the requested passes and exposes the resulting typed project. */
public final class Cpg {
    public enum Stage { PARSED, RESOLVED, GREEN, FOLDED }
    public record Options(Language language, Stage stage) {
        public Options {
            Objects.requireNonNull(stage);
            if (language != Language.JAVA && language != Language.CPP)
                throw new IllegalArgumentException("Input language must be JAVA or CPP");
        }
    }

    private Cpg() {}

    /** Reads matching files in name order and returns a complete snapshot at the requested stage. */
    public static Project read(Path directory, Options options) {
        var root = Project.create();
        String suffix = options.language() == Language.JAVA ? ".java" : ".cpp";
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(suffix)).sorted().toList())
                root.addFile(new Parser(path.getFileName().toString(), Files.readString(path), options.language()).parse());
        } catch (IOException | java.io.UncheckedIOException e) {
            throw new ParseException(directory.toString(), 1, 1, e.getMessage());
        }
        Project result = root.build();
        if (options.stage() == Stage.PARSED) return result;
        result = Passes.resolve(result);
        if (options.stage() == Stage.RESOLVED) return result;
        result = Passes.green(result);
        return options.stage() == Stage.GREEN ? result : Passes.fold(result);
    }

    public static Map<String,String> sources(Project project, Language language) {
        return Source.files(project, language);
    }

    private static final String USAGE = """
        Usage: java -jar cpg.jar --input DIR --from JAVA|CPP [--stage PARSED|RESOLVED|GREEN|FOLDED]
                    [--to JAVA|CPP --out NEW_DIR] [--svg FILE]
        Choose --out, --svg, or both. Defaults: FOLDED stage, source language as target.
        SVG rendering requires Graphviz dot on PATH.
        """;

    public static void main(String[] args) {
        try {
            if (args.length == 1 && (args[0].equals("--help") || args[0].equals("-h"))) {
                System.out.print(USAGE);
                return;
            }
            run(args);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Rendering interrupted");
            System.exit(1);
        } catch (IOException | RuntimeException e) {
            System.err.println("CPG: " + e.getMessage());
            System.exit(1);
        }
    }

    static void run(String... args) throws IOException, InterruptedException {
        if (args.length == 4 && !args[0].startsWith("--"))
            args = new String[]{"--from", args[0], "--stage", args[1], "--input", args[2], "--out", args[3]};
        Map<String,String> flags = new HashMap<>();
        Set<String> allowed = Set.of("--input", "--from", "--stage", "--to", "--out", "--svg");
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 >= args.length || !allowed.contains(args[i]) || args[i + 1].startsWith("--")
                    || flags.putIfAbsent(args[i], args[i+1]) != null) throw new IllegalArgumentException(USAGE);
        }
        if (!flags.containsKey("--from") || !flags.containsKey("--input")
                || !flags.containsKey("--out") && !flags.containsKey("--svg")
                || flags.containsKey("--to") && !flags.containsKey("--out")) throw new IllegalArgumentException(USAGE);
        Language from = Language.valueOf(flags.get("--from").toUpperCase(Locale.ROOT));
        Language to = Language.valueOf(flags.getOrDefault("--to", from.name()).toUpperCase(Locale.ROOT));
        Stage stage = Stage.valueOf(flags.getOrDefault("--stage", "FOLDED").toUpperCase(Locale.ROOT));
        Project project = read(Path.of(flags.get("--input")), new Options(from, stage));
        Path output = flags.containsKey("--out") ? Path.of(flags.get("--out")).toAbsolutePath().normalize() : null;
        Path svg = flags.containsKey("--svg") ? Path.of(flags.get("--svg")).toAbsolutePath().normalize() : null;
        Map<String,String> generated = output == null ? Map.of() : sources(project, to);
        if (output != null && Files.exists(output)) throw new IllegalArgumentException("Output directory already exists: " + output);
        if (svg != null) {
            if (Files.exists(svg) || svg.equals(output) || output != null && generated.keySet().stream().anyMatch(n -> output.resolve(n).equals(svg)))
                throw new IllegalArgumentException("SVG output conflicts with another file: " + svg);
            Files.createDirectories(svg.getParent());
            DotVisualizer.renderSvg(project, svg);
            System.out.println("SVG: " + svg);
        }
        if (output != null) {
            Files.createDirectories(output);
            for (var entry : generated.entrySet())
                Files.writeString(output.resolve(entry.getKey()), entry.getValue(), StandardOpenOption.CREATE_NEW);
            System.out.println("Generated " + generated.size() + " " + to + " file(s) in " + output);
        }
    }
}
