/* Code Property Graph: source file and declaration grammar. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.model.*;

/**
 * Entry point for parsing one source file into typed builders. Declarations own
 * the outer grammar and delegate method bodies to StatementParser. All readers
 * consume the same cursor, so nested rules resume at the first unconsumed token.
 * Name resolution takes place after all files have been assembled into a project.
 */
public final class Parser {
    private final String file;
    private final Language language;
    private final TokenCursor tokens;
    private final StatementParser statements;
    private final ExpressionParser expressions;
    private final TypeParser types;
    private final ParseRecovery recovery;

    private Parser(String file, Language language, java.util.List<Token> input, Preprocessing preprocessing) {
        this(file, language, input, preprocessing, null);
    }

    private Parser(String file, Language language, java.util.List<Token> input, Preprocessing preprocessing, ParseRecovery recovery) {
        this.recovery = recovery;
        this.file = java.nio.file.Path.of(file).toAbsolutePath().normalize().toString();
        this.language = language;
        tokens = new TokenCursor(this.file, input, language, preprocessing);
        expressions = new ExpressionParser(tokens, language, recovery);
        types = new TypeParser(tokens, expressions);
        expressions.setTypes(types);
        statements = new StatementParser(tokens, expressions, recovery, types);
    }

    private static java.util.List<Token> parserTokens(CppPreprocessor.Result result, Preprocessing provenance) { return parserTokens(result, provenance, false, null); }

    private static java.util.List<Token> parserTokens(CppPreprocessor.Result result, Preprocessing provenance, boolean recovering, ParseException terminalProblem) {
        var tokens = new java.util.ArrayList<Token>();
        for (int index = 0; index < result.tokens().size(); index++) {
            var expanded = result.tokens().get(index);
            var token = expanded.token();
            if (token.kind() == PreprocessingToken.Kind.WHITESPACE || token.kind() == PreprocessingToken.Kind.NEWLINE) continue;
            var range = expanded.expansionRange();
            String violation = CppDialect.violation(result.tokens(), index);
            ParseException problem = violation == null ? null : new ParseException(range.file(), range.start().line(), range.start().column(), violation, range);
            if (problem != null) problem = problem.withDiagnostic("cpp03-unsupported", violation + " [token: " + token.spelling() + "]", range,
                    DiagnosticProvenance.related(provenance, index), DiagnosticProvenance.context(provenance, index));
            if (token.kind() == PreprocessingToken.Kind.EOF && terminalProblem != null) problem = terminalProblem;
            if (problem != null && !recovering) throw problem;
            String spelling = switch (token.kind()) {
                case EOF -> "<eof>";
                case HEADER_NAME -> "#include " + token.spelling();
                default -> token.canonicalSpelling();
            };
            tokens.add(new Token(spelling, range.start(), range.end(), range.file(), index, problem));
        }
        return java.util.List.copyOf(tokens);
    }

    /**
     * Parses one complete Java or C++ source file in the supported subset.
     * @param file source file name used in the graph and diagnostics
     * @param source complete source text
     * @param language grammar selected for the input folder
     * @return the parsed file ready to add to a project builder
     * @throws ParseException if a token or construction is unsupported
     */
    public static SourceFile.Builder parse(String file, String source, Language language) {
        return parse(file, source, language, null);
    }

    /** Edition-aware entry point; null preserves the C++03 default. */
    public static SourceFile.Builder parse(String file, String source, Language language, String standard) {
        if (language == Language.CPP) com.kniazkov.cpg.build.CppStandard.require(standard, file);
        else if (standard != null) throw new IllegalArgumentException("C++ standard requires CPP input");
        if (language != Language.JAVA && language != Language.CPP)
            throw new IllegalArgumentException("Input language must be JAVA or CPP");
        if (language == Language.CPP) {
            var path = java.nio.file.Path.of(file).toAbsolutePath().normalize();
            var unit = new com.kniazkov.cpg.build.Build.TranslationUnit(
                    new com.kniazkov.cpg.build.Build.PhysicalSource(path), path.getParent(),
                    java.util.List.of(), java.util.List.of(), standard);
            return parse(unit, source);
        }
        try { return new Parser(file, language, new Lexer(java.nio.file.Path.of(file).getFileName().toString(), source).scan(), null).parse(); }
        catch (ParseException e) {
            if (language == Language.CPP) throw e.withReason(e.reason().contains("C++03") ? e.reason() : e.reason() + " (C++03)");
            throw e;
        }
    }

    /** Preprocesses a configured C++ translation unit and parses its resulting tokens. */
    public static SourceFile.Builder parse(com.kniazkov.cpg.build.Build.TranslationUnit unit, String source) {
        return parse(unit, source, null);
    }

    /** Parses using only supplied physical sources when the map is non-null. */
    public static SourceFile.Builder parse(com.kniazkov.cpg.build.Build.TranslationUnit unit, String source,
            java.util.Map<String, String> sources) {
        return parse(unit, source, sources, null);
    }

    /** Replays preprocessing with the original translation time for date/time macros. */
    public static SourceFile.Builder parse(com.kniazkov.cpg.build.Build.TranslationUnit unit, String source,
            java.util.Map<String, String> sources, java.time.Instant translationTime) {
        String file = unit.source().path().toString();
        com.kniazkov.cpg.build.CppStandard.require(unit.standard(), file);
        try {
            var preprocessed = translationTime != null ? CppPreprocessor.preprocess(unit, source, sources, translationTime)
                    : sources == null ? CppPreprocessor.preprocess(unit, source) : CppPreprocessor.preprocess(unit, source, sources);
            var provenance = preprocessing(file, preprocessed, unit);
            var parsed = new Parser(file, Language.CPP, parserTokens(preprocessed, provenance), provenance).parse();
            parsed.setProvenance(new TokenProvenance(provenance, 0, provenance.tokens().size()));
            return parsed;
        } catch (ParseException e) {
            throw e.withReason(e.reason().contains("C++03") ? e.reason() : e.reason() + " (C++03)");
        }
    }

    /** Parses a diagnostic snapshot, recovering only at reliable expression boundaries. */
    public static SourceFile.Builder parseRecovering(String file, String source, Language language) {
        return parseRecovering(file, source, language, null);
    }

    /** Edition/configuration errors remain exceptions, even in diagnostic mode. */
    public static SourceFile.Builder parseRecovering(String file, String source, Language language, String standard) {
        if (language == Language.CPP) {
            com.kniazkov.cpg.build.CppStandard.require(standard, file);
            var path = java.nio.file.Path.of(file).toAbsolutePath().normalize();
            var unit = new com.kniazkov.cpg.build.Build.TranslationUnit(
                    new com.kniazkov.cpg.build.Build.PhysicalSource(path), path.getParent(), java.util.List.of(), java.util.List.of(), standard);
            return parseRecovering(unit, source);
        }
        if (language != Language.JAVA) throw new IllegalArgumentException("Input language must be JAVA or CPP");
        if (standard != null) throw new IllegalArgumentException("C++ standard requires CPP input");
        String path = java.nio.file.Path.of(file).toAbsolutePath().normalize().toString();
        var recovery = new ParseRecovery(language, java.util.Map.of(path, source));
        var parsed = new Parser(path, language, new Lexer(path, source).scanRecovering(), null, recovery).parse();
        parsed.setRange(new SourceRange(path, new SourcePosition(1, 1, 0), ParseRecovery.position(source, source.length())));
        return parsed;
    }

    public static SourceFile.Builder parseRecovering(com.kniazkov.cpg.build.Build.TranslationUnit unit, String source) {
        return parseRecovering(unit, source, null, null);
    }

    public static SourceFile.Builder parseRecovering(com.kniazkov.cpg.build.Build.TranslationUnit unit, String source,
            java.util.Map<String, String> sources) {
        return parseRecovering(unit, source, sources, null);
    }

    /** Replays the same translation parameters while retaining failures as graph metadata. */
    public static SourceFile.Builder parseRecovering(com.kniazkov.cpg.build.Build.TranslationUnit unit, String source,
            java.util.Map<String, String> sources, java.time.Instant translationTime) {
        String file = unit.source().path().toString();
        com.kniazkov.cpg.build.CppStandard.require(unit.standard(), file);
        var prepared = CppPreprocessor.preprocessRecovering(unit, source, sources, translationTime);
        var provenance = preprocessing(file, prepared.result(), unit);
        var recovery = new ParseRecovery(Language.CPP, provenance.sources(), new DiagnosticContext(file, unit.variantId(), java.util.List.of(), java.util.List.of()));
        var parsed = new Parser(file, Language.CPP, parserTokens(prepared.result(), provenance, true, prepared.problem()), provenance, recovery).parse();
        parsed.setProvenance(new TokenProvenance(provenance, 0, provenance.tokens().size()));
        parsed.setRange(new SourceRange(file, new SourcePosition(1, 1, 0), ParseRecovery.position(source, source.length())));
        return parsed;
    }

    private static Preprocessing.Fragment fragment(CppPreprocessor.Fragment fragment) {
        return new Preprocessing.Fragment(fragment.range(), fragment.include().occurrence(), fragment.spelling());
    }

    private static Preprocessing preprocessing(String file, CppPreprocessor.Result result, com.kniazkov.cpg.build.Build.TranslationUnit unit) {
        var includes = result.includes().stream().map(i -> new Preprocessing.Include(i.occurrence(),
                i.source().path().toString(), i.parent() == null ? null : i.parent().occurrence())).toList();
        var tokens = new java.util.ArrayList<Preprocessing.Token>();
        for (int i = 0; i < result.tokens().size(); i++) {
            var t = result.tokens().get(i);
            tokens.add(new Preprocessing.Token(i, t.token().spelling(), t.token().range(),
                    t.expansionRange(), t.include().occurrence(), t.expansionIds()));
        }
        var events = result.expansions().stream().map(e -> new Preprocessing.Expansion(
                e.id(), e.name(), e.definition(), e.replacement().stream().map(Parser::fragment).toList(),
                e.invocation(), e.include().occurrence(), e.parentId(),
                e.arguments().stream().map(a -> a.stream().map(Parser::fragment).toList()).toList(),
                e.operations().stream().map(o -> new Preprocessing.Operation(o.kind(), o.operator(),
                        o.fragments().stream().map(Parser::fragment).toList(), o.spelling())).toList(),
                e.startToken(), e.endToken(), e.definitionId())).toList();
        return new Preprocessing(file, includes, tokens, events, result.sources(), unit,
                result.definitions().stream().map(d -> new Preprocessing.Definition(d.id(), d.name(),
                        d.functionLike(), d.parameters(), d.rawReplacement(), d.range(), d.include().occurrence(),
                        d.originKind(), externalSourceKey(unit, d))).toList(),
                result.branches().stream().map(b -> new Preprocessing.Branch(b.id(), b.directive(), b.rawSource(),
                        b.range(), b.directiveRange(), b.include().occurrence(), b.parentId(), b.parentActive(),
                        b.evaluated(), b.conditionResult(), b.active(), b.startToken(), b.endToken())).toList(),
                result.presenceChecks().stream().map(c -> new Preprocessing.PresenceCheck(c.id(), c.name(), c.range(),
                        c.include().occurrence(), c.present(), c.conditionResult(), c.definitionId(), c.branchId())).toList(),
                result.directives().stream().map(d -> new Preprocessing.Directive(d.kind(), d.rawSource(), d.range(),
                        d.include().occurrence(), d.active())).toList(),
                result.logicalLocations().stream().map(l -> new Preprocessing.LogicalLocation(l.range(),
                        l.include().occurrence(), l.file(), l.line())).toList(),
                result.includeStates().stream().map(s -> new Preprocessing.IncludeState(s.include().occurrence(), s.activeDefinitions())).toList(), result.translationTime());
    }

    private static String externalSourceKey(com.kniazkov.cpg.build.Build.TranslationUnit unit, CppPreprocessor.Definition definition) {
        if (definition.originKind().equals("predefined")) return java.util.Set.of("__LINE__", "__FILE__", "__DATE__", "__TIME__", "__cplusplus").contains(definition.sourceKey())
                ? "profile:c++03:" + definition.sourceKey() : "profile:platform:" + unit.platform().identityHash() + ":" + definition.sourceKey();
        if (!definition.originKind().equals("command-line")) return definition.sourceKey();
        int ordinal = Integer.parseInt(definition.sourceKey());
        var source = unit.configurationSource();
        String key = source.kind().equals("database") ? "database:" + source.database() + ":entry:" + source.entryOrdinal()
                : source.kind() + ":" + unit.variantId();
        var operation = unit.macros().get(ordinal - 1);
        return key + ":" + (operation.sourceKey() == null ? "operation:" + ordinal : operation.sourceKey());
    }

    /** Consumes top-level items until EOF, preserving the order of declarations and includes. */
    private SourceFile.Builder parse() {
        var result = SourceFile.create().setPath(java.nio.file.Path.of(file).getFileName().toString()).setLanguage(language);
        while (recovery == null || !recovery.stopped()) {
            try {
                if (tokens.peek().equals("<eof>")) break;
                var start = tokens.start();
                if (tokens.peek().startsWith("#")) {
                    var include = java.util.regex.Pattern.compile("#\\s*include\\s*(?:<([^>]+)>|\"([^\"]+)\")\\s*").matcher(tokens.peek());
                    if (language != Language.CPP || !include.matches()) throw tokens.error("Only #include is supported");
                    boolean system = include.group(1) != null;
                    tokens.advance();
                    result.addItem(tokens.span(start, IncludeDirective.create().setSystem(system).setHeader(include.group(system ? 1 : 2))));
                } else if (language == Language.CPP && (tokens.peek().equals("class") || tokens.peek().equals("struct") || tokens.peek().equals("union")) && tokens.peek(2).equals("{")) {
                    result.addItem(cppClass());
                } else if (language == Language.JAVA && (tokens.peek().equals("class") || tokens.peek().equals("public"))) {
                    result.addItem(clazz());
                } else if (language == Language.CPP && types.startsType()) {
                    for (var declaration : declarations(false)) result.addItem(declaration);
                } else {
                    if (language == Language.CPP) tokens.checkUnsupportedDeclaration();
                    throw tokens.error("Unsupported top-level declaration");
                }
            } catch (ParseException error) {
                if (recovery == null) throw error;
                recovery.stop(error, tokens.currentRange());
            }
        }
        result.setRange(new SourceRange(file, new SourcePosition(1, 1, 0), tokens.position()));
        if (recovery != null) recovery.attach(result);
        return result;
    }

    private static NamedType.Builder type(String name) {
        return NamedType.create().setName(name);
    }

    /** One declaration-specifier sequence may introduce several independently owned declarators. */
    private java.util.List<NodeBuilder<? extends Declaration>> declarations(boolean member) {
        var start = tokens.start();
        var specs = types.specifiers();
        var result = new java.util.ArrayList<NodeBuilder<? extends Declaration>>();
        do {
            var declarator = types.declarator(false);
            var type = declarator.apply().apply(specs.type().get());
            if (specs.typedef()) {
                if (!specs.modifiers().isEmpty()) throw tokens.error("Storage specifier on typedef");
                var declaration = TypedefDeclaration.create().setName(declarator.name()).setType(type);
                types.declareAlias(declarator.name(), declaration);
                result.add(tokens.span(start, declaration));
            } else if (type instanceof FunctionType.Builder function) {
                CallableDeclaration.Builder<?, ?> declaration = member ? MethodDeclaration.create() : FunctionDeclaration.create();
                declaration.setName(declarator.name()).setReturnType(function.returnType().orElseThrow())
                        .setParameters(function.parameters()).setModifiers(specs.modifiers()).setVariadic(function.variadic())
                        .setConst(function.isConst()).setVolatile(function.isVolatile());
                if (!member && (function.isConst() || function.isVolatile())) throw tokens.error("Free function cannot have cv qualifiers");
                types.declareName(declarator.name());
                if (tokens.peek().equals("{")) {
                    types.pushScope();
                    try {
                        for (var parameter : function.parameters()) types.declareName(((ParameterDeclaration.Builder) parameter).name().orElse(""));
                        declaration.setBody(statements.block());
                    } finally { types.popScope(); }
                    result.add(tokens.span(start, declaration));
                    return result;
                }
                result.add(tokens.span(start, declaration));
            } else {
                if (type.build().canonicalType() instanceof FunctionType) throw tokens.error("Function declarations through typedef names are not yet supported");
                if (!specs.modifiers().isEmpty()) throw tokens.error("Variable storage specifiers are not yet supported");
                var declaration = VariableDeclaration.create().setName(declarator.name()).setType(type);
                types.declareName(declarator.name());
                if (tokens.take("=")) declaration.setInitializer(expressions.parse());
                result.add(tokens.span(start, declaration));
            }
        } while (tokens.take(","));
        tokens.expect(";");
        if (result.size() == 1) tokens.span(start, result.getFirst());
        return result;
    }

    private ClassDeclaration.Builder cppClass() {
        var start = tokens.start(); String keyword = tokens.peek(); tokens.advance();
        String name = tokens.identifier(); types.registerClass(name);
        String access = keyword.equals("class") ? "private" : "public";
        var result = ClassDeclaration.create().setName(name).setKeyword(keyword).setMemberAccess(access);
        tokens.expect("{"); types.pushScope();
        try {
            while ((recovery == null || !recovery.stopped()) && !tokens.take("}")) {
                if (java.util.Set.of("public", "private", "protected").contains(tokens.peek())) {
                    String next = tokens.peek(); tokens.advance(); tokens.expect(":");
                    if (!result.members().isEmpty() && !next.equals(access)) throw tokens.error("Mixed class access sections are not yet supported");
                    access = next; result.setMemberAccess(access);
                } else if (tokens.take(";")) { /* Empty member declaration. */ }
                else for (var declaration : declarations(true)) result.addMember(declaration);
            }
        } catch (ParseException error) {
            if (recovery == null) throw error;
            recovery.stop(error, tokens.currentRange());
        } finally { types.popScope(); }
        if (recovery == null || !recovery.stopped()) {
            try { tokens.expect(";"); }
            catch (ParseException error) { if (recovery == null) throw error; recovery.stop(error, tokens.currentRange()); }
        }
        return tokens.span(start, result);
    }

    /** Parses the supported class form and dispatches each method body to the statement reader. */
    private ClassDeclaration.Builder clazz() {
        var start = tokens.start();
        boolean isPublic = tokens.take("public");
        if (isPublic && language == Language.CPP) throw tokens.error("Unexpected public before class");
        tokens.expect("class");
        var node = ClassDeclaration.create().setName(tokens.identifier());
        if (isPublic) node.addModifier("public");
        tokens.expect("{");
        try {
            if (language == Language.CPP) { tokens.expect("public"); tokens.expect(":"); }
            while ((recovery == null || !recovery.stopped()) && !tokens.take("}")) {
                var methodStart = tokens.start();
                var method = MethodDeclaration.create();
                if (language == Language.JAVA) { tokens.expect("public"); method.addModifier("public"); }
                tokens.expect("static");
                var typeStart = tokens.start();
                tokens.expect("void");
                var returnType = tokens.span(typeStart, type("void")); tokens.expect("main"); tokens.expect("(");
                method.addModifier("static").setName("main").setReturnType(returnType);
                if (language == Language.JAVA) {
                    var paramStart = tokens.start();
                    tokens.expect("String"); tokens.expect("["); tokens.expect("]");
                    var paramType = tokens.span(paramStart, type("String[]"));
                    method.addParameter(tokens.span(paramStart, ParameterDeclaration.create().setType(paramType).setName(tokens.identifier())));
                }
                tokens.expect(")");
                node.addMember(tokens.span(methodStart, method.setBody(statements.block())));
            }
            if (language == Language.CPP && (recovery == null || !recovery.stopped())) tokens.expect(";");
        } catch (ParseException error) {
            if (recovery == null) throw error;
            recovery.stop(error, tokens.currentRange());
        }
        return tokens.span(start, node);
    }

}
