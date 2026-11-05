/* Code Property Graph: folder pipeline and command-line entry point. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.build.Build;
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
    public record Options(Language language, Stage stage, String cppStandard,
            Build.ConfigurationSource configurationSource, com.kniazkov.cpg.build.PlatformProfile platform) {
        public Options(Language language, Stage stage) { this(language, stage, null); }
        public Options(Language language, Stage stage, String cppStandard) {
            this(language, stage, cppStandard, Build.ConfigurationSource.javaApi(), null);
        }
        public Options(Language language, Stage stage, String cppStandard, Build.ConfigurationSource configurationSource) {
            this(language, stage, cppStandard, configurationSource, null);
        }
        public Options withPlatform(com.kniazkov.cpg.build.PlatformProfile profile) {
            return new Options(language, stage, cppStandard, configurationSource, Objects.requireNonNull(profile));
        }
        public Options {
            Objects.requireNonNull(stage);
            Objects.requireNonNull(configurationSource);
            if (platform != null && language != Language.CPP) throw new IllegalArgumentException("Platform profile requires CPP input");
            if (cppStandard != null) {
                if (language != Language.CPP) throw new IllegalArgumentException("C++ standard requires CPP input");

            }
            if (language != Language.JAVA && language != Language.CPP)
                throw new IllegalArgumentException("Input language must be JAVA or CPP");
        }
    }

    private Cpg() {}

    /** Reads matching files in name order and returns a complete snapshot at the requested stage. */
    public static Project read(Path directory, Options options) {
        return read(directory, options, false);
    }

    /**
     * Reads C++ units independently, retaining syntax prefixes and parse problems when a unit
     * cannot be completed. A project with parse errors remains at PARSED even when a later
     * stage was requested. Java parsing and invalid build configurations remain strict.
     */
    public static Project readRecovering(Path directory, Options options) {
        return read(directory, options, true);
    }

    private static Project read(Path directory, Options options, boolean recover) {
        var root = Project.create();
        String suffix = options.language() == Language.JAVA ? ".java" : ".cpp";
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(suffix)).sorted().toList()) {
                if (options.language() == Language.CPP) {
                    var unit = new Build.TranslationUnit(new Build.PhysicalSource(path),
                            path.toAbsolutePath().getParent(), List.of(), List.of(), options.cppStandard(),
                            options.configurationSource(), options.platform() == null
                                    ? com.kniazkov.cpg.build.PlatformProfile.defaults() : options.platform());
                    String source = Files.readString(path, java.nio.charset.Charset.forName(unit.platform().sourceEncoding()));
                    root.addFile(recover ? Parser.parseRecovering(unit, source) : Parser.parse(unit, source));
                } else root.addFile(Parser.parse(path.toString(), Files.readString(path), options.language(), options.cppStandard()));
            }
        } catch (IOException | java.io.UncheckedIOException e) {
            throw new ParseException(directory.toString(), 1, 1, e.getMessage());
        }
        if (options.language() == Language.CPP) com.kniazkov.cpg.build.CppStandard.require(options.cppStandard(), directory.toString());
        return finish(root.build(), options);
    }

    /** Reads a compilation database, applying an optional explicit edition override. */
    public static Project readCompilationDatabase(Path database, Options options) {
        try { return read(com.kniazkov.cpg.build.CompilationDatabase.read(database), options); }
        catch (IOException e) { throw new ParseException(database.toString(), 1, 1, e.getMessage()); }
    }

    /** Reads a valid compilation database while retaining errors in individual C++ units. */
    public static Project readCompilationDatabaseRecovering(Path database, Options options) {
        try { return readRecovering(com.kniazkov.cpg.build.CompilationDatabase.read(database), options); }
        catch (IOException e) { throw new ParseException(database.toString(), 1, 1, e.getMessage()); }
    }

    /** Parses configurations independently and retains their identities and optional link composition. */
    public static Project read(com.kniazkov.cpg.build.Build build, Options options) {
        return read(build, options, false);
    }

    /** Parses each configured C++ unit independently and preserves diagnostic snapshots. */
    public static Project readRecovering(com.kniazkov.cpg.build.Build build, Options options) {
        return read(build, options, true);
    }

    private static Project read(com.kniazkov.cpg.build.Build build, Options options, boolean recover) {
        if (options.language() != Language.CPP) throw new IllegalArgumentException("Compilation builds require CPP");
        var effectiveUnits = build.units().stream().map(unit -> new com.kniazkov.cpg.build.Build.TranslationUnit(
                unit.source(), unit.directory(), unit.includes(), unit.macros(),
                options.cppStandard() != null ? options.cppStandard() : unit.standard() == null ? "c++03" : unit.standard(),
                options.cppStandard() == null ? unit.configurationSource()
                        : unit.configurationSource().withStandardOverride(options.configurationSource().kind()),
                options.platform() == null ? unit.platform() : options.platform())).toList();
        var composition = build.composition();
        if (composition != null && (options.cppStandard() != null || options.platform() != null)) {
            var ids = new ArrayList<String>();
            for (int i = 0; i < build.units().size(); i++)
                if (composition.variantIds().contains(build.units().get(i).variantId())) ids.add(effectiveUnits.get(i).variantId());
            composition = new com.kniazkov.cpg.build.Build.Composition(composition.name(), ids.stream().distinct().toList());
        }
        var effectiveBuild = new com.kniazkov.cpg.build.Build(effectiveUnits, composition);
        var root = Project.create().setBuild(effectiveBuild);
        for (var unit : effectiveBuild.units()) {
            if (composition != null && !composition.variantIds().contains(unit.variantId())) continue;
            String file = unit.source().path().toString();
            com.kniazkov.cpg.build.CppStandard.require(unit.standard(), file);
            try {
                String source = Files.readString(unit.source().path(), java.nio.charset.Charset.forName(unit.platform().sourceEncoding()));
                root.addFile(recover ? Parser.parseRecovering(unit, source) : Parser.parse(unit, source));
            }
            catch (IOException e) { throw new ParseException(file, 1, 1, e.getMessage()); }
        }
        com.kniazkov.cpg.build.CppStandard.require(options.cppStandard(), "empty compilation build");
        return finish(root.build(), options);
    }

    private static Project finish(Project result, Options options) {
        // Analyses assume complete syntax and may rebuild nodes. Keep partial syntax and
        // original parser diagnostics intact rather than analyzing an incomplete program.
        if (options.stage() == Stage.PARSED || result.hasErrors()) return result;
        result = NameResolver.resolve(result);
        // Green rewrites clear annotations. Do not erase the invalid syntax that
        // produced semantic errors before the folding stage can observe them.
        boolean[] semanticError = {false};
        result.walk(new NodeVisitor() {
            @Override public void visit(Node node) {
                semanticError[0] |= node.diagnostics().stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
            }
        });
        if (semanticError[0]) return result;
        if (options.stage() != Stage.RESOLVED) result = com.kniazkov.cpg.analysis.TypeNormalizer.green(ConsoleNormalizer.green(result));
        if (options.stage() == Stage.FOLDED) result = ConstantFolder.fold(result);
        // Transformations deliberately drop annotations; recompute links and build diagnostics.
        if (options.stage() != Stage.RESOLVED) result = NameResolver.resolve(result);
        return UnreadWrites.analyze(result);
    }

    /** Generates one named source string per output file for the selected target language. */
    public static Map<String,String> sources(Project project, Language language) {
        return Source.files(project, language);
    }

}
