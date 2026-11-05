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

    /** Assignment expressions stop at commas owned by declarations and argument lists. */
    NodeBuilder<? extends Expression> parse() { return expression(1); }
    NodeBuilder<? extends Expression> parseFull() { return expression(0); }

    /** Only a complete expression statement owns its terminating semicolon. */
    NodeBuilder<? extends Expression> parseStatement() {
        statementBoundary = true;
        try { return expression(0); }
        finally { statementBoundary = false; }
    }

    private NodeBuilder<? extends Expression> nestedExpression(int minimum) {
        nestedDelimiters++;
        try { return expression(minimum); }
        finally { nestedDelimiters--; }
    }

    private int precedence(String op) {
        if (language == Language.JAVA) return switch (op) {
            case "=" -> 1;
            case "==", "!=" -> 8;
            case "<", "<=", ">", ">=" -> 9;
            case "+", "-" -> 11;
            case "*", "/" -> 12;
            default -> -1;
        };
        return switch (op) {
            case "," -> 0;
            case "=", "+=", "-=", "*=", "/=", "%=", "<<=", ">>=", "&=", "^=", "|=" -> 1;
            case "?" -> 2;
            case "||" -> 3;
            case "&&" -> 4;
            case "|" -> 5;
            case "^" -> 6;
            case "&" -> 7;
            case "==", "!=" -> 8;
            case "<", "<=", ">", ">=" -> 9;
            case "<<", ">>" -> 10;
            case "+", "-" -> 11;
            case "*", "/", "%" -> 12;
            case ".*", "->*" -> 13;
            default -> -1;
        };
    }

    private boolean writable(NodeBuilder<? extends Expression> value) {
        return value instanceof NameExpression.Builder || value instanceof MemberExpression.Builder<?, ?>
                || value instanceof DereferenceExpression.Builder || value instanceof IndexExpression.Builder
                || value instanceof DotMemberPointerExpression.Builder || value instanceof ArrowMemberPointerExpression.Builder
                || language == Language.CPP && (value instanceof AssignmentExpression.Builder
                    || value instanceof CompoundAssignmentExpression.Builder || value instanceof ConditionalExpression.Builder
                    || value instanceof CommaExpression.Builder || value instanceof PrefixIncrementExpression.Builder
                    || value instanceof PrefixDecrementExpression.Builder || value instanceof CallExpression.Builder
                    || value instanceof ConversionExpression.Builder);
    }

    private NodeBuilder<? extends Expression> expression(int minimum) {
        var start = tokens.start();
        var left = primary();
        while (precedence(tokens.peek()) >= minimum) {
            String operator = tokens.peek();
            int priority = precedence(operator);
            if (priority == 1 && !writable(left)) throw tokens.error("Assignment requires a writable target");
            tokens.advance();
            if (operator.equals("?")) {
                var thenExpression = nestedExpression(0);
                tokens.expect(":");
                // C++ permits an assignment expression in the third operand.
                left = ConditionalExpression.create().setCondition(left).setThenExpression(thenExpression).setElseExpression(expression(1));
                tokens.span(start, left);
                continue;
            }
            var rightStart = tokens.start();
            NodeBuilder<? extends Expression> right;
            try { right = expression(priority == 1 ? priority : priority + 1); }
            catch (ParseException error) {
                if (recovery == null || priority != 1 || !statementBoundary || nestedDelimiters != 0) throw error;
                var skipped = tokens.recoverInitializer(rightStart);
                if (skipped == null) throw error;
                right = ErrorExpression.create().setProblem(recovery.recovered(error, skipped));
            }
            left = switch (operator) {
                case "=" -> AssignmentExpression.create().setTarget(left).setValue(right);
                case "+=", "-=", "*=", "/=", "%=", "<<=", ">>=", "&=", "^=", "|=" -> CompoundAssignmentExpression.create().setOperator(operator).setTarget(left).setValue(right);
                case "+" -> AddExpression.create().setLeft(left).setRight(right);
                case "-" -> SubtractExpression.create().setLeft(left).setRight(right);
                case "*" -> MultiplyExpression.create().setLeft(left).setRight(right);
                case "/" -> DivideExpression.create().setLeft(left).setRight(right);
                case "%" -> RemainderExpression.create().setLeft(left).setRight(right);
                case "<<" -> ShiftLeftExpression.create().setLeft(left).setRight(right);
                case ">>" -> ShiftRightExpression.create().setLeft(left).setRight(right);
                case "==" -> EqualExpression.create().setLeft(left).setRight(right);
                case "!=" -> NotEqualExpression.create().setLeft(left).setRight(right);
                case "<" -> LessExpression.create().setLeft(left).setRight(right);
                case "<=" -> LessOrEqualExpression.create().setLeft(left).setRight(right);
                case ">" -> GreaterExpression.create().setLeft(left).setRight(right);
                case ">=" -> GreaterOrEqualExpression.create().setLeft(left).setRight(right);
                case "&" -> BitwiseAndExpression.create().setLeft(left).setRight(right);
                case "|" -> BitwiseOrExpression.create().setLeft(left).setRight(right);
                case "^" -> BitwiseXorExpression.create().setLeft(left).setRight(right);
                case "&&" -> LogicalAndExpression.create().setLeft(left).setRight(right);
                case "||" -> LogicalOrExpression.create().setLeft(left).setRight(right);
                case "," -> CommaExpression.create().setLeft(left).setRight(right);
                case ".*" -> DotMemberPointerExpression.create().setLeft(left).setRight(right);
                case "->*" -> ArrowMemberPointerExpression.create().setLeft(left).setRight(right);
                default -> throw new AssertionError(operator);
            };
            tokens.span(start, left);
        }
        return left;
    }

    /** Reads a prefix or atom followed by any member accesses and calls bound to it. */
    private NodeBuilder<? extends Expression> primary() {
        var start = tokens.start();
        if (java.util.Set.of(";", ")", "]", "}", ",", ":", "<eof>").contains(tokens.peek())) throw tokens.missingExpression();
        NodeBuilder<? extends Expression> value;
        if (language == Language.CPP && types != null && types.functionalTypeAhead()) {
            int checkpoint = tokens.checkpoint();
            try {
                var specs = types.specifiers();
                if (tokens.take("(")) {
                    var construction = InitializationExpression.create().setForm(InitializationExpression.Form.TEMPORARY).setType(specs.type().get());
                    if (!tokens.take(")")) { do { construction.addArgument(nestedExpression(1)); } while (tokens.take(",")); tokens.expect(")"); }
                    return postfix(start, tokens.span(start, construction));
                }
            } catch (ParseException ignored) { /* Ordinary expression or qualified name. */ }
            tokens.restore(checkpoint);
        }
        if (language == Language.CPP && java.util.Set.of("static_cast", "dynamic_cast", "const_cast", "reinterpret_cast").contains(tokens.peek())) {
            String cast = tokens.peek(); tokens.advance();
            tokens.expect("<");
            var target = types.abstractType();
            tokens.expect(">"); tokens.expect("(");
            value = ConversionExpression.create().setTargetType(target).setOperand(nestedExpression(0))
                    .setExplicit(true).setKind(ConversionExpression.Kind.valueOf(cast.toUpperCase(java.util.Locale.ROOT)));
            tokens.expect(")");
            return postfix(start, tokens.span(start, value));
        }
        if (language == Language.CPP && java.util.Set.of("!", "~", "++", "--").contains(tokens.peek())) {
            String operator = tokens.peek(); tokens.advance();
            var operand = primary();
            if ((operator.equals("++") || operator.equals("--")) && !writable(operand)) throw tokens.error("Increment requires a writable target");
            value = switch (operator) {
                case "!" -> LogicalNotExpression.create().setOperand(operand);
                case "~" -> BitwiseNotExpression.create().setOperand(operand);
                case "++" -> PrefixIncrementExpression.create().setOperand(operand);
                default -> PrefixDecrementExpression.create().setOperand(operand);
            };
            return tokens.span(start, value);
        }
        if (language == Language.CPP && (tokens.peek().equals("sizeof") || tokens.peek().equals("typeid"))) {
            boolean size = tokens.take("sizeof");
            if (!size) tokens.expect("typeid");
            NodeBuilder<? extends TypeNode> type = null;
            NodeBuilder<? extends Expression> operand = null;
            if (tokens.peek().equals("(")) {
                var operandStart = tokens.start(); tokens.advance();
                type = parenthesizedType();
                if (type == null) {
                    operand = nestedExpression(0); tokens.expect(")");
                    if (size) operand = postfix(operandStart, tokens.span(operandStart, operand));
                }
            } else {
                if (!size) throw tokens.error("typeid requires parentheses");
                operand = primary();
            }
            if (size) {
                var result = SizeofExpression.create();
                if (type != null) result.setType(type); else result.setOperand(operand);
                return tokens.span(start, result);
            }
            var result = TypeidExpression.create();
            if (type != null) result.setType(type); else result.setOperand(operand);
            return postfix(start, tokens.span(start, result));
        }
        if (language == Language.CPP && (tokens.peek().equals("new") || tokens.peek().equals("delete")
                || tokens.peek().equals("::") && java.util.Set.of("new", "delete").contains(tokens.peek(1)))) {
            boolean global = tokens.take("::");
            if (tokens.take("delete")) {
                boolean array = tokens.take("["); if (array) tokens.expect("]");
                return tokens.span(start, DeleteExpression.create().setGlobal(global).setArray(array).setOperand(primary()));
            }
            tokens.expect("new");
            var allocation = NewExpression.create().setGlobal(global);
            NodeBuilder<? extends TypeNode> type = null;
            if (tokens.take("(")) {
                type = parenthesizedType();
                if (type != null) allocation.setParenthesizedType(true);
                else {
                    do { allocation.addPlacementArgument(nestedExpression(1)); } while (tokens.take(","));
                    tokens.expect(")");
                }
            }
            if (type == null) {
                if (tokens.take("(")) {
                    type = types.abstractType(); tokens.expect(")"); allocation.setParenthesizedType(true);
                } else type = types.newType();
            }
            allocation.setType(type);
            if (tokens.take("(")) {
                allocation.setHasInitializer(true);
                if (!tokens.take(")")) {
                    do { allocation.addInitializerArgument(nestedExpression(1)); } while (tokens.take(","));
                    tokens.expect(")");
                }
            }
            return tokens.span(start, allocation);
        }
        if (language == Language.CPP && tokens.take("&")) return tokens.span(start, AddressOfExpression.create().setOperand(primary()));
        if (language == Language.CPP && tokens.take("*")) return tokens.span(start, DereferenceExpression.create().setOperand(primary()));
        if (tokens.take("(")) {
            var target = language == Language.CPP ? parenthesizedType() : null;
            if (target != null) return tokens.span(start, ConversionExpression.create().setTargetType(target)
                    .setOperand(primary()).setExplicit(true).setKind(ConversionExpression.Kind.C_STYLE_CAST));
            value = nestedExpression(0); tokens.expect(")");
        }
        else if (tokens.take("-")) {
            if (language == Language.JAVA && tokens.take("2147483648")) return tokens.span(start, IntegerLiteral.create().setValue(Integer.MIN_VALUE));
            return tokens.span(start, NegateExpression.create().setOperand(primary()));
        } else if (tokens.take("+")) return tokens.span(start, UnaryPlusExpression.create().setOperand(primary()));
        else if (language == Language.CPP && (value = new LiteralParser(tokens, language).parse()) != null) { }
        else if (tokens.peek().matches("[0-9]+")) {
            String number = tokens.peek();
            if (number.length() > 1 && number.startsWith("0")) throw tokens.error("Only decimal literals without leading zeros are supported");
            try { value = IntegerLiteral.create().setValue(Integer.parseInt(number)); }
            catch (NumberFormatException e) { throw tokens.error("Integer literal outside signed 32-bit range"); }
            tokens.advance();
        } else if (language == Language.CPP && tokens.take("::")) value = NameExpression.create().setName("::" + tokens.identifier());
        else value = NameExpression.create().setName(tokens.identifier());
        return postfix(start, tokens.span(start, value));
    }

    /** Called after '('; commit only a complete type-id followed by ')'. */
    private NodeBuilder<? extends TypeNode> parenthesizedType() {
        if (types == null || !types.startsType()) return null;
        int checkpoint = tokens.checkpoint();
        try {
            var type = types.abstractType();
            if (tokens.take(")")) return type;
        } catch (ParseException ignored) { /* It may instead be an expression. */ }
        tokens.restore(checkpoint);
        return null;
    }

    private NodeBuilder<? extends Expression> postfix(TokenCursor.Mark start, NodeBuilder<? extends Expression> value) {
        while (true) {
            if (tokens.take(".")) value = DotMemberExpression.create().setReceiver(value).setMember(tokens.identifier());
            else if (language == Language.CPP && tokens.take("::")) value = ScopeMemberExpression.create().setReceiver(value).setMember(tokens.identifier());
            else if (language == Language.CPP && tokens.take("->")) value = PointerMemberExpression.create().setReceiver(value).setMember(tokens.identifier());
            else if (language == Language.CPP && tokens.take("[")) {
                value = IndexExpression.create().setLeft(value).setRight(nestedExpression(0));
                tokens.expect("]");
            } else if (language == Language.CPP && (tokens.peek().equals("++") || tokens.peek().equals("--"))) {
                if (!writable(value)) throw tokens.error("Increment requires a writable target");
                boolean increment = tokens.take("++");
                if (!increment) tokens.expect("--");
                value = increment ? PostfixIncrementExpression.create().setOperand(value) : PostfixDecrementExpression.create().setOperand(value);
            } else if (tokens.take("(")) {
                var call = CallExpression.create().setCallee(value);
                if (!tokens.take(")")) { do { call.addArgument(nestedExpression(1)); } while (tokens.take(",")); tokens.expect(")"); }
                value = call;
            } else return value;
            tokens.span(start, value);
        }
    }
}
