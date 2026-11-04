/* Code Property Graph: expression grammar and operator precedence. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.model.*;

/** Builds typed expression trees by precedence climbing over a shared token cursor. */
final class ExpressionParser {
    private final TokenCursor tokens;
    private final Language language;

    ExpressionParser(TokenCursor tokens, Language language) {
        this.tokens = tokens;
        this.language = language;
    }

    /** Starts at assignment precedence, leaving the enclosing rule's delimiter on the cursor. */
    NodeBuilder<? extends Expression> parse() {
        return expression(0);
    }

    /** Binding power of an infix token. A negative value terminates the expression. */
    private int precedence(String op) {
        return switch (op) {
            case "=" -> 0;
            case "==", "!=" -> 1;
            case "<", "<=", ">", ">=" -> 2;
            case "<<" -> language == Language.CPP ? 3 : -1;
            case "+", "-" -> 4;
            case "*", "/" -> 5;
            default -> -1;
        };
    }

    /**
     * Consumes operators at or above the supplied binding power. Assignment recurses
     * at the same level for right associativity, while arithmetic recurses one level higher.
     */
    private NodeBuilder<? extends Expression> expression(int minimum) {
        var start = tokens.start();
        var left = primary();
        while (precedence(tokens.peek()) >= minimum) {
            String operator = tokens.peek();
            int priority = precedence(operator);
            if (operator.equals("=") && !(left instanceof NameExpression.Builder)
                    && !(left instanceof MemberExpression.Builder<?, ?>)) throw tokens.error("Assignment requires a writable target");
            tokens.advance();
            var right = expression(operator.equals("=") ? priority : priority + 1);
            left = switch (operator) {
                case "=" -> AssignmentExpression.create().setTarget(left).setValue(right);
                case "+" -> AddExpression.create().setLeft(left).setRight(right);
                case "-" -> SubtractExpression.create().setLeft(left).setRight(right);
                case "*" -> MultiplyExpression.create().setLeft(left).setRight(right);
                case "/" -> DivideExpression.create().setLeft(left).setRight(right);
                case "<<" -> ShiftLeftExpression.create().setLeft(left).setRight(right);
                case "==" -> EqualExpression.create().setLeft(left).setRight(right);
                case "!=" -> NotEqualExpression.create().setLeft(left).setRight(right);
                case "<" -> LessExpression.create().setLeft(left).setRight(right);
                case "<=" -> LessOrEqualExpression.create().setLeft(left).setRight(right);
                case ">" -> GreaterExpression.create().setLeft(left).setRight(right);
                case ">=" -> GreaterOrEqualExpression.create().setLeft(left).setRight(right);
                default -> throw new AssertionError(operator);
            };
            tokens.span(start, left);
        }
        return left;
    }

    /** Reads a prefix or atom followed by any member accesses and calls bound to it. */
    private NodeBuilder<? extends Expression> primary() {
        var start = tokens.start();
        NodeBuilder<? extends Expression> value;
        if (tokens.take("(")) { value = expression(0); tokens.expect(")"); }
        else if (tokens.take("-")) {
            if (tokens.take("2147483648")) return tokens.span(start, IntegerLiteral.create().setValue(Integer.MIN_VALUE));
            return tokens.span(start, NegateExpression.create().setOperand(primary()));
        } else if (tokens.take("+")) return tokens.span(start, UnaryPlusExpression.create().setOperand(primary()));
        else if (tokens.peek().matches("[0-9]+")) {
            String number = tokens.peek();
            if (number.length() > 1 && number.startsWith("0")) throw tokens.error("Only decimal literals without leading zeros are supported");
            try { value = IntegerLiteral.create().setValue(Integer.parseInt(number)); }
            catch (NumberFormatException e) { throw tokens.error("Integer literal outside signed 32-bit range"); }
            tokens.advance();
        } else value = NameExpression.create().setName(tokens.identifier());
        tokens.span(start, value);
        while (true) {
            if (tokens.take(".")) value = DotMemberExpression.create().setReceiver(value).setMember(tokens.identifier());
            else if (language == Language.CPP && tokens.take("::")) value = ScopeMemberExpression.create().setReceiver(value).setMember(tokens.identifier());
            else if (tokens.take("(")) {
                var call = CallExpression.create().setCallee(value);
                if (!tokens.take(")")) { do { call.addArgument(expression(0)); } while (tokens.take(",")); tokens.expect(")"); }
                value = call;
            } else return value;
            tokens.span(start, value);
        }
    }
}
