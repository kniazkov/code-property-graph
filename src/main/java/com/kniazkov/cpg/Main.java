/* Code Property Graph: command-line launcher. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.Cpg.Stage;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.visualization.DotVisualizer;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Reads console options and writes source files, an SVG, or both from a single project snapshot. */
public final class Main {
    private Main() {}

    private static final String USAGE = """
        Usage: java -jar cpg.jar --input DIR --from JAVA|CPP [--stage PARSED|RESOLVED|GREEN|FOLDED]
                    [--to JAVA|CPP --out NEW_DIR] [--svg FILE]
        Choose --out, --svg, or both. Defaults: FOLDED stage, source language as target.
        SVG rendering requires Graphviz dot on PATH.
        """;

    /** Runs the console pipeline and translates argument, parsing and rendering failures into a nonzero exit status. */
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
        Project project = Cpg.read(Path.of(flags.get("--input")), new Cpg.Options(from, stage));
        Path output = flags.containsKey("--out") ? Path.of(flags.get("--out")).toAbsolutePath().normalize() : null;
        Path svg = flags.containsKey("--svg") ? Path.of(flags.get("--svg")).toAbsolutePath().normalize() : null;
        Map<String,String> generated = output == null ? Map.of() : Cpg.sources(project, to);
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
