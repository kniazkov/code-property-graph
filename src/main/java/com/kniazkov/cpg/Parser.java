/* Handwritten lexer and recursive-descent parser. MIT License. */
package com.kniazkov.cpg;

import java.util.*;
import com.kniazkov.cpg.Graph.*;

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
    Draft parse() {
        Draft result = new Draft(Kind.FILE, file);
        while (!peek().equals("<eof>")) {
            if (peek().startsWith("#")) {
                if (language != Language.CPP || !peek().matches("#\\s*include\\s*(<[^>]+>|\"[^\"]+\")\\s*")) fail("Only #include is supported");
                result.add(new Draft(Kind.INCLUDE, peek())); pos++;
            } else if (peek().equals("class") || peek().equals("public")) result.add(clazz());
            else if (language == Language.CPP && peek().equals("int")) {
                expect("int"); expect("main"); expect("("); expect(")");
                result.add(new Draft(Kind.METHOD, "int main()", block()));
            } else fail("Unsupported top-level declaration");
        }
        return result;
    }
    private Draft clazz() {
        boolean isPublic = take("public");
        if (isPublic && language == Language.CPP) fail("Unexpected public before class");
        expect("class"); String name = identifier(); expect("{");
        Draft node = new Draft(Kind.CLASS, (isPublic ? "public " : "") + name);
        if (language == Language.CPP) { expect("public"); expect(":"); }
        while (!take("}")) {
            if (language == Language.JAVA) expect("public");
            expect("static"); expect("void"); expect("main"); expect("(");
            String signature;
            if (language == Language.JAVA) {
                expect("String"); expect("["); expect("]"); String parameter = identifier();
                signature = "public static void main(String[] " + parameter + ")";
            } else signature = "static void main()";
            expect(")"); node.add(new Draft(Kind.METHOD, signature, block()));
        }
        if (language == Language.CPP) expect(";");
        return node;
    }
    private Draft block() {
        expect("{"); Draft block = new Draft(Kind.BLOCK, "");
        while (!take("}")) {
            Draft statement;
            if (take("int")) {
                String name = identifier(); expect("="); statement = new Draft(Kind.DECLARE, name, expression(0));
            } else if (take("return")) statement = new Draft(Kind.RETURN, "", expression(0));
            else {
                Draft lhs = expression(0);
                if (take("=")) {
                    if (lhs.kind() != Kind.NAME) fail("Assignment requires a variable");
                    statement = new Draft(Kind.ASSIGN, "", lhs, expression(0));
                } else statement = new Draft(Kind.EXPRESSION, "", lhs);
            }
            expect(";"); block.add(statement);
        }
        return block;
    }
    private int precedence(String op) {
        return switch(op) { case "<<" -> language == Language.CPP ? 1 : -1; case "+", "-" -> 2; case "*", "/" -> 3; default -> -1; };
    }
    private Draft expression(int minimum) {
        Draft left = primary();
        while (precedence(peek()) >= minimum) {
            String operator = peek(); int p = precedence(operator); pos++;
            left = new Draft(Kind.BINARY, operator, left, expression(p + 1));
        }
        return left;
    }
    private Draft primary() {
        Draft value;
        if (take("(")) { value = expression(0); expect(")"); }
        else if (peek().equals("-") || peek().equals("+")) {
            String op = peek(); pos++;
            // The signed minimum is accepted without constructing an out-of-range positive literal.
            if (op.equals("-") && peek().equals("2147483648")) { pos++; value = new Draft(Kind.INTEGER, "-2147483648"); }
            else value = new Draft(Kind.UNARY, op, primary());
        } else if (peek().matches("[0-9]+")) {
            String number = peek();
            try { Integer.parseInt(number); } catch (NumberFormatException e) { fail("Integer literal outside signed 32-bit range"); }
            if (number.length() > 1 && number.startsWith("0")) fail("Only decimal literals without leading zeros are supported");
            pos++; value = new Draft(Kind.INTEGER, number);
        } else value = new Draft(Kind.NAME, identifier());
        while (true) {
            if (peek().equals(".") || (language == Language.CPP && peek().equals("::"))) {
                String separator = peek(); pos++;
                value = new Draft(Kind.MEMBER, separator + identifier(), value);
            } else if (take("(")) {
                Draft call = new Draft(Kind.CALL, "", value);
                if (!take(")")) { do { call.add(expression(0)); } while (take(",")); expect(")"); }
                value = call;
            } else break;
        }
        return value;
    }
}
