/* Code Property Graph: blocks and statement grammar. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.model.*;

/** Reads method bodies. Each rule delegates its value expressions and consumes its own semicolon. */
final class StatementParser {
    private final TokenCursor tokens;
    private final ExpressionParser expressions;
    private final ParseRecovery recovery;
    private final TypeParser types;
    private java.util.function.Supplier<java.util.List<NodeBuilder<? extends Declaration>>> declarationReader;
    void setDeclarationReader(java.util.function.Supplier<java.util.List<NodeBuilder<? extends Declaration>>> value) { declarationReader = value; }

    StatementParser(TokenCursor tokens, ExpressionParser expressions) {
        this(tokens, expressions, null);
    }

    StatementParser(TokenCursor tokens, ExpressionParser expressions, ParseRecovery recovery) {
        this(tokens, expressions, recovery, new TypeParser(tokens, expressions));
    }

    StatementParser(TokenCursor tokens, ExpressionParser expressions, ParseRecovery recovery, TypeParser types) {
        this.types = types;
        this.recovery = recovery;
        this.tokens = tokens;
        this.expressions = expressions;
    }

    /** Consumes both braces and records declarations, returns and expression statements in source order. */
    BlockStatement.Builder block() {
        var start = tokens.start();
        tokens.expect("{");
        var block = BlockStatement.create();
        types.pushScope();
        try {
        while (recovery == null || !recovery.stopped()) {
            try {
                if (tokens.take("}")) break;
                var itemStart = tokens.start();
                if (tokens.language() == Language.CPP && types.startsDeclaration()) {
                    for (var declaration : declarationReader == null ? declarations() : declarationReader.get()) block.addItem(declaration);
                } else if (tokens.take("int")) {
                    var type = tokens.span(itemStart, NamedType.create().setName("int"));
                    String name = tokens.identifier();
                    tokens.expect("=");
                    var decl = VariableDeclaration.create().setName(name).setType(type);
                    var initializerStart = tokens.start();
                    try { decl.setInitializer(expressions.parse()); }
                    catch (ParseException error) {
                        if (recovery == null) throw error;
                        var skipped = tokens.recoverInitializer(initializerStart);
                        if (skipped == null) throw error;
                        decl.setInitializer(ErrorExpression.create().setProblem(recovery.recovered(error, skipped)));
                    }
                    tokens.expect(";");
                    block.addItem(tokens.span(itemStart, decl));
                } else {
                    var statement = statement();
                    for (var declaration : types.takeImplicitDeclarations()) block.addItem(declaration);
                    block.addItem(statement);
                }
            } catch (ParseException error) {
                if (recovery == null) throw error;
                recovery.stop(error, tokens.currentRange());
            }
        }
        } finally { types.popScope(); }
        return tokens.span(start, block);
    }

    private java.util.List<NodeBuilder<? extends Declaration>> declarations() {
        var start = tokens.start(); var specs = types.specifiers();
        var result = new java.util.ArrayList<NodeBuilder<? extends Declaration>>();
        int declaratorCount = 0;
        do {
            declaratorCount++;
            var declarator = types.declarator(false);
            var type = declarator.apply().apply(specs.type().get());
            var function = types.functionType(type);
            result.addAll(types.takeImplicitDeclarations());
            if (specs.typedef()) {
                if (!specs.modifiers().isEmpty()) throw tokens.error("Storage specifier on typedef");
                var declaration = TypedefDeclaration.create().setName(declarator.name()).setType(type);
                types.declareAlias(declarator.name(), declaration); result.add(tokens.span(start, declaration));
            } else if (function != null) {
                var declaration = FunctionDeclaration.create().setName(declarator.name())
                        .setReturnType(function.returnType().orElseThrow()).setParameters(function.parameters())
                        .setVariadic(function.variadic()).setModifiers(specs.modifiers());
                if (!(type instanceof FunctionType.Builder)) declaration.setWrittenType(type);
                if (function.isConst() || function.isVolatile()) throw tokens.error("Free function cannot have cv qualifiers");
                types.declareName(declarator.name()); result.add(tokens.span(start, declaration));
            } else {
                var declaration = VariableDeclaration.create().setName(declarator.name()).setType(type).setModifiers(specs.modifiers());
                types.declareName(declarator.name());
                if (tokens.take("=")) {
                    var initializerStart = tokens.start();
                    try { declaration.setInitializer(expressions.parse()); }
                    catch (ParseException error) {
                        if (recovery == null) throw error;
                        var skipped = tokens.recoverInitializer(initializerStart);
                        if (skipped == null) throw error;
                        declaration.setInitializer(ErrorExpression.create().setProblem(recovery.recovered(error, skipped)));
                    }
                }
                result.add(tokens.span(start, declaration));
            }
        } while (tokens.take(","));
        tokens.expect(";");
        if (declaratorCount == 1) tokens.span(start, result.getLast());
        return result;
    }

    /** Recursion binds an optional else to its nearest still-open if. */
    private NodeBuilder<? extends Statement> statement() {
        var start = tokens.start();
        if (tokens.peek().equals("{")) return block();
        if (tokens.take("if")) {
            var branch = IfStatement.create().setCondition(condition()).setThenBranch(statement());
            if ((recovery == null || !recovery.stopped()) && tokens.take("else")) branch.setElseBranch(statement());
            return tokens.span(start, branch);
        }
        if (tokens.take("while")) return tokens.span(start, WhileStatement.create().setCondition(condition()).setBody(statement()));
        if (tokens.take("return")) {
            var result = ReturnStatement.create();
            if (!tokens.peek().equals(";")) result.setValue(expressions.parse());
            tokens.expect(";");
            return tokens.span(start, result);
        }
        var result = ExpressionStatement.create().setExpression(expressions.parseStatement());
        tokens.expect(";");
        return tokens.span(start, result);
    }

    private NodeBuilder<? extends Expression> condition() {
        tokens.expect("(");
        var condition = expressions.parse();
        tokens.expect(")");
        return condition;
    }
}
