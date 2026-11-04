/* Code Property Graph: folder pipeline and command-line entry point. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.analysis.ConsoleNormalizer;
import com.kniazkov.cpg.analysis.UnreadWrites;
import com.kniazkov.cpg.analysis.ConstantFolder;
import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.generation.Source;
import com.kniazkov.cpg.parser.ParseException;
import com.kniazkov.cpg.parser.Parser;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Parses one folder, applies the requested passes and exposes the resulting typed project. */
public final class Cpg {
    /** Last stage to execute. Each stage includes the preceding passes in declaration order. */
    public enum Stage { PARSED, RESOLVED, GREEN, FOLDED }
    /** Selects a single input language for the directory and the stage to publish. */
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
                root.addFile(Parser.parse(path.toString(), Files.readString(path), options.language()));
        } catch (IOException | java.io.UncheckedIOException e) {
            throw new ParseException(directory.toString(), 1, 1, e.getMessage());
        }
        Project result = root.build();
        if (options.stage() == Stage.PARSED) return result;
        result = NameResolver.resolve(result);
        if (options.stage() != Stage.RESOLVED) result = ConsoleNormalizer.green(result);
        if (options.stage() == Stage.FOLDED) result = ConstantFolder.fold(result);
        return UnreadWrites.analyze(result);
    }

    /** Generates one named source string per output file for the selected target language. */
    public static Map<String,String> sources(Project project, Language language) {
        return Source.files(project, language);
    }

}
