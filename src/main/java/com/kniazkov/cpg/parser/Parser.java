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
    private String languageLinkage = "C++";
    private final java.util.Deque<String> classNames = new java.util.ArrayDeque<>();
    private final java.util.Deque<java.util.List<Runnable>> deferredBodies = new java.util.ArrayDeque<>();

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
        statements.setDeclarationReader(() -> cppDeclarations(false, true));
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
                } else if (language == Language.JAVA && (tokens.peek().equals("class") || tokens.peek().equals("public"))) {
                    result.addItem(clazz());
                } else if (language == Language.CPP && tokens.take(";")) {
                    // Empty namespace-scope declarations have no semantic identity.
                } else if (language == Language.CPP) {
                    for (var declaration : cppDeclarations(false, false)) result.addItem(declaration);
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
    private java.util.List<NodeBuilder<? extends Declaration>> declarations(boolean member, boolean local) {
        var start = tokens.start();
        var specs = types.specifiers();
        validateStorage(specs.modifiers(), member, local);
        var result = new java.util.ArrayList<NodeBuilder<? extends Declaration>>();
        int declaratorCount = 0;
        do {
            declaratorCount++;
            var declarator = member && tokens.peek().equals(":") ? new TypeParser.Declarator("", java.util.function.Function.identity()) : types.declarator(false);
            var type = declarator.apply().apply(specs.type().get());
            types.validateDefaultPlacement(type, !specs.typedef());
            var function = types.functionType(type);
            result.addAll(types.takeImplicitDeclarations());
            if (specs.typedef()) {
                if (!specs.modifiers().isEmpty()) throw tokens.error("Storage specifier on typedef");
                var declaration = TypedefDeclaration.create().setName(declarator.name()).setType(type);
                types.declareAlias(declarator.name(), declaration);
                result.add(tokens.span(start, declaration));
            } else if (function != null) {
                CallableDeclaration.Builder<?, ?> declaration = member || declarator.name().contains("::") ? MethodDeclaration.create() : FunctionDeclaration.create();
                if (!(type instanceof FunctionType.Builder)) declaration.setWrittenType(type);
                declaration.setName(declarator.name()).setReturnType(function.returnType().orElseThrow())
                        .setParameters(function.parameters()).setModifiers(specs.modifiers()).setVariadic(function.variadic())
                        .setConst(function.isConst()).setVolatile(function.isVolatile());
                if (!member && !declarator.name().contains("::") && (function.isConst() || function.isVolatile())) throw tokens.error("Free function cannot have cv qualifiers");
                if (specs.modifiers().stream().anyMatch(java.util.Set.of("auto", "register", "mutable")::contains))
                    throw tokens.error("Invalid function storage class");
                declaration.setLanguageLinkage(member ? "C++" : languageLinkage);
                declaration.setDeclarationDefinition(tokens.peek().equals("{"));
                types.declareName(declarator.name());
                if (tokens.peek().equals("{")) {
                    if (local) throw tokens.error("Function definition is not permitted in a block");
                    if (declaration.writtenType().isPresent()) throw tokens.error("A function definition cannot use a function typedef declarator in C++03");
                    if (member && !deferredBodies.isEmpty()) {
                        int bodyPosition = tokens.checkpoint();
                        try {
                            tokens.skipBlock();
                            deferredBodies.peek().add(types.inCurrentScope(() -> {
                                int resume = tokens.checkpoint(); tokens.restore(bodyPosition);
                                try { functionBody(declaration, function.parameters()); }
                                finally { tokens.restore(resume); }
                            }));
                        } catch (ParseException error) {
                            if (recovery == null) throw error;
                            for (var earlier : deferredBodies.peek()) earlier.run();
                            deferredBodies.peek().clear();
                            tokens.restore(bodyPosition);
                            if (!recovery.stopped()) functionBody(declaration, function.parameters());
                        }
                    } else functionBody(declaration, function.parameters());
                    result.add(tokens.span(start, declaration));
                    return result;
                }
                result.add(tokens.span(start, declaration));
            } else {
                if (specs.modifiers().contains("inline")) throw tokens.error("inline applies only to functions in C++03");
                var declaration = VariableDeclaration.create().setName(declarator.name()).setType(type).setModifiers(specs.modifiers());
                declaration.setLanguageLinkage(member || local ? "C++" : languageLinkage);
                types.declareName(declarator.name());
                if (member && tokens.take(":")) declaration.setBitWidth(expressions.parse());
                if (tokens.peek().equals("(")) declaration.setInitializer(initializer(InitializationExpression.Form.DIRECT));
                else if (tokens.take("=")) {
                    var initializerStart = tokens.start();
                    try { declaration.setInitializer(tokens.peek().equals("{") ? initializer(InitializationExpression.Form.AGGREGATE) : expressions.parse()); }
                    catch (ParseException error) {
                        if (recovery == null) throw error;
                        var skipped = tokens.recoverInitializer(initializerStart);
                        if (skipped == null) throw error;
                        declaration.setInitializer(ErrorExpression.create().setProblem(recovery.recovered(error, skipped)));
                    }
                }
                result.addAll(types.takeImplicitDeclarations());
                declaration.setDeclarationDefinition((!specs.modifiers().contains("extern") || declaration.initializer().isPresent())
                        && !(member && specs.modifiers().contains("static")));
                result.add(tokens.span(start, declaration));
            }
        } while (tokens.take(","));
        tokens.expect(";");
        if (declaratorCount == 1) tokens.span(start, result.getLast());
        return result;
    }

    private InitializationExpression.Builder initializer(InitializationExpression.Form form) {
        var start = tokens.start();
        String open = form == InitializationExpression.Form.AGGREGATE ? "{" : "(";
        String close = form == InitializationExpression.Form.AGGREGATE ? "}" : ")";
        tokens.expect(open);
        var result = InitializationExpression.create().setForm(form);
        if (!tokens.take(close)) {
            do {
                result.addArgument(tokens.peek().equals("{") ? initializer(InitializationExpression.Form.AGGREGATE) : expressions.parse());
                if (!tokens.take(",")) break;
                if (tokens.peek().equals(close) && form == InitializationExpression.Form.AGGREGATE) break;
            } while (true);
            tokens.expect(close);
        }
        return tokens.span(start, result);
    }

    private boolean specialMemberAhead(boolean member) {
        int offset = 0;
        while (java.util.Set.of("inline", "explicit").contains(tokens.peek(offset))) offset++;
        if (member && !classNames.isEmpty())
            return tokens.peek(offset).equals("~") || tokens.peek(offset).equals(classNames.peek()) && tokens.peek(offset + 1).equals("(");
        if (tokens.peek(offset).equals("::")) offset++;
        String owner = tokens.peek(offset);
        while (tokens.peek(offset + 1).equals("::")) {
            offset += 2;
            if (tokens.peek(offset).equals("~")) return true;
            String next = tokens.peek(offset);
            if (next.equals(owner) && tokens.peek(offset + 1).equals("(")) return true;
            owner = next;
        }
        return false;
    }

    private MethodDeclaration.Builder specialMember() {
        var start = tokens.start();
        var modifiers = new java.util.ArrayList<String>();
        while (java.util.Set.of("inline", "explicit").contains(tokens.peek())) { modifiers.add(tokens.peek()); tokens.advance(); }
        var declarator = types.declarator(false);
        var function = types.functionType(declarator.apply().apply(NamedType.create().setName("void")));
        if (function == null) throw tokens.error("Expected constructor or destructor parameter clause");
        boolean destructor = declarator.name().contains("~");
        if (function.isConst() || function.isVolatile() || destructor && (!function.parameters().isEmpty() || function.variadic())) throw tokens.error("Invalid special member signature");
        if (destructor && modifiers.contains("explicit")) throw tokens.error("explicit applies only to constructors");
        var result = MethodDeclaration.create().setName(declarator.name()).setKind(destructor ? CallableDeclaration.Kind.DESTRUCTOR : CallableDeclaration.Kind.CONSTRUCTOR)
                .setParameters(function.parameters()).setVariadic(function.variadic()).setModifiers(modifiers);
        if (tokens.take(":")) {
            if (destructor) throw tokens.error("Destructor cannot have initializer list");
            do {
                var itemStart = tokens.start();
                var item = MemberInitializer.create().setName(types.qualifiedName());
                tokens.expect("(");
                if (!tokens.take(")")) { do { item.addArgument(expressions.parse()); } while (tokens.take(",")); tokens.expect(")"); }
                result.addInitializer(tokens.span(itemStart, item));
            } while (tokens.take(","));
        }
        result.setDeclarationDefinition(tokens.peek().equals("{"));
        if (tokens.peek().equals("{")) {
            if (!deferredBodies.isEmpty()) {
                int bodyPosition = tokens.checkpoint(); tokens.skipBlock();
                deferredBodies.peek().add(types.inCurrentScope(() -> {
                    int resume = tokens.checkpoint(); tokens.restore(bodyPosition);
                    try { functionBody(result, function.parameters()); } finally { tokens.restore(resume); }
                }));
            } else functionBody(result, function.parameters());
        } else tokens.expect(";");
        return tokens.span(start, result);
    }

    private void functionBody(CallableDeclaration.Builder<?, ?> declaration,
            java.util.List<NodeBuilder<? extends ParameterDeclaration>> parameters) {
        types.inQualifier(declaration.name().orElse(""), () -> {
            types.pushScope();
            try {
                for (var parameter : parameters) types.declareName(((ParameterDeclaration.Builder) parameter).name().orElse(""));
                declaration.setBody(statements.block());
            } finally { types.popScope(); }
        });
    }

    private void validateStorage(java.util.List<String> modifiers, boolean member, boolean local) {
        var storage = modifiers.stream().filter(m -> !m.equals("inline")).toList();
        if (new java.util.HashSet<>(modifiers).size() != modifiers.size() || storage.size() > 1)
            throw tokens.error("Conflicting or duplicate storage-class specifiers");
        if (modifiers.contains("mutable") && !member) throw tokens.error("mutable requires a class member");
        if ((modifiers.contains("auto") || modifiers.contains("register")) && !local)
            throw tokens.error("auto and register require block scope");
        if (member && modifiers.contains("extern")) throw tokens.error("extern is not a class-member storage class");
    }

    /** Namespace and class bodies use the same declaration dispatch as the translation unit. */
    private java.util.List<NodeBuilder<? extends Declaration>> cppDeclarations(boolean member, boolean local) {
        var start = tokens.start();
        if (tokens.take("friend")) {
            if (!member) throw tokens.error("friend requires class scope");
            var declarations = cppDeclarations(false, false);
            declarations.forEach(declaration -> declaration.setFriend(true));
            return declarations;
        }
        if (specialMemberAhead(member)) return java.util.List.of(specialMember());
        if (tokens.peek().equals("namespace")) {
            if (member || local) throw tokens.error("Namespace definitions require namespace scope");
            return java.util.List.of(namespace());
        }
        if (tokens.peek().equals("using")) {
            if (member && tokens.peek(1).equals("namespace")) throw tokens.error("A using-directive is not permitted in class scope");
            return java.util.List.of(using());
        }
        if (tokens.peek().equals("extern") && tokens.peek(1).startsWith("\"")) {
            if (member || local) throw tokens.error("Language linkage specifications require namespace scope");
            tokens.advance(); String spelling = tokens.peek(); tokens.advance();
            if (!spelling.equals("\"C\"") && !spelling.equals("\"C++\"")) throw tokens.error("Unsupported language linkage " + spelling);
            String previous = languageLinkage;
            languageLinkage = spelling.substring(1, spelling.length() - 1);
            try {
                var result = new java.util.ArrayList<NodeBuilder<? extends Declaration>>();
                if (tokens.take("{")) {
                    while (!tokens.take("}")) {
                        if (tokens.take(";")) continue;
                        result.addAll(cppDeclarations(false, false));
                    }
                } else {
                    result.addAll(cppDeclarations(false, false));
                    for (var declaration : result) {
                        if (declaration instanceof VariableDeclaration.Builder variable && variable.initializer().isEmpty()) {
                            if (!variable.modifiers().contains("extern")) variable.addModifier("extern");
                            variable.setDeclarationDefinition(false);
                        }
                    }
                    if (result.size() == 1) tokens.span(start, result.getFirst());
                }
                return result;
            } finally { languageLinkage = previous; }
        }
        if (java.util.Set.of("class", "struct", "union").contains(tokens.peek())
                && java.util.Set.of("{", ";", ":").contains(tokens.peek(2))) {
            var declaration = cppClass();
            var result = new java.util.ArrayList<NodeBuilder<? extends Declaration>>(types.takeImplicitDeclarations());
            result.add(declaration); return result;
        }
        if (tokens.peek().equals("enum") && (tokens.peek(1).equals("{") || tokens.peek(2).equals("{")))
            return java.util.List.of(enumeration());
        if (!types.startsType()) { tokens.checkUnsupportedDeclaration(); throw tokens.error("Unsupported declaration or unknown type name"); }
        return declarations(member, local);
    }

    private NodeBuilder<? extends Declaration> namespace() {
        var start = tokens.start(); tokens.expect("namespace");
        boolean anonymous = tokens.peek().equals("{");
        String name = anonymous ? "" : tokens.identifier();
        if (tokens.take("=")) {
            String target = types.qualifiedName(); tokens.expect(";"); types.namespaceAlias(name, target);
            return tokens.span(start, NamespaceAliasDeclaration.create().setName(name).setTargetName(target));
        }
        var result = NamespaceDeclaration.create().setName(name).setAnonymous(anonymous);
        tokens.expect("{"); types.pushNamedScope(name);
        try {
            while (!tokens.take("}")) {
                if (tokens.take(";")) continue;
                for (var declaration : cppDeclarations(false, false)) result.addMember(declaration);
            }
        } finally { types.popScope(); }
        if (anonymous) types.usingNamespace("");
        return tokens.span(start, result);
    }

    private NodeBuilder<? extends Declaration> using() {
        var start = tokens.start(); tokens.expect("using");
        if (tokens.take("namespace")) {
            String name = types.qualifiedName(); tokens.expect(";"); types.usingNamespace(name);
            return tokens.span(start, UsingDirective.create().setNamespaceName(name));
        }
        String name = types.qualifiedName();
        if (!name.contains("::")) throw tokens.error("A using-declaration requires a qualified name");
        tokens.expect(";"); types.usingDeclaration(name);
        return tokens.span(start, UsingDeclaration.create().setQualifiedName(name));
    }

    private EnumDeclaration.Builder enumeration() {
        var start = tokens.start(); tokens.expect("enum");
        String name = tokens.peek().equals("{") ? "" : tokens.identifier();
        if (!name.isEmpty()) types.registerClass(name);
        var result = EnumDeclaration.create().setName(name);
        tokens.expect("{");
        if (!tokens.take("}")) {
            do {
                var itemStart = tokens.start(); String itemName = tokens.identifier();
                var item = EnumeratorDeclaration.create().setName(itemName);
                if (tokens.take("=")) item.setValue(expressions.parse());
                types.declareName(itemName);
                result.addEnumerator(tokens.span(itemStart, item));
                if (!tokens.take(",")) break;
                if (tokens.peek().equals("}")) throw tokens.error("Trailing enum comma is not permitted in C++03");
            } while (true);
            tokens.expect("}");
        }
        tokens.expect(";");
        return tokens.span(start, result);
    }

    private ClassDeclaration.Builder cppClass() {
        var start = tokens.start(); String keyword = tokens.peek(); tokens.advance();
        String name = tokens.identifier(); types.registerClass(name);
        String access = keyword.equals("class") ? "private" : "public";
        var result = ClassDeclaration.create().setName(name).setKeyword(keyword).setMemberAccess(access);
        if (tokens.take(";")) {
            result.setDeclarationDefinition(false);
            return tokens.span(start, result);
        }
        if (tokens.take(":")) {
            if (keyword.equals("union")) throw tokens.error("Union cannot have base classes");
            do {
                var baseStart = tokens.start();
                String baseAccess = access;
                boolean virtual = tokens.take("virtual");
                if (java.util.Set.of("public", "protected", "private").contains(tokens.peek())) { baseAccess = tokens.peek(); tokens.advance(); }
                if (!virtual) virtual = tokens.take("virtual");
                String baseName = types.qualifiedName();
                result.addBase(tokens.span(baseStart, BaseSpecifier.create().setBaseAccess(baseAccess).setVirtual(virtual).setType(NamedType.create().setName(baseName))));
            } while (tokens.take(","));
        }
        tokens.expect("{"); types.pushNamedScope(name, true); classNames.push(name);
        var deferred = new java.util.ArrayList<Runnable>(); deferredBodies.push(deferred);
        try {
            while ((recovery == null || !recovery.stopped()) && !tokens.take("}")) {
                if (java.util.Set.of("public", "private", "protected").contains(tokens.peek())) {
                    String next = tokens.peek(); tokens.advance(); tokens.expect(":");
                    access = next;
                    if (result.members().isEmpty()) result.setMemberAccess(access);
                } else if (tokens.take(";")) { /* Empty member declaration. */ }
                else for (var declaration : cppDeclarations(true, false)) { declaration.setAccess(access); result.addMember(declaration); }
            }
        } catch (ParseException error) {
            if (recovery == null) throw error;
            // A malformed class tail must not discard already delimited inline bodies.
            for (var body : deferred) {
                if (recovery.stopped()) break;
                body.run();
            }
            deferred.clear();
            recovery.stop(error, tokens.currentRange());
        } finally { types.popScope(); deferredBodies.pop(); classNames.pop(); }
        if (recovery == null || !recovery.stopped()) {
            if (!deferredBodies.isEmpty()) deferredBodies.peek().addAll(deferred);
            else for (var body : deferred) {
                try { body.run(); }
                catch (ParseException error) { if (recovery == null) throw error; recovery.stop(error, tokens.currentRange()); break; }
            }
        }
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
