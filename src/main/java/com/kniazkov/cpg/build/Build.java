package com.kniazkov.cpg.build;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Immutable compilation inputs, independent of graph snapshots and future macro expansion. */
public record Build(List<TranslationUnit> units) {
    public Build { units = List.copyOf(units); }
    public record PhysicalSource(Path path) {
        public PhysicalSource { path = path.toAbsolutePath().normalize(); }
    }
    public record IncludePath(String kind, Path path) {
        public IncludePath { Objects.requireNonNull(kind); Objects.requireNonNull(path); }
    }
    /** Ordered compiler macro operation, not a claim that expansion is already supported. */
    public record MacroOperation(boolean define, String operand) {
        public MacroOperation { Objects.requireNonNull(operand); }
    }
    public record TranslationUnit(PhysicalSource source, Path directory,
            List<IncludePath> includes, List<MacroOperation> macros, String standard) {
        public TranslationUnit {
            Objects.requireNonNull(source);
            directory = directory.toAbsolutePath().normalize();
            Path base = directory;
            includes = includes.stream().map(p -> new IncludePath(p.kind(), base.resolve(p.path()).normalize())).toList();
            macros = List.copyOf(macros);
        }
    }
    /** Distinct occurrence identity; the same physical text may appear in several TU contexts. */
    public record IncludeInstance(TranslationUnit unit, PhysicalSource source,
            IncludeInstance parent, long occurrence) {
        public IncludeInstance { Objects.requireNonNull(unit); Objects.requireNonNull(source); }
    }
}
