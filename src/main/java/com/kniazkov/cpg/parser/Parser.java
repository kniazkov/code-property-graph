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

    private Parser(String file, String source, Language language) {
        this.file = file;
        this.language = language;
        tokens = new TokenCursor(file, new Lexer(file, source).scan());
        statements = new StatementParser(tokens, new ExpressionParser(tokens, language));
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
        if (language != Language.JAVA && language != Language.CPP)
            throw new IllegalArgumentException("Input language must be JAVA or CPP");
        return new Parser(file, source, language).parse();
    }

    /** Consumes top-level items until EOF, preserving the order of declarations and includes. */
    private SourceFile.Builder parse() {
        var result = SourceFile.create().setPath(file).setLanguage(language);
        while (!tokens.peek().equals("<eof>")) {
            if (tokens.peek().startsWith("#")) {
                var include = java.util.regex.Pattern.compile("#\\s*include\\s*(?:<([^>]+)>|\"([^\"]+)\")\\s*").matcher(tokens.peek());
                if (language != Language.CPP || !include.matches()) throw tokens.error("Only #include is supported");
                boolean system = include.group(1) != null;
                result.addItem(IncludeDirective.create().setSystem(system).setHeader(include.group(system ? 1 : 2)));
                tokens.advance();
            } else if (tokens.peek().equals("class") || tokens.peek().equals("public")) {
                result.addItem(clazz());
            } else if (language == Language.CPP && tokens.take("int")) {
                tokens.expect("main"); tokens.expect("("); tokens.expect(")");
                result.addItem(FunctionDeclaration.create().setName("main").setReturnType(type("int")).setBody(statements.block()));
            } else throw tokens.error("Unsupported top-level declaration");
        }
        return result;
    }

    private static NamedType.Builder type(String name) {
        return NamedType.create().setName(name);
    }

    /** Parses the supported class form and dispatches each method body to the statement reader. */
    private ClassDeclaration.Builder clazz() {
        boolean isPublic = tokens.take("public");
        if (isPublic && language == Language.CPP) throw tokens.error("Unexpected public before class");
        tokens.expect("class");
        var node = ClassDeclaration.create().setName(tokens.identifier());
        if (isPublic) node.addModifier("public");
        tokens.expect("{");
        if (language == Language.CPP) { tokens.expect("public"); tokens.expect(":"); }
        while (!tokens.take("}")) {
            var method = MethodDeclaration.create();
            if (language == Language.JAVA) { tokens.expect("public"); method.addModifier("public"); }
            tokens.expect("static"); tokens.expect("void"); tokens.expect("main"); tokens.expect("(");
            method.addModifier("static").setName("main").setReturnType(type("void"));
            if (language == Language.JAVA) {
                tokens.expect("String"); tokens.expect("["); tokens.expect("]");
                method.addParameter(ParameterDeclaration.create().setType(type("String[]")).setName(tokens.identifier()));
            }
            tokens.expect(")");
            node.addMember(method.setBody(statements.block()));
        }
        if (language == Language.CPP) tokens.expect(";");
        return node;
    }

}
