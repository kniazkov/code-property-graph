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
            } else if (tokens.take("return")) {
                var statement = ReturnStatement.create();
                if (!tokens.peek().equals(";")) statement.setValue(expressions.parse());
                block.addItem(statement);
            } else {
                block.addItem(ExpressionStatement.create().setExpression(expressions.parse()));
            }
            tokens.expect(";");
        }
        return block;
    }

}
