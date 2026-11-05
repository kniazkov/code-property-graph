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
                if (tokens.language() == Language.CPP && types.startsDeclaration() && !tokens.peek(1).equals(":")) {
                    for (var declaration : declarationReader == null ? declarations() : declarationReader.get()) block.addItem(declaration);
                } else if (tokens.language() == Language.JAVA && tokens.take("int")) {
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
                block.setComplete(false);
                recovery.stop(error, tokens.currentRange());
            }
        }
        } finally { types.popScope(); }
        if (recovery != null && recovery.stopped()) block.setComplete(false);
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

    /** Selection and iteration statements introduce scopes for condition declarations. */
    private NodeBuilder<? extends Statement> statement() {
        var start = tokens.start();
        if (tokens.peek().equals("{")) return block();
        if (tokens.language() == Language.CPP && types.startsDeclaration() && !tokens.peek(1).equals(":")) {
            var result = BlockStatement.create();
            for (var declaration : declarationReader == null ? declarations() : declarationReader.get()) result.addItem(declaration);
            return tokens.span(start, result);
        }
        if (tokens.language() == Language.CPP && tokens.take(";")) return tokens.span(start, EmptyStatement.create());
        if (tokens.peek().equals("if") || tokens.peek().equals("while") || tokens.language() == Language.CPP && tokens.peek().equals("switch")) {
            String kind = tokens.peek(); tokens.advance(); types.pushScope();
            try {
                var condition = condition();
                if (kind.equals("if")) {
                    var branch = IfStatement.create().setCondition(condition).setThenBranch(scopedStatement());
                    if ((recovery == null || !recovery.stopped()) && tokens.take("else")) branch.setElseBranch(scopedStatement());
                    return tokens.span(start, branch);
                }
                if (kind.equals("while")) return tokens.span(start, WhileStatement.create().setCondition(condition).setBody(scopedStatement()));
                return tokens.span(start, SwitchStatement.create().setCondition(condition).setBody(scopedStatement()));
            } finally { types.popScope(); }
        }
        if (tokens.language() == Language.CPP) {
            if (tokens.take("do")) {
                var body = scopedStatement(); tokens.expect("while");
                tokens.expect("("); var condition = expressions.parseFull(); tokens.expect(")"); tokens.expect(";");
                return tokens.span(start, DoWhileStatement.create().setBody(body).setCondition(condition));
            }
            if (tokens.take("for")) {
                types.pushScope();
                try {
                    tokens.expect("("); var loop = ForStatement.create();
                    if (types.startsDeclaration()) {
                        for (var declaration : declarationReader == null ? declarations() : declarationReader.get()) loop.addInitializer(declaration);
                    } else if (!tokens.take(";")) {
                        var initStart = tokens.start(); var value = expressions.parseFull(); tokens.expect(";");
                        loop.addInitializer(tokens.span(initStart, ExpressionStatement.create().setExpression(value)));
                    }
                    if (!tokens.peek().equals(";")) loop.setCondition(conditionValue());
                    tokens.expect(";");
                    if (!tokens.peek().equals(")")) loop.setUpdate(expressions.parseFull());
                    tokens.expect(")"); loop.setBody(scopedStatement());
                    return tokens.span(start, loop);
                } finally { types.popScope(); }
            }
            if (tokens.take("case")) {
                var result = CaseStatement.create().setValue(expressions.parse()); tokens.expect(":");
                return tokens.span(start, result.setStatement(statement()));
            }
            if (tokens.take("default")) { tokens.expect(":"); return tokens.span(start, CaseStatement.create().setStatement(statement())); }
            if (tokens.take("break")) { tokens.expect(";"); return tokens.span(start, BreakStatement.create()); }
            if (tokens.take("continue")) { tokens.expect(";"); return tokens.span(start, ContinueStatement.create()); }
            if (tokens.take("goto")) { var result = GotoStatement.create().setLabel(tokens.identifier()); tokens.expect(";"); return tokens.span(start, result); }
            if (tokens.take("try")) {
                var result = TryStatement.create().setBody(block());
                if (recovery == null || !recovery.stopped()) {
                    try { result.setHandlers(handlers()); }
                    catch (ParseException error) {
                        if (recovery == null) throw error;
                        recovery.stop(error, tokens.currentRange());
                    }
                }
                return tokens.span(start, result);
            }
            if (tokens.peek(1).equals(":")) {
                var result = LabelStatement.create().setLabel(tokens.identifier()); tokens.expect(":");
                return tokens.span(start, result.setStatement(statement()));
            }
        }
        if (tokens.take("return")) {
            var result = ReturnStatement.create();
            if (!tokens.peek().equals(";")) result.setValue(expressions.parseFull());
            tokens.expect(";");
            return tokens.span(start, result);
        }
        var result = ExpressionStatement.create().setExpression(expressions.parseStatement());
        tokens.expect(";");
        return tokens.span(start, result);
    }

    private NodeBuilder<? extends Statement> scopedStatement() {
        types.pushScope();
        try { return statement(); } finally { types.popScope(); }
    }

    java.util.List<NodeBuilder<? extends CatchClause>> handlers() {
        var result = new java.util.ArrayList<NodeBuilder<? extends CatchClause>>();
        boolean catchAll = false;
        do {
            var start = tokens.start(); tokens.expect("catch");
            if (catchAll) throw tokens.error("catch(...) must be the last handler");
            tokens.expect("("); types.pushScope();
            try {
                var handler = CatchClause.create();
                if (tokens.take("...")) catchAll = true;
                else {
                    var parameterStart = tokens.start(); var specs = types.specifiers();
                    if (specs.typedef() || !specs.modifiers().isEmpty()) throw tokens.error("Invalid exception declaration specifier");
                    var declarator = types.declarator(true);
                    handler.setParameter(tokens.span(parameterStart, ParameterDeclaration.create().setName(declarator.name()).setType(declarator.apply().apply(specs.type().get()))));
                    types.declareName(declarator.name());
                }
                tokens.expect(")"); handler.setBody(block());
                result.add(tokens.span(start, handler));
            } finally { types.popScope(); }
        } while ((recovery == null || !recovery.stopped()) && tokens.peek().equals("catch"));
        return result;
    }

    private NodeBuilder<? extends Expression> condition() {
        tokens.expect("("); var result = conditionValue(); tokens.expect(")"); return result;
    }

    private NodeBuilder<? extends Expression> conditionValue() {
        if (tokens.language() != Language.CPP || !types.startsDeclaration()) return expressions.parseFull();
        var start = tokens.start(); int checkpoint = tokens.checkpoint(); var specs = types.specifiers();
        if (specs.typedef() || !specs.modifiers().isEmpty()) throw tokens.error("Invalid condition declaration specifier");
        var declarator = types.declarator(false);
        var type = declarator.apply().apply(specs.type().get());
        if (types.functionType(type) != null || type instanceof ArrayType.Builder) throw tokens.error("Invalid condition declaration type");
        if (!tokens.take("=")) {
            tokens.restore(checkpoint);
            return expressions.parseFull();
        }
        types.declareName(declarator.name());
        var declaration = tokens.span(start, VariableDeclaration.create().setName(declarator.name()).setType(type).setInitializer(expressions.parse()));
        return tokens.span(start, ConditionDeclarationExpression.create().setDeclaration(declaration));
    }
}
