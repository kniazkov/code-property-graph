/* Code Property Graph: expression grammar and operator precedence. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.model.*;

/** Builds typed expression trees by precedence climbing over a shared token cursor. */
final class ExpressionParser {
    private final TokenCursor tokens;
    private final Language language;
    private final ParseRecovery recovery;
    private TypeParser types;
    void setTypes(TypeParser types) { this.types = types; }
    private boolean statementBoundary;
    private int nestedDelimiters;

    ExpressionParser(TokenCursor tokens, Language language) {
        this(tokens, language, null);
    }
    ExpressionParser(TokenCursor tokens, Language language, ParseRecovery recovery) {
        this.tokens = tokens;
        this.language = language;
        this.recovery = recovery;
    }

    /** Starts at assignment precedence, leaving the enclosing rule's delimiter on the cursor. */
    NodeBuilder<? extends Expression> parse() {
        return expression(0);
    }

    /** Only a complete expression statement owns its terminating semicolon. */
    NodeBuilder<? extends Expression> parseStatement() {
        statementBoundary = true;
        try { return expression(0); }
        finally { statementBoundary = false; }
    }

    private NodeBuilder<? extends Expression> nestedExpression() {
        nestedDelimiters++;
        try { return expression(0); }
        finally { nestedDelimiters--; }
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
            var rightStart = tokens.start();
            NodeBuilder<? extends Expression> right;
            try { right = expression(operator.equals("=") ? priority : priority + 1); }
            catch (ParseException error) {
                if (recovery == null || !operator.equals("=") || !statementBoundary || nestedDelimiters != 0) throw error;
                var skipped = tokens.recoverInitializer(rightStart);
                if (skipped == null) throw error;
                right = ErrorExpression.create().setProblem(recovery.recovered(error, skipped));
            }
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

    /** C++03 literal candidate order uses the selected target, never host widths. */
    private String integerType(java.math.BigInteger value, int radix, String suffix) {
        String ending = suffix == null ? "" : suffix.toLowerCase(java.util.Locale.ROOT);
        java.util.List<String> candidates;
        if (ending.contains("u") && ending.contains("l")) candidates = java.util.List.of("unsigned long");
        else if (ending.contains("u")) candidates = java.util.List.of("unsigned int", "unsigned long");
        else if (ending.contains("l")) candidates = java.util.List.of("long", "unsigned long");
        else candidates = radix == 10 ? java.util.List.of("int", "long", "unsigned long")
                : java.util.List.of("int", "unsigned int", "long", "unsigned long");
        for (String candidate : candidates) {
            var range = tokens.platform().integerRange(candidate);
            if (range.isPresent() && value.compareTo(range.orElseThrow().max()) <= 0) return candidate;
        }
        throw tokens.error("Integer literal outside target C++03 type ranges");
    }

    /** Reads a prefix or atom followed by any member accesses and calls bound to it. */
    private NodeBuilder<? extends Expression> primary() {
        var start = tokens.start();
        if (java.util.Set.of(";", ")", "}", ",", "<eof>").contains(tokens.peek())) throw tokens.missingExpression();
        NodeBuilder<? extends Expression> value;
        if (language == Language.CPP && tokens.take("sizeof")) {
            var size = SizeofExpression.create();
            if (tokens.take("(")) {
                if (types != null && types.startsType()) size.setType(types.abstractType());
                else size.setOperand(nestedExpression());
                tokens.expect(")");
            } else size.setOperand(primary());
            return tokens.span(start, size);
        }
        if (language == Language.CPP && tokens.take("&")) return tokens.span(start, AddressOfExpression.create().setOperand(primary()));
        if (language == Language.CPP && tokens.take("*")) return tokens.span(start, DereferenceExpression.create().setOperand(primary()));
        if (tokens.take("(")) { value = nestedExpression(); tokens.expect(")"); }
        else if (tokens.take("-")) {
            if (language == Language.JAVA && tokens.take("2147483648")) return tokens.span(start, IntegerLiteral.create().setValue(Integer.MIN_VALUE));
            if (language == Language.CPP && tokens.peek().matches("(?i)2147483648(?:u|l|ul|lu)?")) {
                String suffix = tokens.peek().substring(10);
                String type = integerType(new java.math.BigInteger("2147483648"), 10, suffix.isEmpty() ? null : suffix);
                if (type.startsWith("unsigned")) throw tokens.error("Negated unsigned literal outside supported signed 32-bit payload");
                tokens.advance();
                return tokens.span(start, IntegerLiteral.create().setValue(Integer.MIN_VALUE).setCppType(type));
            }
            return tokens.span(start, NegateExpression.create().setOperand(primary()));
        } else if (tokens.take("+")) return tokens.span(start, UnaryPlusExpression.create().setOperand(primary()));
        else if (language == Language.CPP && tokens.peek().matches("(?i)(?:0x[0-9a-f]+|[0-9]+)(?:u|l|ul|lu)?")) {
            String number = tokens.peek();
            var match = java.util.regex.Pattern.compile("(?i)(0x[0-9a-f]+|[0-9]+)(u|l|ul|lu)?").matcher(number);
            match.matches();
            String digits = match.group(1), suffix = match.group(2);
            int radix = digits.startsWith("0x") || digits.startsWith("0X") ? 16 : digits.startsWith("0") && digits.length() > 1 ? 8 : 10;
            if (radix == 16) digits = digits.substring(2);
            try {
                var numberValue = new java.math.BigInteger(digits, radix);
                String type = integerType(numberValue, radix, suffix);
                var literal = IntegerLiteral.create().setValue(numberValue.intValueExact());
                if (suffix != null || radix != 10) literal.setCppType(type);
                value = literal;
            } catch (NumberFormatException | ArithmeticException ex) { throw tokens.error("Integer literal outside supported signed 32-bit payload"); }
            tokens.advance();
        }
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
                if (!tokens.take(")")) { do { call.addArgument(nestedExpression()); } while (tokens.take(",")); tokens.expect(")"); }
                value = call;
            } else return value;
            tokens.span(start, value);
        }
    }
}
