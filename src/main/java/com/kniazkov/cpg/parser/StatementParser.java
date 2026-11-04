/* Code Property Graph: blocks and statement grammar. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.model.*;

/** Reads method bodies. Each rule delegates its value expressions and consumes its own semicolon. */
final class StatementParser {
    private final TokenCursor tokens;
    private final ExpressionParser expressions;

    StatementParser(TokenCursor tokens, ExpressionParser expressions) {
        this.tokens = tokens;
        this.expressions = expressions;
    }

    /** Consumes both braces and records declarations, returns and expression statements in source order. */
    BlockStatement.Builder block() {
        var start = tokens.start();
        tokens.expect("{");
        var block = BlockStatement.create();
        while (!tokens.take("}")) {
            var itemStart = tokens.start();
            if (tokens.take("int")) {
                var type = tokens.span(itemStart, NamedType.create().setName("int"));
                String name = tokens.identifier();
                tokens.expect("=");
                var decl = VariableDeclaration.create().setName(name).setType(type).setInitializer(expressions.parse());
                tokens.expect(";");
                block.addItem(tokens.span(itemStart, decl));
            } else block.addItem(statement());
        }
        return tokens.span(start, block);
    }

    /** Recursion binds an optional else to its nearest still-open if. */
    private NodeBuilder<? extends Statement> statement() {
        var start = tokens.start();
        if (tokens.peek().equals("{")) return block();
        if (tokens.take("if")) {
            var branch = IfStatement.create().setCondition(condition()).setThenBranch(statement());
            if (tokens.take("else")) branch.setElseBranch(statement());
            return tokens.span(start, branch);
        }
        if (tokens.take("while")) return tokens.span(start, WhileStatement.create().setCondition(condition()).setBody(statement()));
        if (tokens.take("return")) {
            var result = ReturnStatement.create();
            if (!tokens.peek().equals(";")) result.setValue(expressions.parse());
            tokens.expect(";");
            return tokens.span(start, result);
        }
        var result = ExpressionStatement.create().setExpression(expressions.parse());
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
