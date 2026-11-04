/* Code Property Graph: C++03 conditional-inclusion expressions. MIT License. */
package com.kniazkov.cpg.parser;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static com.kniazkov.cpg.parser.PreprocessingToken.Kind.*;

/**
 * Evaluates the significant tokens of a macro-expanded C++03 #if expression.
 * The caller replaces defined expressions before invoking this helper.
 *
 * The preprocessing integer model uses 64-bit long and unsigned long. Signed
 * overflow is diagnosed, unsigned arithmetic wraps, signed division truncates
 * toward zero, and negative signed right shifts extend the sign bit. Ordinary
 * characters use UTF-8 with nonnegative single-byte values; multicharacter
 * literals pack bytes into the low 64 bits. Wide characters use Unicode scalar
 * values, 32-bit numeric escapes, and the final character of a multicharacter
 * literal. These choices specify the implementation-defined cases in 16.1.
 */
final class CppConditionalExpression {
    private static final BigInteger SIGNED_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger UNSIGNED_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private final List<PreprocessingToken> tokens;
    private int index;

    private record Value(long bits, boolean unsigned) {
        boolean truth() { return bits != 0; }
    }

    private CppConditionalExpression(List<PreprocessingToken> tokens) { this.tokens = tokens; }

    static boolean evaluate(List<PreprocessingToken> tokens) {
        var parser = new CppConditionalExpression(tokens);
        Value result = parser.conditional(true);
        if (parser.index != tokens.size())
            throw parser.error(tokens.get(parser.index), "Unexpected token in #if expression");
        return result.truth();
    }

    private Value conditional(boolean evaluate) {
        Value condition = binary(1, evaluate);
        if (!take("?")) return condition;
        Value yes = conditional(evaluate && condition.truth());
        if (!take(":")) throw error(current(), "Expected ':' in #if expression");
        Value no = conditional(evaluate && !condition.truth());
        // The unselected operand still determines the usual arithmetic conversion.
        return new Value(evaluate ? (condition.truth() ? yes.bits() : no.bits()) : 0,
                yes.unsigned() || no.unsigned());
    }

    private Value binary(int minimum, boolean evaluate) {
        Value left = unary(evaluate);
        while (index < tokens.size()) {
            PreprocessingToken operator = tokens.get(index);
            String op = operator.canonicalSpelling();
            int precedence = precedence(op);
            if (precedence < minimum) break;
            index++;
            boolean evaluateRight = evaluate && (!op.equals("&&") || left.truth())
                    && (!op.equals("||") || !left.truth());
            Value right = binary(precedence + 1, evaluateRight);
            left = apply(operator, left, right, evaluate);
        }
        return left;
    }

    private static int precedence(String op) {
        return switch (op) {
            case "||" -> 1;
            case "&&" -> 2;
            case "|" -> 3;
            case "^" -> 4;
            case "&" -> 5;
            case "==", "!=" -> 6;
            case "<", ">", "<=", ">=" -> 7;
            case "<<", ">>" -> 8;
            case "+", "-" -> 9;
            case "*", "/", "%" -> 10;
            default -> 0;
        };
    }

    private Value unary(boolean evaluate) {
        PreprocessingToken token = current();
        if (token == null) throw error(null, "Expected operand in #if expression");
        String spelling = token.canonicalSpelling();
        if (spelling.equals("+") || spelling.equals("-") || spelling.equals("!") || spelling.equals("~")) {
            index++;
            Value operand = unary(evaluate);
            if (spelling.equals("!")) return bool(evaluate && !operand.truth());
            if (!evaluate) return new Value(0, operand.unsigned());
            if (spelling.equals("-") && !operand.unsigned() && operand.bits() == Long.MIN_VALUE)
                throw error(token, "Signed integer overflow in #if expression");
            return new Value(switch (spelling) {
                case "-" -> -operand.bits();
                case "~" -> ~operand.bits();
                default -> operand.bits();
            }, operand.unsigned());
        }
        if (take("(")) {
            Value value = conditional(evaluate);
            if (!take(")")) throw error(current(), "Expected ')' in #if expression");
            return value;
        }
        index++;
        if (token.kind() == PP_NUMBER) return integer(token);
        if (token.kind() == CHARACTER_LITERAL) return character(token);
        // The lexer classifies new/delete as operator tokens. They are keywords,
        // unlike alternative operator tokens, and cpp.cond also replaces them.
        if (token.kind() == IDENTIFIER || spelling.equals("new") || spelling.equals("delete"))
            return bool(spelling.equals("true"));
        throw error(token, "Expected integer operand in #if expression");
    }

    private Value apply(PreprocessingToken token, Value left, Value right, boolean evaluate) {
        String op = token.canonicalSpelling();
        boolean unsigned = left.unsigned() || right.unsigned();
        boolean comparison = precedence(op) <= 2 || precedence(op) == 6 || precedence(op) == 7;
        boolean shift = op.equals("<<") || op.equals(">>");
        if (!evaluate) return new Value(0, !comparison && (shift ? left.unsigned() : unsigned));
        long a = left.bits(), b = right.bits();
        if (shift) {
            if (b < 0 || b >= 64) throw error(token, "Invalid shift count in #if expression");
            return new Value(op.equals("<<") ? a << (int) b
                    : left.unsigned() ? a >>> (int) b : a >> (int) b, left.unsigned());
        }
        int order = unsigned ? Long.compareUnsigned(a, b) : Long.compare(a, b);
        if (comparison) return bool(switch (op) {
            case "||" -> left.truth() || right.truth();
            case "&&" -> left.truth() && right.truth();
            case "==" -> a == b;
            case "!=" -> a != b;
            case "<" -> order < 0;
            case ">" -> order > 0;
            case "<=" -> order <= 0;
            case ">=" -> order >= 0;
            default -> throw new AssertionError(op);
        });
        if ((op.equals("/") || op.equals("%")) && b == 0)
            throw error(token, "Division by zero in #if expression");
        if ((op.equals("/") || op.equals("%")) && !unsigned && a == Long.MIN_VALUE && b == -1)
            throw error(token, "Signed integer overflow in #if expression");
        try {
            return new Value(switch (op) {
                case "+" -> unsigned ? a + b : Math.addExact(a, b);
                case "-" -> unsigned ? a - b : Math.subtractExact(a, b);
                case "*" -> unsigned ? a * b : Math.multiplyExact(a, b);
                case "/" -> unsigned ? Long.divideUnsigned(a, b) : a / b;
                case "%" -> unsigned ? Long.remainderUnsigned(a, b) : a % b;
                case "&" -> a & b;
                case "^" -> a ^ b;
                case "|" -> a | b;
                default -> throw new AssertionError(op);
            }, unsigned);
        } catch (ArithmeticException exception) {
            throw error(token, "Signed integer overflow in #if expression");
        }
    }

    private Value integer(PreprocessingToken token) {
        String spelling = token.spelling();
        if (!spelling.matches("(?:0[xX][0-9a-fA-F]+|0[0-7]*|[1-9][0-9]*)(?:[uU][lL]?|[lL][uU]?)?"))
            throw error(token, "Invalid C++03 integer literal in #if expression");
        int end = spelling.length();
        while (end > 0 && "uUlL".indexOf(spelling.charAt(end - 1)) >= 0) end--;
        String suffix = spelling.substring(end);
        String digits = spelling.substring(0, end);
        int radix = digits.startsWith("0x") || digits.startsWith("0X") ? 16 : digits.startsWith("0") ? 8 : 10;
        if (radix == 16) digits = digits.substring(2);
        BigInteger value = new BigInteger(digits, radix);
        boolean unsigned = suffix.indexOf('u') >= 0 || suffix.indexOf('U') >= 0;
        if (value.compareTo(UNSIGNED_MAX) > 0 || (!unsigned && suffix.isEmpty()
                && radix == 10 && value.compareTo(SIGNED_MAX) > 0))
            throw error(token, "Integer literal out of range in #if expression");
        return new Value(value.longValue(), unsigned || value.compareTo(SIGNED_MAX) > 0);
    }

    private Value character(PreprocessingToken token) {
        String spelling = token.spelling();
        boolean wide = spelling.startsWith("L'");
        int cursor = wide ? 2 : 1;
        int end = spelling.length() - 1;
        if (cursor >= end || spelling.charAt(end) != '\'')
            throw error(token, "Invalid character literal in #if expression");
        long value = 0;
        while (cursor < end) {
            int codePoint = spelling.codePointAt(cursor);
            cursor += Character.charCount(codePoint);
            boolean numeric = false;
            if (codePoint == '\\') {
                if (cursor >= end) throw error(token, "Invalid character escape in #if expression");
                char escape = spelling.charAt(cursor++);
                String simple = "'\"?\\abfnrtv";
                String replacements = "'\"?\\\u0007\b\f\n\r\t\u000b";
                int simpleIndex = simple.indexOf(escape);
                if (simpleIndex >= 0) codePoint = replacements.charAt(simpleIndex);
                else if (escape == 'x' || escape >= '0' && escape <= '7') {
                    int start = escape == 'x' ? cursor : cursor - 1;
                    int limit = escape == 'x' ? end : Math.min(end, start + 3);
                    int radix = escape == 'x' ? 16 : 8;
                    while (cursor < limit && digit(spelling.charAt(cursor), radix)) cursor++;
                    if (cursor == start) throw error(token, "Invalid character escape in #if expression");
                    codePoint = new BigInteger(spelling.substring(start, cursor), radix).intValue();
                    numeric = true;
                } else if (escape == 'u' || escape == 'U') {
                    int length = escape == 'u' ? 4 : 8;
                    if (cursor + length > end) throw error(token, "Invalid universal character name in #if expression");
                    for (int i = cursor; i < cursor + length; i++)
                        if (!digit(spelling.charAt(i), 16)) throw error(token, "Invalid universal character name in #if expression");
                    long decoded = Long.parseLong(spelling.substring(cursor, cursor + length), 16);
                    if (decoded > Character.MAX_CODE_POINT || decoded >= 0xD800 && decoded <= 0xDFFF
                            || decoded < 0xA0 && decoded != 0x24 && decoded != 0x40 && decoded != 0x60)
                        throw error(token, "Invalid universal character name in #if expression");
                    codePoint = (int) decoded;
                    cursor += length;
                } else throw error(token, "Invalid character escape in #if expression");
            }
            if (wide) value = codePoint;
            else if (numeric) value = (value << 8) | (codePoint & 0xffL);
            else for (byte octet : new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8))
                value = (value << 8) | (octet & 0xffL);
        }
        return new Value(value, false);
    }

    private static boolean digit(char ch, int radix) {
        return ch >= '0' && ch <= (radix == 8 ? '7' : '9')
                || radix == 16 && (ch >= 'a' && ch <= 'f' || ch >= 'A' && ch <= 'F');
    }

    private static Value bool(boolean value) { return new Value(value ? 1 : 0, false); }
    private PreprocessingToken current() { return index < tokens.size() ? tokens.get(index) : null; }
    private boolean take(String spelling) {
        if (current() == null || !current().canonicalSpelling().equals(spelling)) return false;
        index++;
        return true;
    }

    private ParseException error(PreprocessingToken token, String reason) {
        if (token == null && !tokens.isEmpty()) {
            var range = tokens.getLast().range();
            return new ParseException(range.file(), range.end().line(), range.end().column(), reason);
        }
        if (token == null) return new ParseException("<preprocessor>", 1, 1, reason);
        var range = token.range();
        return new ParseException(range.file(), range.start().line(), range.start().column(), reason);
    }
}
