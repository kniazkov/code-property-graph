package com.kniazkov.cpg.build;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Immutable configuration catalog and optional named link composition. */
public record Build(List<TranslationUnit> units, Composition composition) {
    public Build(List<TranslationUnit> units) { this(units, null); }
    public Build {
        // Identical compilation commands represent one configuration, not two definitions.
        var unique = new java.util.LinkedHashMap<String, TranslationUnit>();
        units.forEach(unit -> unique.putIfAbsent(unit.variantId(), unit));
        units = List.copyOf(unique.values());
        if (composition != null) {
            var available = units.stream().map(TranslationUnit::variantId).collect(java.util.stream.Collectors.toSet());
            if (!available.containsAll(composition.variantIds()))
                throw new IllegalArgumentException("Build composition contains an unknown variant");
            var paths = new java.util.HashSet<Path>();
            for (var unit : units) if (composition.variantIds().contains(unit.variantId())
                    && !paths.add(unit.source().path()))
                throw new IllegalArgumentException("Conflicting configurations for " + unit.source().path());
        }
    }
    /** A named, explicit link composition. IDs refer to complete translation-unit configurations. */
    public record Composition(String name, List<String> variantIds) {
        public Composition {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Build name is required");
            variantIds = List.copyOf(variantIds);
            if (new java.util.HashSet<>(variantIds).size() != variantIds.size())
                throw new IllegalArgumentException("Duplicate variant in build composition");
        }
    }
    public record PhysicalSource(Path path) {
        public PhysicalSource { path = path.toAbsolutePath().normalize(); }
    }
    public record IncludePath(String kind, Path path) {
        public IncludePath { Objects.requireNonNull(kind); Objects.requireNonNull(path); }
    }
    /** Source of a configuration, independent of its effective compiler-setting identity. */
    public record ConfigurationSource(String kind, Path database, int entryOrdinal,
            List<String> arguments, String standardOverrideKind) {
        public ConfigurationSource {
            if (!List.of("database", "java-api", "cli").contains(kind))
                throw new IllegalArgumentException("Unknown configuration source: " + kind);
            if (database != null) database = database.toAbsolutePath().normalize();
            if (kind.equals("database") && (database == null || entryOrdinal < 0))
                throw new IllegalArgumentException("Database source requires a path and entry ordinal");
            arguments = List.copyOf(arguments);
            if (standardOverrideKind != null && !List.of("java-api", "cli").contains(standardOverrideKind))
                throw new IllegalArgumentException("Unknown standard override source: " + standardOverrideKind);
        }
        public static ConfigurationSource javaApi() {
            return new ConfigurationSource("java-api", null, -1, List.of(), null);
        }
        public static ConfigurationSource cli(List<String> arguments) {
            return new ConfigurationSource("cli", null, -1, arguments, null);
        }
        public static ConfigurationSource database(Path path, int ordinal, List<String> arguments) {
            return new ConfigurationSource("database", path, ordinal, arguments, null);
        }
        public ConfigurationSource withStandardOverride(String kind) {
            return new ConfigurationSource(this.kind, database, entryOrdinal, arguments, kind);
        }
    }
    /** Ordered compiler macro operation, retaining the original flag and any separate operand. */
    public record MacroOperation(boolean define, String operand, String sourceKey,
            String originalArgument, List<String> originalArguments) {
        public MacroOperation(boolean define, String operand) {
            this(define, operand, null, (define ? "-D" : "-U") + operand,
                    List.of((define ? "-D" : "-U") + operand));
        }
        public MacroOperation {
            Objects.requireNonNull(operand);
            Objects.requireNonNull(originalArgument);
            originalArguments = List.copyOf(originalArguments);
        }
    }
    public record TranslationUnit(PhysicalSource source, Path directory,
            List<IncludePath> includes, List<MacroOperation> macros, String standard,
            ConfigurationSource configurationSource) {
        public TranslationUnit(PhysicalSource source, Path directory,
                List<IncludePath> includes, List<MacroOperation> macros, String standard) {
            this(source, directory, includes, macros, standard, ConfigurationSource.javaApi());
        }
        public TranslationUnit {
            Objects.requireNonNull(source);
            Objects.requireNonNull(configurationSource);
            directory = directory.toAbsolutePath().normalize();
            Path base = directory;
            includes = includes.stream().map(p -> new IncludePath(p.kind(), base.resolve(p.path()).normalize())).toList();
            macros = List.copyOf(macros);
        }
        /** Stable content identity; list order and every supported compiler setting matter. */
        public String variantId() {
            var text = new StringBuilder();
            java.util.function.Consumer<String> add = value -> {
                if (value == null) text.append("-1:");
                else text.append(value.length()).append(':').append(value);
            };
            add.accept(source.path().toString()); add.accept(directory.toString()); add.accept(standard == null ? "c++03" : standard);
            add.accept(Integer.toString(includes.size()));
            includes.forEach(p -> { add.accept(p.kind()); add.accept(p.path().toString()); });
            add.accept(Integer.toString(macros.size()));
            macros.forEach(m -> { add.accept(Boolean.toString(m.define())); add.accept(m.operand()); });
            try {
                return "tu-" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            } catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
        }
    }
    /** Distinct occurrence identity; the same physical text may appear in several TU contexts. */
    public record IncludeInstance(TranslationUnit unit, PhysicalSource source,
            IncludeInstance parent, long occurrence) {
        public IncludeInstance { Objects.requireNonNull(unit); Objects.requireNonNull(source); }
    }
}
