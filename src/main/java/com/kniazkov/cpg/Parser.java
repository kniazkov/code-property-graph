/* Handwritten lexer and recursive-descent parser. MIT License. */
package com.kniazkov.cpg;

import java.util.*;
import com.kniazkov.cpg.model.*;

final class Parser {
    private record Token(String text, int line, int column) {}
    private final List<Token> tokens = new ArrayList<>();
    private final String file;
    private final Language language;
    private int pos;
    Parser(String file, String source, Language language) {
        this.file = file; this.language = language; lex(source);
    }
    private void lex(String s) {
        int i = 0, line = 1, column = 1;
        while (i < s.length()) {
            char c = s.charAt(i); int start = i, sl = line, sc = column;
            if (Character.isWhitespace(c)) {
                if (c == '\n') { line++; column = 1; } else column++;
                i++; continue;
            }
            if (s.startsWith("//", i)) {
                while (i < s.length() && s.charAt(i) != '\n') { i++; column++; } continue;
            }
            if (s.startsWith("/*", i)) {
                i += 2; column += 2;
                while (i < s.length() && !s.startsWith("*/", i)) {
                    if (s.charAt(i++) == '\n') { line++; column = 1; } else column++;
                }
                if (i == s.length()) throw new ParseException(file, sl, sc, "Unterminated comment");
                i += 2; column += 2; continue;
            }
            if (c == '#') {
                while (i < s.length() && s.charAt(i) != '\n') { i++; column++; }
            } else if (Character.isJavaIdentifierStart(c)) {
                i++; while (i < s.length() && Character.isJavaIdentifierPart(s.charAt(i))) i++;
                column += i - start;
            } else if (Character.isDigit(c)) {
                i++; while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
                column += i - start;
            } else if (s.startsWith("::", i) || s.startsWith("<<", i)) { i += 2; column += 2; }
            else if ("{}()[];=+-*/.,:".indexOf(c) >= 0) { i++; column++; }
            else throw new ParseException(file, line, column, "Unsupported character: " + c);
            tokens.add(new Token(s.substring(start, i), sl, sc));
        }
        tokens.add(new Token("<eof>", line, column));
    }
    private String peek() { return tokens.get(pos).text; }
    private boolean take(String value) { if (peek().equals(value)) { pos++; return true; } return false; }
    private void expect(String value) { if (!take(value)) fail("Expected '" + value + "', found '" + peek() + "'"); }
    private void fail(String reason) { Token t = tokens.get(pos); throw new ParseException(file, t.line, t.column, reason); }
    private String identifier() {
        String s = peek();
        if (!s.matches("[A-Za-z_$][A-Za-z0-9_$]*") || Set.of("class", "int", "void", "return", "if", "while", "for", "public", "static").contains(s)) fail("Expected identifier");
        pos++; return s;
    }
    SourceFile.Builder parse() {
        var result = SourceFile.create().setPath(file).setLanguage(language);
        while (!peek().equals("<eof>")) {
            if (peek().startsWith("#")) {
                var include = java.util.regex.Pattern.compile("#\\s*include\\s*(?:<([^>]+)>|\"([^\"]+)\")\\s*").matcher(peek());
                if (language != Language.CPP || !include.matches()) fail("Only #include is supported");
                boolean system = include.group(1) != null;
                result.addItem(IncludeDirective.create().setSystem(system).setHeader(include.group(system ? 1 : 2)));
                pos++;
            } else if (peek().equals("class") || peek().equals("public")) {
                result.addItem(clazz());
            } else if (language == Language.CPP && take("int")) {
                expect("main"); expect("("); expect(")");
                result.addItem(FunctionDeclaration.create().setName("main").setReturnType(type("int")).setBody(block()));
            } else fail("Unsupported top-level declaration");
        }
        return result;
    }

    private static NamedType.Builder type(String name) {
        return NamedType.create().setName(name);
    }

    private ClassDeclaration.Builder clazz() {
        boolean isPublic = take("public");
        if (isPublic && language == Language.CPP) fail("Unexpected public before class");
        expect("class");
        var node = ClassDeclaration.create().setName(identifier());
        if (isPublic) node.addModifier("public");
        expect("{");
        if (language == Language.CPP) { expect("public"); expect(":"); }
        while (!take("}")) {
            var method = MethodDeclaration.create();
            if (language == Language.JAVA) { expect("public"); method.addModifier("public"); }
            expect("static"); expect("void"); expect("main"); expect("(");
            method.addModifier("static").setName("main").setReturnType(type("void"));
            if (language == Language.JAVA) {
                expect("String"); expect("["); expect("]");
                method.addParameter(ParameterDeclaration.create().setType(type("String[]")).setName(identifier()));
            }
            expect(")");
            node.addMember(method.setBody(block()));
        }
        if (language == Language.CPP) expect(";");
        return node;
    }

    private BlockStatement.Builder block() {
        expect("{");
        var block = BlockStatement.create();
        while (!take("}")) {
            if (take("int")) {
                String name = identifier();
                expect("=");
                block.addItem(VariableDeclaration.create().setName(name).setType(type("int")).setInitializer(expression(0)));
            } else if (take("return")) {
                var statement = ReturnStatement.create();
                if (!peek().equals(";")) statement.setValue(expression(0));
                block.addItem(statement);
            } else {
                block.addItem(ExpressionStatement.create().setExpression(expression(0)));
            }
            expect(";");
        }
        return block;
    }

    private int precedence(String op) {
        return switch (op) {
            case "=" -> 0;
            case "<<" -> language == Language.CPP ? 1 : -1;
            case "+", "-" -> 2;
            case "*", "/" -> 3;
            default -> -1;
        };
    }

    private NodeBuilder<? extends Expression> expression(int minimum) {
        var left = primary();
        while (precedence(peek()) >= minimum) {
            String operator = peek();
            int priority = precedence(operator);
            if (operator.equals("=") && !(left instanceof NameExpression.Builder)
                    && !(left instanceof MemberExpression.Builder<?, ?>)) fail("Assignment requires a writable target");
            pos++;
            var right = expression(operator.equals("=") ? priority : priority + 1);
            left = switch (operator) {
                case "=" -> AssignmentExpression.create().setTarget(left).setValue(right);
                case "+" -> AddExpression.create().setLeft(left).setRight(right);
                case "-" -> SubtractExpression.create().setLeft(left).setRight(right);
                case "*" -> MultiplyExpression.create().setLeft(left).setRight(right);
                case "/" -> DivideExpression.create().setLeft(left).setRight(right);
                case "<<" -> ShiftLeftExpression.create().setLeft(left).setRight(right);
                default -> throw new AssertionError(operator);
            };
        }
        return left;
    }

    private NodeBuilder<? extends Expression> primary() {
        NodeBuilder<? extends Expression> value;
        if (take("(")) { value = expression(0); expect(")"); }
        else if (take("-")) {
            if (take("2147483648")) return IntegerLiteral.create().setValue(Integer.MIN_VALUE);
            return NegateExpression.create().setOperand(primary());
        } else if (take("+")) return UnaryPlusExpression.create().setOperand(primary());
        else if (peek().matches("[0-9]+")) {
            String number = peek();
            if (number.length() > 1 && number.startsWith("0")) fail("Only decimal literals without leading zeros are supported");
            try { value = IntegerLiteral.create().setValue(Integer.parseInt(number)); }
            catch (NumberFormatException e) { fail("Integer literal outside signed 32-bit range"); throw new AssertionError(); }
            pos++;
        } else value = NameExpression.create().setName(identifier());
        while (true) {
            if (take(".")) value = DotMemberExpression.create().setReceiver(value).setMember(identifier());
            else if (language == Language.CPP && take("::")) value = ScopeMemberExpression.create().setReceiver(value).setMember(identifier());
            else if (take("(")) {
                var call = CallExpression.create().setCallee(value);
                if (!take(")")) { do { call.addArgument(expression(0)); } while (take(",")); expect(")"); }
                value = call;
            } else return value;
        }
    }
}
