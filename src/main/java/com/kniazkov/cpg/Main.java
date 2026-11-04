/* Code Property Graph: command-line launcher. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.Cpg.Stage;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.visualization.DotVisualizer;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Reads console options and writes source files, an SVG, or both from a single project snapshot. */
public final class Main {
    private Main() {}

    private static final String USAGE = """
        Usage: java -jar cpg.jar --input DIR --from JAVA|CPP [--stage PARSED|RESOLVED|GREEN|FOLDED]
                    [--cpp-standard c++03] [--to JAVA|CPP --out NEW_DIR] [--svg FILE] [--json FILE] [--provenance on|off]
        Choose one or more outputs: --out, --svg, --json. Defaults: FOLDED stage, source language as target.
        Use --compile-commands FILE instead of --input DIR to import a C++ build.
        SVG rendering requires Graphviz dot on PATH. Macro provenance is shown by default.
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
        Set<String> allowed = Set.of("--input", "--from", "--stage", "--to", "--out", "--svg", "--json", "--cpp-standard", "--compile-commands", "--provenance");
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 >= args.length || !allowed.contains(args[i]) || args[i + 1].startsWith("--")
                    || flags.putIfAbsent(args[i], args[i+1]) != null) throw new IllegalArgumentException(USAGE);
        }
        if (!flags.containsKey("--from") || (!flags.containsKey("--input") && !flags.containsKey("--compile-commands"))
                || flags.containsKey("--input") && flags.containsKey("--compile-commands")
                || !flags.containsKey("--out") && !flags.containsKey("--svg") && !flags.containsKey("--json")
                || flags.containsKey("--to") && !flags.containsKey("--out")) throw new IllegalArgumentException(USAGE);
        Language from = Language.valueOf(flags.get("--from").toUpperCase(Locale.ROOT));
        Language to = Language.valueOf(flags.getOrDefault("--to", from.name()).toUpperCase(Locale.ROOT));
        Stage stage = Stage.valueOf(flags.getOrDefault("--stage", "FOLDED").toUpperCase(Locale.ROOT));
        String provenance = flags.getOrDefault("--provenance", "on");
        if (!provenance.equals("on") && !provenance.equals("off"))
            throw new IllegalArgumentException("--provenance must be on or off");
        Cpg.Options options = new Cpg.Options(from, stage, flags.get("--cpp-standard"));
        Project project = flags.containsKey("--compile-commands")
                ? Cpg.readCompilationDatabase(Path.of(flags.get("--compile-commands")), options)
                : Cpg.read(Path.of(flags.get("--input")), options);
        project.walk(new NodeVisitor() {
            @Override public void visit(Node node) {
                for (Diagnostic d : node.diagnostics()) {
                    String position = node.range().map(r -> r.file() + ":" + r.start().line() + ":" + r.start().column() + ": ").orElse("");
                    System.err.println(position + d.severity().name().toLowerCase(Locale.ROOT) + " [" + d.code() + "]: " + d.message());
                }
            }
        });
        Path output = flags.containsKey("--out") ? Path.of(flags.get("--out")).toAbsolutePath().normalize() : null;
        Path svg = flags.containsKey("--svg") ? Path.of(flags.get("--svg")).toAbsolutePath().normalize() : null;
        Path json = flags.containsKey("--json") ? Path.of(flags.get("--json")).toAbsolutePath().normalize() : null;
        Map<String,String> generated = output == null ? Map.of() : Cpg.sources(project, to);
        if (output != null && Files.exists(output)) throw new IllegalArgumentException("Output directory already exists: " + output);
        var destinations = new ArrayList<Path>();
        for (String name : generated.keySet()) {
            Path destination = output.resolve(name).normalize();
            if (!destination.startsWith(output) || destination.equals(output))
                throw new IllegalArgumentException("Unsafe output path: " + name);
            destinations.add(destination);
        }
        if (json != null) destinations.add(json);
        if (svg != null) destinations.add(svg);
        // An export file must not occupy a directory required by another export.
        // Check every destination before rendering SVG or writing any output.
        for (int i = 0; i < destinations.size(); i++) {
            Path destination = destinations.get(i);
            if (Files.exists(destination) || output != null && output.startsWith(destination))
                throw new IllegalArgumentException("Output conflicts with another file: " + destination);
            for (int j = 0; j < i; j++) {
                Path other = destinations.get(j);
                if (conflicts(destination, other))
                    throw new IllegalArgumentException("Output paths conflict: " + destination + " and " + other);
            }
        }
        if (svg != null) {
            Files.createDirectories(svg.getParent());
            DotVisualizer.renderSvg(project, svg, provenance.equals("on"));
            System.out.println("SVG: " + svg);
        }
        if (json != null) {
            Files.createDirectories(json.getParent());
            ProjectJson.write(project, json);
            System.out.println("JSON: " + json);
        }
        if (output != null) {
            Files.createDirectories(output);
            for (var entry : generated.entrySet()) {
                Path destination = output.resolve(entry.getKey()).normalize();
                if (!destination.startsWith(output)) throw new IllegalArgumentException("Unsafe output path: " + entry.getKey());
                Files.createDirectories(destination.getParent());
                Files.writeString(destination, entry.getValue(), StandardOpenOption.CREATE_NEW);
            }
            System.out.println("Generated " + generated.size() + " " + to + " file(s) in " + output);
        }
    }

    private static boolean conflicts(Path left, Path right) {
        return left.startsWith(right) || right.startsWith(left);
    }

}
