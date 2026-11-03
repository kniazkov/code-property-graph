/* Folder-to-graph entry point and a small demonstration CLI. MIT License. */
package com.kniazkov.cpg;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import com.kniazkov.cpg.Graph.*;

/** Reads one non-recursive source folder using one explicitly selected language. */
public final class Cpg {
    public enum Stage { PARSED, RESOLVED, GREEN, FOLDED }
    public record Options(Language language, Stage stage) {
        public Options { Objects.requireNonNull(language); Objects.requireNonNull(stage); }
    }
    private Cpg() {}
    public static Graph read(Path directory, Options options) {
        Objects.requireNonNull(options);
        try {
            if (!Files.isDirectory(directory)) throw new IOException("Not a directory");
            Draft project = new Draft(Kind.PROJECT, directory.getFileName().toString());
            String suffix = options.language() == Language.JAVA ? ".java" : ".cpp";
            try (var paths = Files.list(directory)) {
                for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(suffix)).sorted().toList()) {
                    project.add(new Parser(path.getFileName().toString(), Files.readString(path, StandardCharsets.UTF_8), options.language()).parse());
                }
            }
            Graph graph = new Builder(project).build();
            if (options.stage().ordinal() >= Stage.RESOLVED.ordinal()) graph = Passes.resolve(graph);
            if (options.stage().ordinal() >= Stage.GREEN.ordinal()) graph = Passes.green(graph);
            if (options.stage().ordinal() >= Stage.FOLDED.ordinal()) graph = Passes.fold(graph);
            return graph;
        } catch (ParseException e) { throw e; }
        catch (IOException | IllegalStateException e) { throw new ParseException(directory.toString(), 1, 1, e.getMessage()); }
    }
    /** Returns one generated string per source file; no files are overwritten. */
    public static Map<String, String> sources(Graph graph, Language language) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Node file : graph.root().children()) result.put(file.text(), file.source(language));
        return Collections.unmodifiableMap(result);
    }
    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            System.err.println("Usage: Cpg <JAVA|CPP> <PARSED|RESOLVED|GREEN|FOLDED> <input-folder> <output-folder>");
            System.exit(2);
        }
        Language language = Language.valueOf(args[0]);
        Graph graph = read(Path.of(args[2]), new Options(language, Stage.valueOf(args[1])));
        Map<String, String> generated = sources(graph, language);
        Path output = Path.of(args[3]);
        if (Files.exists(output)) throw new IllegalArgumentException("Output folder must not exist");
        Files.createDirectories(output);
        for (var entry : generated.entrySet()) Files.writeString(output.resolve(entry.getKey()), entry.getValue(), StandardCharsets.UTF_8);
        System.out.println("Generated " + generated.size() + " file(s), stage " + args[1]);
    }
}
