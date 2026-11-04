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

    private Parser(String file, Language language, java.util.List<Token> input) {
        this.file = java.nio.file.Path.of(file).toAbsolutePath().normalize().toString();
        this.language = language;
        tokens = new TokenCursor(this.file, input, language);
        statements = new StatementParser(tokens, new ExpressionParser(tokens, language));
    }

    private static java.util.List<Token> parserTokens(CppPreprocessor.Result result) {
        var tokens = new java.util.ArrayList<Token>();
        for (var expanded : result.tokens()) {
            var token = expanded.token();
            if (token.kind() == PreprocessingToken.Kind.WHITESPACE || token.kind() == PreprocessingToken.Kind.NEWLINE) continue;
            var range = expanded.expansionRange();
            if (token.kind() == PreprocessingToken.Kind.OTHER)
                throw new ParseException(range.file(), range.start().line(), range.start().column(),
                        "Unsupported character: " + token.spelling());
            String spelling = switch (token.kind()) {
                case EOF -> "<eof>";
                case HEADER_NAME -> "#include " + token.spelling();
                default -> token.canonicalSpelling();
            };
            tokens.add(new Token(spelling, range.start(), range.end(), range.file()));
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
        try { return new Parser(file, language, new Lexer(java.nio.file.Path.of(file).getFileName().toString(), source).scan()).parse(); }
        catch (ParseException e) {
            if (language == Language.CPP) throw new ParseException(e.file(), e.line(), e.column(), e.reason() + " (C++03)");
            throw e;
        }
    }

    /** Preprocesses a configured C++ translation unit and parses its resulting tokens. */
    public static SourceFile.Builder parse(com.kniazkov.cpg.build.Build.TranslationUnit unit, String source) {
        String file = unit.source().path().toString();
        com.kniazkov.cpg.build.CppStandard.require(unit.standard(), file);
        try {
            var preprocessed = CppPreprocessor.preprocess(unit, source);
            return new Parser(file, Language.CPP, parserTokens(preprocessed)).parse();
        } catch (ParseException e) {
            throw new ParseException(e.file(), e.line(), e.column(), e.reason() + " (C++03)");
        }
    }

    /** Consumes top-level items until EOF, preserving the order of declarations and includes. */
    private SourceFile.Builder parse() {
        var result = SourceFile.create().setPath(java.nio.file.Path.of(file).getFileName().toString()).setLanguage(language);
        while (!tokens.peek().equals("<eof>")) {
            var start = tokens.start();
            if (tokens.peek().startsWith("#")) {
                var include = java.util.regex.Pattern.compile("#\\s*include\\s*(?:<([^>]+)>|\"([^\"]+)\")\\s*").matcher(tokens.peek());
                if (language != Language.CPP || !include.matches()) throw tokens.error("Only #include is supported");
                boolean system = include.group(1) != null;
                tokens.advance();
                result.addItem(tokens.span(start, IncludeDirective.create().setSystem(system).setHeader(include.group(system ? 1 : 2))));
            } else if (tokens.peek().equals("class") || tokens.peek().equals("public")) {
                result.addItem(clazz());
            } else if (language == Language.CPP && tokens.take("int")) {
                var returnType = tokens.span(start, type("int"));
                String name = tokens.identifier(); tokens.expect("("); tokens.expect(")");
                var function = FunctionDeclaration.create().setName(name).setReturnType(returnType);
                if (!tokens.take(";")) function.setBody(statements.block());
                result.addItem(tokens.span(start, function));
            } else throw tokens.error("Unsupported top-level declaration");
        }
        result.setRange(new SourceRange(file, new SourcePosition(1, 1, 0), tokens.position()));
        return result;
    }

    private static NamedType.Builder type(String name) {
        return NamedType.create().setName(name);
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
        if (language == Language.CPP) { tokens.expect("public"); tokens.expect(":"); }
        while (!tokens.take("}")) {
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
        if (language == Language.CPP) tokens.expect(";");
        return tokens.span(start, node);
    }

}
