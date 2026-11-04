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
        tokens.expect("{");
        var block = BlockStatement.create();
        while (!tokens.take("}")) {
            if (tokens.take("int")) {
                String name = tokens.identifier();
                tokens.expect("=");
                block.addItem(VariableDeclaration.create().setName(name).setType(NamedType.create().setName("int")).setInitializer(expressions.parse()));
                tokens.expect(";");
            } else block.addItem(statement());
        }
        return block;
    }

    /** Recursion binds an optional else to its nearest still-open if. */
    private NodeBuilder<? extends Statement> statement() {
        if (tokens.peek().equals("{")) return block();
        if (tokens.take("if")) {
            var branch = IfStatement.create().setCondition(condition()).setThenBranch(statement());
            if (tokens.take("else")) branch.setElseBranch(statement());
            return branch;
        }
        if (tokens.take("while")) return WhileStatement.create().setCondition(condition()).setBody(statement());
        if (tokens.take("return")) {
            var result = ReturnStatement.create();
            if (!tokens.peek().equals(";")) result.setValue(expressions.parse());
            tokens.expect(";");
            return result;
        }
        var result = ExpressionStatement.create().setExpression(expressions.parse());
        tokens.expect(";");
        return result;
    }

    private NodeBuilder<? extends Expression> condition() {
        tokens.expect("(");
        var condition = expressions.parse();
        tokens.expect(")");
        return condition;
    }
}
