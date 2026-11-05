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
        C++ parse errors retain diagnostic JSON/SVG, block source generation, and exit nonzero.
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
        Cpg.Options options = new Cpg.Options(from, stage, flags.get("--cpp-standard"),
                com.kniazkov.cpg.build.Build.ConfigurationSource.cli(Arrays.asList(args)));
        Project project = flags.containsKey("--compile-commands")
                ? Cpg.readCompilationDatabaseRecovering(Path.of(flags.get("--compile-commands")), options)
                : Cpg.readRecovering(Path.of(flags.get("--input")), options);
        printParseProblems(project);
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
        boolean broken = project.hasErrors();
        // Never invoke any source emitter for an invalid project, including preserved-source
        // replay. Diagnostic exports are independent of the blocked source output.
        Map<String,String> generated = output == null || broken ? Map.of() : Cpg.sources(project, to);
        if (output != null && !broken && Files.exists(output)) throw new IllegalArgumentException("Output directory already exists: " + output);
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
            if (broken && output != null && destination.startsWith(output))
                throw new IllegalArgumentException("Diagnostic export must be outside the blocked source directory: " + destination);
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
        if (broken) throw new IllegalArgumentException("Project contains parse errors"
                + (output == null ? "" : "; source generation is blocked"));
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

    /** One primary error per root cause, with distinct evidence retained as labeled notes. */
    static void printParseProblems(Project project) {
        for (DiagnosticGroup group : project.diagnosticGroups()) {
            System.err.println(position(group.primaryRange()) + "error [" + group.code() + "]: " + group.message()
                    + " (" + count(group.translationUnitCount(), "translation unit")
                    + ", " + count(group.variantCount(), "variant") + ", " + count(group.occurrenceCount(), "occurrence") + ")");
            group.occurrences().stream().flatMap(p -> p.relatedRanges().stream()).distinct()
                    .sorted(Comparator.comparing((DiagnosticRelatedRange r) -> r.range().file())
                            .thenComparingInt(r -> r.range().start().offset())
                            .thenComparingInt(r -> r.range().end().offset())
                            .thenComparing(DiagnosticRelatedRange::role).thenComparing(DiagnosticRelatedRange::label))
                    .forEach(r -> System.err.println(position(r.range()) + "note [" + r.role() + "]: " + r.label()));
        }
    }

    private static String position(SourceRange range) {
        return range.file() + ":" + range.start().line() + ":" + range.start().column() + ": ";
    }

    private static String count(int number, String noun) {
        return number + " " + noun + (number == 1 ? "" : "s");
    }

    private static boolean conflicts(Path left, Path right) {
        return left.startsWith(right) || right.startsWith(left);
    }

}
